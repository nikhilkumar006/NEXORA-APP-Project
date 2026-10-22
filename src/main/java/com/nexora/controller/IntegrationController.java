package com.nexora.controller;

import org.springframework.core.env.Environment;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.nexora.repository.StudentRepository;
import java.util.List;

@Controller
public class IntegrationController {
    private final Environment env;
    private final StudentRepository students;
    private final DevelopmentGpsPageCache developmentPageCache;
    private final JdbcTemplate jdbc;
    private final CodeforcesIntegrationService codeforces;
    private final GitHubIntegrationService githubSync;
    private final ObjectProvider<OAuth2AuthorizedClientService> oauthClients;

    public IntegrationController(Environment env, StudentRepository students, JdbcTemplate jdbc, CodeforcesIntegrationService codeforces, GitHubIntegrationService githubSync, ObjectProvider<OAuth2AuthorizedClientService> oauthClients, DevelopmentGpsPageCache developmentPageCache) {
        this.env = env;
        this.students = students;
        this.jdbc = jdbc;
        this.developmentPageCache = developmentPageCache;
        this.codeforces = codeforces;
        this.githubSync = githubSync;
        this.oauthClients = oauthClients;
    }

    @GetMapping("/integrations")
    public String page(Authentication auth, Model model, @RequestParam(required=false) String error, @RequestParam(required=false) Integer neopatImported) {
        var student = auth == null || auth.getName() == null ? null : students.findByLoginId(auth.getName()).orElse(null);
        if (student == null) return "redirect:/login?error=account";
        boolean linkedinEnabled = enabled("nexora.integrations.linkedin.enabled");
        boolean githubEnabled = enabled("nexora.integrations.github.enabled");
        model.addAttribute("student", student);
        model.addAttribute("studentInitial", student.getFullName() == null || student.getFullName().isBlank()
                ? "N" : student.getFullName().trim().substring(0, 1).toUpperCase(java.util.Locale.ROOT));
        model.addAttribute("linkedinEnabled", linkedinEnabled);
        boolean linkedinConnected = false;
        try {
            Integer connected = jdbc.queryForObject("SELECT COUNT(*) FROM student_evidence WHERE registration_no=? AND category='PROFESSIONAL' AND metric='linkedin_profile' AND verification_status='OAUTH_VERIFIED'", Integer.class, student.getRegistrationNo());
            linkedinConnected = connected != null && connected > 0;
        } catch (Exception ignored) { /* schema not ready; keep connection status unavailable */ }
        model.addAttribute("linkedinConnected", linkedinConnected);
        model.addAttribute("githubEnabled", githubEnabled);
        // Never perform a remote GitHub API sync while rendering a normal GET page. A stale
        // connection can require several API calls and must not block Connections from opening.
        // Sync remains available through the explicit POST button and scheduled background job.
        OAuth2AuthorizedClientService authorizedClients = oauthClients.getIfAvailable();
        boolean githubConnected = githubSync.connected(student.getRegistrationNo(), authorizedClients);
        model.addAttribute("githubConnected", githubConnected);
        model.addAttribute("githubHandle", githubSync.connectedHandle(student.getRegistrationNo()));
        model.addAttribute("codeforcesHandle", codeforces.handle(student.getRegistrationNo()));
        model.addAttribute("error", error);
        model.addAttribute("neopatImported", neopatImported);
        model.addAttribute("neopatProgressCount", neopatProgressCount(student.getRegistrationNo()));
        model.addAttribute("platforms", List.of(
                new PlatformCard("LeetCode", "Practice problems and coding interview preparation", "LC", "https://leetcode.com/"),
                new PlatformCard("HackerRank", "Programming challenges and skill practice", "HR", "https://www.hackerrank.com/"),
                new PlatformCard("CodeChef", "Competitive programming problems and contests", "CC", "https://www.codechef.com/"),
                new PlatformCard("Kaggle", "Datasets, notebooks and machine-learning competitions", "K", "https://www.kaggle.com/")
        ));
        return "integrations";
    }

