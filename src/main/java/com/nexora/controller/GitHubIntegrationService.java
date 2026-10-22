package com.nexora.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexora.evidence.EvidenceRecord;
import com.nexora.evidence.EvidenceRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/** Least-privilege, source-linked GitHub public-repository sync. */
@Service
public class GitHubIntegrationService {
    private final JdbcTemplate jdbc;
    private final EvidenceRepository evidence;
    private final ObjectMapper mapper;
    private final ObjectProvider<OAuth2AuthorizedClientService> oauthClients;
    private final RestTemplate rest = new RestTemplate();

    public GitHubIntegrationService(JdbcTemplate jdbc, EvidenceRepository evidence, ObjectMapper mapper,
                                    ObjectProvider<OAuth2AuthorizedClientService> oauthClients) {
        this.jdbc = jdbc; this.evidence = evidence; this.mapper = mapper; this.oauthClients = oauthClients;
    }

    @Scheduled(initialDelayString = "${nexora.integrations.github-sync-initial-delay-ms:120000}", fixedDelayString = "${nexora.integrations.github-sync-ms:900000}")
    public void scheduledSync() {
        OAuth2AuthorizedClientService clients = oauthClients.getIfAvailable();
        if (clients == null) return;
        try {
            List<String> registrations = jdbc.queryForList("SELECT registration_no FROM student_oauth_connections WHERE provider='GITHUB'", String.class);
            for (String reg : registrations) syncIfStale(reg, clients, false);
        } catch (Exception ignored) { /* The scheduled sync retries after the next interval. */ }
    }

    public void connectAndSync(String registrationNo, String providerPrincipal, String githubLogin, String accessToken) {
        jdbc.update("INSERT INTO student_oauth_connections(registration_no,provider,provider_principal,external_handle) VALUES(?,'GITHUB',?,?) ON DUPLICATE KEY UPDATE provider_principal=VALUES(provider_principal),external_handle=VALUES(external_handle),updated_at=CURRENT_TIMESTAMP",
                registrationNo, providerPrincipal, githubLogin == null || githubLogin.isBlank() ? providerPrincipal : githubLogin);
        sync(registrationNo, accessToken, githubLogin);
    }

    public void syncIfStale(String registrationNo, OAuth2AuthorizedClientService clients, boolean force) {
        if (clients == null) return;
        try {
            var connection = jdbc.queryForMap("SELECT provider_principal,external_handle,last_synced_at FROM student_oauth_connections WHERE registration_no=? AND provider='GITHUB'", registrationNo);
            Object rawLast = connection.get("last_synced_at");
            LocalDateTime last = rawLast instanceof Timestamp ts ? ts.toLocalDateTime() : (rawLast instanceof LocalDateTime dt ? dt : null);
            if (!force && last != null && last.isAfter(LocalDateTime.now().minusMinutes(15))) return;
            String principal = String.valueOf(connection.get("provider_principal"));
            OAuth2AuthorizedClient client = clients.loadAuthorizedClient("github", principal);
            if (client == null || client.getAccessToken() == null) return;
            sync(registrationNo, client.getAccessToken().getTokenValue(), String.valueOf(connection.get("external_handle")));
        } catch (Exception ignored) {
            // Missing OAuth credentials/connection should never break the Connections page.
        }
    }

    public String connectedHandle(String registrationNo) {
        try { return jdbc.queryForObject("SELECT external_handle FROM student_oauth_connections WHERE registration_no=? AND provider='GITHUB'", String.class, registrationNo); }
        catch (Exception ignored) { return ""; }
    }

    public boolean connected(String registrationNo, OAuth2AuthorizedClientService clients) {
        if (clients == null) return false;
        try {
            String principal = jdbc.queryForObject("SELECT provider_principal FROM student_oauth_connections WHERE registration_no=? AND provider='GITHUB'", String.class, registrationNo);
            OAuth2AuthorizedClient client = clients.loadAuthorizedClient("github", principal);
            return client != null && client.getAccessToken() != null;
        } catch (Exception ignored) { return false; }
    }

    private void sync(String registrationNo, String accessToken, String githubLogin) {
        if (accessToken == null || accessToken.isBlank()) return;
        try {
            HttpHeaders headers = new HttpHeaders(); headers.setBearerAuth(accessToken);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON)); headers.set("X-GitHub-Api-Version", "2026-03-10");
            LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Kolkata")); int total = 0;
            for (int page = 1; page <= 10; page++) {
                String url = "https://api.github.com/user/repos?per_page=100&page=" + page + "&affiliation=owner&visibility=public&sort=updated";
                ResponseEntity<String> response = rest.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
                JsonNode repos = mapper.readTree(response.getBody());
                if (!repos.isArray() || repos.isEmpty()) break;
                for (JsonNode repo : repos) {
                    if (repo.path("fork").asBoolean(false)) continue;
                    String name = repo.path("full_name").asText(""); String repoUrl = repo.path("html_url").asText("");
                    if (name.isBlank() || repoUrl.isBlank()) continue; total++;
                    Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM student_evidence WHERE registration_no=? AND category='PROJECT' AND metric='github_repository' AND reference_url=?", Integer.class, registrationNo, repoUrl);
                    String description = repo.path("description").asText("");
                    String notes = "Imported public repository metadata. Description=" + (description.isBlank() ? "(none)" : description) + ". Repository existence is not a project-quality or completion assessment.";
                    if (exists == null || exists == 0) {
                        evidence.save(new EvidenceRecord(0, registrationNo, "PROJECT", "github_repository", name,
                                "GitHub REST API", "API_VERIFIED", repoUrl, null, now, notes));
                    } else {
                        jdbc.update("UPDATE student_evidence SET value_text=?,notes=?,captured_at=CURRENT_TIMESTAMP WHERE registration_no=? AND category='PROJECT' AND metric='github_repository' AND reference_url=?",
                                name, notes, registrationNo, repoUrl);
                    }
                }
                if (repos.size() < 100) break;
            }
            jdbc.update("DELETE FROM student_evidence WHERE registration_no=? AND category='CODING' AND metric='github_public_repositories'", registrationNo);
            evidence.save(new EvidenceRecord(0, registrationNo, "CODING", "github_public_repositories", Integer.toString(total),
                    "GitHub REST API", "API_VERIFIED", "https://github.com/" + (githubLogin == null ? "" : githubLogin), null, now,
                    "Count of non-fork public repositories returned by the authenticated GitHub API. Not a commit or solved-problem count."));
            jdbc.update("UPDATE student_oauth_connections SET last_synced_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE registration_no=? AND provider='GITHUB'", registrationNo);
        } catch (Exception ignored) {
            // API failure is surfaced as a stale timestamp; a later page visit retries.
        }
    }
}
