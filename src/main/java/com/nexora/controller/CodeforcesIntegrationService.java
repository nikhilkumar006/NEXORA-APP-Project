package com.nexora.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexora.evidence.EvidenceRecord;
import com.nexora.evidence.EvidenceRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;

/** Imports accepted Codeforces submissions using the platform's documented public API. */
@Service
public class CodeforcesIntegrationService {
    private final JdbcTemplate jdbc;
    private final EvidenceRepository evidence;
    private final ObjectMapper mapper;
    private final RestTemplate rest = new RestTemplate();
    private long lastApiCallAt = 0L;

    public CodeforcesIntegrationService(JdbcTemplate jdbc, EvidenceRepository evidence, ObjectMapper mapper) {
        this.jdbc = jdbc; this.evidence = evidence; this.mapper = mapper;
    }

    public String handle(String registrationNo) {
        try {
            return jdbc.queryForObject("SELECT external_handle FROM student_platform_connections WHERE registration_no=? AND platform='CODEFORCES'", String.class, registrationNo);
        } catch (Exception ignored) { return ""; }
    }

    public int connectAndSync(String registrationNo, String rawHandle) {
        String handle = rawHandle == null ? "" : rawHandle.trim();
        if (!handle.matches("[A-Za-z0-9_.-]{1,24}")) throw new IllegalArgumentException("Enter a valid Codeforces handle.");
        String encoded = URLEncoder.encode(handle, StandardCharsets.UTF_8);
        try {
            JsonNode info = get("https://codeforces.com/api/user.info?handles=" + encoded);
            if (!"OK".equals(info.path("status").asText())) throw new IllegalArgumentException("Codeforces did not confirm that handle. Check the spelling and try again.");
            jdbc.update("INSERT INTO student_platform_connections(registration_no,platform,external_handle) VALUES(?,'CODEFORCES',?) ON DUPLICATE KEY UPDATE external_handle=VALUES(external_handle),updated_at=CURRENT_TIMESTAMP", registrationNo, handle);
            Set<String> solved = new HashSet<>(); int imported = 0;
            for (int page = 0; page < 10; page++) {
                int from = page * 1000 + 1;
                JsonNode submissions = get("https://codeforces.com/api/user.status?handle=" + encoded + "&from=" + from + "&count=1000");
                if (!"OK".equals(submissions.path("status").asText())) break;
                JsonNode result = submissions.path("result");
                if (!result.isArray() || result.isEmpty()) break;
                for (JsonNode submission : result) {
                    if (!"OK".equals(submission.path("verdict").asText())) continue;
                    JsonNode problem = submission.path("problem");
                    int contestId = problem.path("contestId").asInt(0);
                    String index = problem.path("index").asText("");
                    String name = problem.path("name").asText("Codeforces problem");
                    if (contestId <= 0 || index.isBlank()) continue;
                    String key = contestId + ":" + index;
                    if (!solved.add(key)) continue;
                    String url = "https://codeforces.com/problemset/problem/" + contestId + "/" + index;
                    java.sql.Date solvedOn = null;
                    long epoch = submission.path("creationTimeSeconds").asLong(0);
                    if (epoch > 0) solvedOn = java.sql.Date.valueOf(Instant.ofEpochSecond(epoch).atZone(ZoneId.of("Asia/Kolkata")).toLocalDate());
                    String difficulty = problem.hasNonNull("rating") ? Integer.toString(problem.path("rating").asInt()) : null;
                    imported += jdbc.update("INSERT IGNORE INTO student_problem_logs(registration_no,platform,problem_title,problem_url,difficulty,solved_on,verification_status) VALUES(?,'Codeforces',?,?,?,?,'API_VERIFIED')", registrationNo, name, url, difficulty, solvedOn);
                }
                if (result.size() < 1000) break;
            }
            LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
            jdbc.update("DELETE FROM student_evidence WHERE registration_no=? AND category='CODING' AND metric IN ('codeforces_handle','codeforces_solved_problems')", registrationNo);
            evidence.save(new EvidenceRecord(0, registrationNo, "CODING", "codeforces_handle", handle, "Codeforces API", "API_VERIFIED", "https://codeforces.com/profile/" + handle, null, now, "Connected Codeforces handle; accepted submissions are imported from the documented public API."));
            Integer count = jdbc.queryForObject("SELECT COUNT(DISTINCT problem_url) FROM student_problem_logs WHERE registration_no=? AND platform='Codeforces' AND verification_status='API_VERIFIED'", Integer.class, registrationNo);
            evidence.save(new EvidenceRecord(0, registrationNo, "CODING", "codeforces_solved_problems", Integer.toString(count == null ? 0 : count), "Codeforces API", "API_VERIFIED", "https://codeforces.com/profile/" + handle, null, now, "Unique accepted problem URLs imported from public Codeforces submissions. Only the API records available during sync are counted."));
            return imported;
        } catch (org.springframework.web.client.RestClientException ex) {
            throw new IllegalStateException("Codeforces API is temporarily unavailable. Try again later.");
        }
    }