    @GetMapping("/integrations/linkedin")
    public String linkedin(Authentication auth, jakarta.servlet.http.HttpSession session) {
        if (!enabled("nexora.integrations.linkedin.enabled")) return "redirect:/integrations?error=linkedin";
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null
                || "anonymousUser".equals(auth.getName())) return "redirect:/login";
        session.setAttribute("integrationStudent", auth.getName());
        return "redirect:/oauth2/authorization/linkedin";
    }

    @GetMapping("/integrations/github")
    public String github(Authentication auth, jakarta.servlet.http.HttpSession session) {
        if (!enabled("nexora.integrations.github.enabled")) return "redirect:/integrations?error=github";
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null || "anonymousUser".equals(auth.getName())) return "redirect:/login";
        session.setAttribute("integrationStudent", auth.getName());
        return "redirect:/oauth2/authorization/github";
    }

    @org.springframework.web.bind.annotation.PostMapping("/integrations/github/sync")
    public String syncGithub(Authentication auth) {
        var student = auth == null || auth.getName() == null ? null : students.findByLoginId(auth.getName()).orElse(null);
        if (student == null) return "redirect:/login?error=account";
        githubSync.syncIfStale(student.getRegistrationNo(), oauthClients.getIfAvailable(), true);
        developmentPageCache.evict(student.getRegistrationNo());
        return "redirect:/integrations?connected=github";
    }

    @org.springframework.web.bind.annotation.PostMapping("/integrations/codeforces")
    public String codeforcesConnect(Authentication auth, @RequestParam String handle) {
        var student = auth == null || auth.getName() == null ? null : students.findByLoginId(auth.getName()).orElse(null);
        if (student == null) return "redirect:/login?error=account";
        try {
            codeforces.connectAndSync(student.getRegistrationNo(), handle);
            developmentPageCache.evict(student.getRegistrationNo());
            return "redirect:/integrations?connected=codeforces";
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? "Codeforces sync failed." : ex.getMessage();
            return "redirect:/integrations?error=" + java.net.URLEncoder.encode(message, java.nio.charset.StandardCharsets.UTF_8);
        }
    }


    /**
     * Neopat does not expose a public progress API to this application. Students sign in directly
     * on the provider site and may import a provider-exported CSV; NEXORA never collects Neopat passwords.
     */
    @org.springframework.web.bind.annotation.PostMapping(value="/integrations/neopat/import", consumes="multipart/form-data")
    public String importNeopatProgress(Authentication auth,
            @org.springframework.web.bind.annotation.RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        var student = auth == null || auth.getName() == null ? null : students.findByLoginId(auth.getName()).orElse(null);
        if (student == null) return "redirect:/login?error=account";
        if (file == null || file.isEmpty() || file.getSize() > 2 * 1024 * 1024) {
            return "redirect:/integrations?error=neopat-file";
        }
        try {
            String csv = new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
            String[] lines = csv.split("\\R");
            if (lines.length < 2 || lines.length > 2001) return "redirect:/integrations?error=neopat-format";
            String headerLine = lines[0].startsWith("\uFEFF") ? lines[0].substring(1) : lines[0];
            String[] headers = parseCsvLine(headerLine);
            int moduleCol = -1, statusCol = -1;
            for (int i=0; i<headers.length; i++) {
                String h = headers[i].trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
                if (java.util.Set.of("module", "modulename", "lesson", "activity", "name", "title").contains(h)) moduleCol = i;
                if (java.util.Set.of("status", "progress", "completion", "completed", "state").contains(h)) statusCol = i;
            }
            if (moduleCol < 0 || statusCol < 0) return "redirect:/integrations?error=neopat-format";
            // The table is provisioned at application startup by DatabaseSchemaInitializer;
            // do not require DDL privileges from the user performing a CSV import.
            int imported = 0;
            for (int row=1; row<lines.length; row++) {
                if (lines[row].isBlank()) continue;
                String[] cells = parseCsvLine(lines[row]);
                if (moduleCol >= cells.length || statusCol >= cells.length) continue;
                String module = cells[moduleCol].trim();
                String status = cells[statusCol].trim();
                if (module.isBlank() || status.isBlank()) continue;
                if (module.length() > 255) module = module.substring(0,255);
                if (status.length() > 80) status = status.substring(0,80);
                jdbc.update("INSERT INTO nexora_neopat_progress (registration_no,module_name,progress_status) VALUES (?,?,?) ON DUPLICATE KEY UPDATE progress_status=VALUES(progress_status), imported_at=CURRENT_TIMESTAMP",
                        student.getRegistrationNo(), module, status);
                imported++;
            }
            return "redirect:/integrations?neopatImported=" + imported;
        } catch (Exception ex) {
            return "redirect:/integrations?error=neopat-import";
        }
    }

    private int neopatProgressCount(String registrationNo) {
        try {
            Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM nexora_neopat_progress WHERE registration_no=?", Integer.class, registrationNo);
            return n == null ? 0 : n;
        } catch (Exception ignored) { return 0; }
    }

    private static String[] parseCsvLine(String line) {
        java.util.List<String> out = new java.util.ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i=0; i<line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i+1 < line.length() && line.charAt(i+1) == '"') { cell.append('"'); i++; }
                else quoted = !quoted;
            } else if (c == ',' && !quoted) {
                out.add(cell.toString()); cell.setLength(0);
            } else cell.append(c);
        }
        out.add(cell.toString());
        return out.toArray(String[]::new);
    }

    private boolean enabled(String key) { return env.getProperty(key, Boolean.class, false); }

    public record PlatformCard(String name, String description, String initials, String url) {}
}
