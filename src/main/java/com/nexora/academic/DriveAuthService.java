package com.nexora.academic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/**
 * Optional service-account authentication for institutional Google Drive access.
 * The application can still read a public Drive folder with NEXORA_DRIVE_API_KEY.
 */
@Service
public class DriveAuthService {
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String DRIVE_SCOPE = "https://www.googleapis.com/auth/drive";

    private final ObjectMapper mapper = new ObjectMapper();
    private final RestTemplate restTemplate = createRestTemplate();

    private static RestTemplate createRestTemplate() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(5));
        factory.setReadTimeout(java.time.Duration.ofSeconds(8));
        return new RestTemplate(factory);
    }

    /** Fast, network-free check for whether service-account credentials are available. */
    public boolean hasCredentials() {
        String json = System.getenv("NEXORA_DRIVE_SERVICE_ACCOUNT_JSON");
        if (json != null && !json.isBlank()) return true;
        String file = System.getenv("NEXORA_DRIVE_SERVICE_ACCOUNT_FILE");
        if (file == null || file.isBlank()) return false;
        try { return Files.isRegularFile(Path.of(file)); }
        catch (RuntimeException ignored) { return false; }
    }
    private volatile String cachedToken;
    private volatile long cachedExpiryEpoch;
    private volatile long cachedFailureUntilEpoch;

    public String accessTokenOrNull() {
        String json = System.getenv("NEXORA_DRIVE_SERVICE_ACCOUNT_JSON");
        String file = System.getenv("NEXORA_DRIVE_SERVICE_ACCOUNT_FILE");
        if ((json == null || json.isBlank()) && file != null && !file.isBlank()) {
            try { json = Files.readString(Path.of(file)); } catch (Exception ignored) { }
        }
        if (json == null || json.isBlank()) return null;

        long now = Instant.now().getEpochSecond();
        if (cachedToken != null && now < cachedExpiryEpoch - 60) return cachedToken;
        // Avoid repeating a slow token exchange on every request when credentials are
        // invalid or Google's token endpoint is temporarily unreachable.
        if (now < cachedFailureUntilEpoch) return null;

        try {
            JsonNode credentials = mapper.readTree(json);
            String clientEmail = credentials.path("client_email").asText();
            String privateKeyPem = credentials.path("private_key").asText();
            if (clientEmail.isBlank() || privateKeyPem.isBlank()) {
                cachedFailureUntilEpoch = now + 30;
                return null;
            }

            String header = base64Url("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
            long iat = now;
            long exp = now + 3600;
            String payload = mapper.writeValueAsString(Map.of(
                    "iss", clientEmail,
                    "scope", DRIVE_SCOPE,
                    "aud", TOKEN_URL,
                    "iat", iat,
                    "exp", exp
            ));
            String encodedPayload = base64Url(payload);
            String unsigned = header + "." + encodedPayload;
            String assertion = unsigned + "." + sign(unsigned, privateKeyPem);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
            form.add("assertion", assertion);
            ResponseEntity<String> response = restTemplate.postForEntity(TOKEN_URL, new HttpEntity<>(form, headers), String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                cachedFailureUntilEpoch = now + 30;
                return null;
            }
            JsonNode token = mapper.readTree(response.getBody());
            String access = token.path("access_token").asText();
            long expiresIn = token.path("expires_in").asLong(3600);
            if (access.isBlank()) {
                cachedFailureUntilEpoch = now + 30;
                return null;
            }
            cachedFailureUntilEpoch = 0L;
            cachedToken = access;
            cachedExpiryEpoch = now + expiresIn;
            return access;
        } catch (Exception e) {
            cachedFailureUntilEpoch = now + 30;
            return null;
        }
    }

    private String sign(String data, String pem) throws Exception {
        String body = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(body);
        PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(key);
        signature.update(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
    }

    private String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
