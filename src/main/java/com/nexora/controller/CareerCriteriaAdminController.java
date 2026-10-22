package com.nexora.controller;

import com.nexora.career.CareerCriteriaAdminService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

@Controller
public class CareerCriteriaAdminController {
    private final CareerCriteriaAdminService criteria;

    public CareerCriteriaAdminController(CareerCriteriaAdminService criteria) { this.criteria = criteria; }

    @GetMapping("/admin/career-criteria")
    public String page(@RequestParam(required = false) String saved,
                       @RequestParam(required = false) String error, Model model) {
        model.addAttribute("criteria", criteria.recent());
        model.addAttribute("saved", saved != null);
        model.addAttribute("error", error);
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("metrics", CareerCriteriaMetric.values());
        model.addAttribute("sourceVersions", criteria.availableSourceVersions());
        return "admin-career-criteria";
    }

    @PostMapping("/admin/career-criteria")
    public String save(@RequestParam int yop,
                       @RequestParam String sourceVersion,
                       @RequestParam String band,
                       @RequestParam String metric,
                       @RequestParam(required = false) Double targetValue,
                       @RequestParam(required = false) String targetText,
                       @RequestParam(defaultValue = "false") boolean mandatory,
                       @RequestParam String sourceTitle,
                       @RequestParam String sourceUrl,
                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate observedAt,
                       @RequestParam(defaultValue = "false") boolean confirmSource) {
        try {
            criteria.save(yop, sourceVersion, band, metric, targetValue, targetText, mandatory,
                    sourceTitle, sourceUrl, observedAt, confirmSource);
            return "redirect:/admin/career-criteria?saved=true";
        } catch (IllegalArgumentException ex) {
            return "redirect:/admin/career-criteria?error=" + java.net.URLEncoder.encode(
                    ex.getMessage(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    public enum CareerCriteriaMetric {
        CGPA("CGPA", "CGPA"), TENTH("tenth_percentile", "Class 10 marks (%)"),
        TWELFTH("twelfth_percentile", "Class 12 marks (%)"), ARREARS("active_arrears", "Active arrears"),
        APTITUDE("aptitude", "Aptitude score"), TECHNICAL("technical_skills", "Technical skills score"),
        CODING("coding_problems", "Coding problems"), INTERNSHIPS("internships", "Internships"),
        PROJECTS("projects", "Projects"), CERTIFICATIONS("certifications", "Certifications"),
        HACKATHONS("hackathons", "Hackathons"), LINKEDIN("linkedin_updated", "LinkedIn profile updated"),
        PORTFOLIO("portfolio", "Portfolio"), RESUME("resume_verified", "Resume verified"),
        PROFILE("profile_verified", "Profile verified"), COMMUNICATION("communication", "Communication score");

        private final String key;
        private final String label;
        CareerCriteriaMetric(String key, String label) { this.key = key; this.label = label; }
        public String getKey() { return key; }
        public String getLabel() { return label; }
    }
}
