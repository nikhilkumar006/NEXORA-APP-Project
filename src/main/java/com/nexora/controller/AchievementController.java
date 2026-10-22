package com.nexora.controller;

import com.nexora.evidence.AchievementRepository;
import com.nexora.evidence.EvidenceRecord;
import com.nexora.evidence.EvidenceRepository;
import com.nexora.repository.StudentRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Set;

@Controller
public class AchievementController {
    private static final Set<String> NON_ACHIEVEMENT_SYNC_METRICS = Set.of(
            "linkedin_profile", "linkedin_headline", "github_public_repositories", "codeforces_handle");

    private final AchievementRepository achievements;
    private final EvidenceRepository evidence;
    private final StudentRepository students;

    public AchievementController(AchievementRepository achievements, EvidenceRepository evidence, StudentRepository students) {
        this.achievements = achievements;
        this.evidence = evidence;
        this.students = students;
    }

    @GetMapping("/achievements")
    public String page(Authentication authentication, Model model,
                       @RequestParam(required = false) String saved,
                       @RequestParam(required = false) String error) {
        String registrationNo = registrationNo(authentication);
        if (registrationNo == null) return "redirect:/login";
        List<com.nexora.evidence.Achievement> achievementRows;
        try { achievementRows = achievements.find(registrationNo); }
        catch (Exception ignored) { achievementRows = List.of(); }
        model.addAttribute("achievements", achievementRows);
        List<java.util.Map<String,Object>> achievementFolders = new java.util.ArrayList<>();
        java.util.List<String> folderTypes = List.of("Hackathon", "Workshop", "Internship", "Certification", "Course completion", "Project", "Competition", "Other");
        for (String folderType : folderTypes) {
            List<com.nexora.evidence.Achievement> matching = achievementRows.stream().filter(row -> {
                String type = row.type() == null ? "" : row.type().trim().toLowerCase(java.util.Locale.ROOT);
                return switch (folderType) {
                    case "Hackathon" -> type.contains("hackathon");
                    case "Workshop" -> type.contains("workshop");
                    case "Internship" -> type.contains("internship");
                    case "Certification" -> type.contains("certification") || type.contains("certificate");
                    case "Course completion" -> type.contains("course");
                    case "Project" -> type.contains("project");
                    case "Competition" -> type.contains("competition") || type.contains("contest");
                    default -> !java.util.List.of("hackathon", "workshop", "internship", "certification", "certificate", "course", "project", "competition", "contest")
                            .stream().anyMatch(type::contains);
                };
            }).toList();
            java.util.Map<String,Object> folder = new java.util.LinkedHashMap<>();
            folder.put("name", folderType); folder.put("records", matching); folder.put("count", matching.size());
            achievementFolders.add(folder);
        }
        model.addAttribute("achievementFolders", achievementFolders);
        List<EvidenceRecord> synced;
        try {
            synced = evidence.findByStudent(registrationNo).stream()
                    .filter(e -> "API_VERIFIED".equalsIgnoreCase(e.verificationStatus()) || "OAUTH_VERIFIED".equalsIgnoreCase(e.verificationStatus()))
                    .filter(e -> !NON_ACHIEVEMENT_SYNC_METRICS.contains(e.metric()))
                    .toList();
        } catch (Exception ignored) { synced = List.of(); }
        model.addAttribute("syncedEvidence", synced);
        model.addAttribute("saved", saved != null);
        model.addAttribute("error", error);
        return "achievements";
    }

    @PostMapping("/achievements")
    public String add(Authentication authentication) {
        // Manual uploads are intentionally disabled: achievements are populated from connected-source evidence.
        if (registrationNo(authentication) == null) return "redirect:/login";
        return "redirect:/achievements?error=Manual%20achievement%20uploads%20are%20disabled.%20Connect%20a%20supported%20source%20in%20Connections.";
    }

    private String registrationNo(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) return null;
        try { return students.findByLoginId(authentication.getName()).map(s -> s.getRegistrationNo()).orElse(null); }
        catch (Exception ignored) { return null; }
    }
}