    @Scheduled(initialDelayString = "${nexora.integrations.codeforces-sync-initial-delay-ms:120000}", fixedDelayString = "${nexora.integrations.codeforces-sync-ms:3600000}")
    public void scheduledRefresh() {
        try {
            var connections = jdbc.query("SELECT registration_no,external_handle FROM student_platform_connections WHERE platform='CODEFORCES'",
                    (rs, row) -> new String[]{rs.getString("registration_no"), rs.getString("external_handle")});
            for (String[] connection : connections) {
                try { syncRecentAccepted(connection[0], connection[1]); } catch (Exception ignored) { }
            }
        } catch (Exception ignored) { /* Schema may not be initialized yet; retry on the next hour. */ }
    }

    private void syncRecentAccepted(String registrationNo, String handle) {
        if (handle == null || handle.isBlank()) return;
        String encoded = URLEncoder.encode(handle, StandardCharsets.UTF_8);
        JsonNode submissions = get("https://codeforces.com/api/user.status?handle=" + encoded + "&from=1&count=1000");
        if (!"OK".equals(submissions.path("status").asText())) return;
        for (JsonNode submission : submissions.path("result")) {
            if (!"OK".equals(submission.path("verdict").asText())) continue;
            JsonNode problem = submission.path("problem"); int contestId = problem.path("contestId").asInt(0); String index = problem.path("index").asText("");
            if (contestId <= 0 || index.isBlank()) continue;
            String url = "https://codeforces.com/problemset/problem/" + contestId + "/" + index;
            long epoch = submission.path("creationTimeSeconds").asLong(0);
            java.sql.Date solvedOn = epoch > 0 ? java.sql.Date.valueOf(Instant.ofEpochSecond(epoch).atZone(ZoneId.of("Asia/Kolkata")).toLocalDate()) : null;
            String rating = problem.hasNonNull("rating") ? Integer.toString(problem.path("rating").asInt()) : null;
            jdbc.update("INSERT IGNORE INTO student_problem_logs(registration_no,platform,problem_title,problem_url,difficulty,solved_on,verification_status) VALUES(?,'Codeforces',?,?,?,?,'API_VERIFIED')",
                    registrationNo, problem.path("name").asText("Codeforces problem"), url, rating, solvedOn);
        }
        jdbc.update("DELETE FROM student_evidence WHERE registration_no=? AND category='CODING' AND metric='codeforces_solved_problems'", registrationNo);
        Integer count = jdbc.queryForObject("SELECT COUNT(DISTINCT problem_url) FROM student_problem_logs WHERE registration_no=? AND platform='Codeforces' AND verification_status='API_VERIFIED'", Integer.class, registrationNo);
        evidence.save(new EvidenceRecord(0, registrationNo, "CODING", "codeforces_solved_problems", Integer.toString(count == null ? 0 : count), "Codeforces API", "API_VERIFIED", "https://codeforces.com/profile/" + handle, null, LocalDateTime.now(ZoneId.of("Asia/Kolkata")), "Unique accepted problem URLs from imported Codeforces submissions. New submissions are refreshed hourly from the public API."));
    }

    private synchronized JsonNode get(String url) {
        long wait = 2100L - (System.currentTimeMillis() - lastApiCallAt);
        if (wait > 0) {
            try { Thread.sleep(wait); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException("Codeforces sync was interrupted."); }
        }
        ResponseEntity<String> response = rest.getForEntity(url, String.class);
        lastApiCallAt = System.currentTimeMillis();
        try { return mapper.readTree(response.getBody()); }
        catch (Exception ex) { throw new IllegalStateException("Codeforces returned an unreadable API response."); }
    }
}
