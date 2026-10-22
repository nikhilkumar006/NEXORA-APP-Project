package com.nexora.controller;

import com.nexora.career.CompanyRoleCriteriaService;
import com.nexora.career.market.CareerDirectoryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@Controller
public class CompanyRoleCriteriaAdminController {
    private final CompanyRoleCriteriaService criteria;
    private final CareerDirectoryService directory;

    public CompanyRoleCriteriaAdminController(CompanyRoleCriteriaService criteria, CareerDirectoryService directory) {
        this.criteria = criteria;
        this.directory = directory;
    }

    @GetMapping("/admin/company-role-criteria")
    public String page(@RequestParam(required = false) String saved,
                       @RequestParam(required = false) String error, Model model) {
        model.addAttribute("criteria", criteria.recent());
        model.addAttribute("roles", directory.roles());
        model.addAttribute("companies", directory.companies().stream()
                .map(com.nexora.career.market.CareerDirectoryService.CareerCompany::name).toList());
        model.addAttribute("saved", saved != null);
        model.addAttribute("error", error);
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("metrics", List.of(
                new Metric("class_10_marks", "Class 10 marks"), new Metric("class_12_marks", "Class 12 marks"),
                new Metric("cgpa", "CGPA / GPA"), new Metric("degree_branch", "Degree / branch"),
                new Metric("graduation_year", "Year of passing"), new Metric("backlogs", "Backlogs / arrears"),
                new Metric("experience", "Experience"), new Metric("required_skills", "Required skills"),
                new Metric("preferred_skills", "Preferred skills"), new Metric("certifications", "Certifications"),
                new Metric("work_location", "Work location"), new Metric("other", "Other stated criterion")
        ));
        return "admin-company-role-criteria";
    }

    @PostMapping("/admin/company-role-criteria")
    public String save(@RequestParam String companyName, @RequestParam String roleTitle,
                       @RequestParam String hiringProgram, @RequestParam int yop, @RequestParam String metric,
                       @RequestParam(required = false) Double targetValue, @RequestParam(required = false) String targetText,
                       @RequestParam(defaultValue = "false") boolean mandatory, @RequestParam String sourceTitle,
                       @RequestParam String sourceUrl,
                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate observedAt,
                       @RequestParam(defaultValue = "false") boolean confirmSource) {
        try {
            if (directory.role(roleTitle) == null) throw new IllegalArgumentException("Select a canonical role from NEXORA's role catalogue.");
            var company = directory.company(companyName);
            String canonicalCompany = company == null ? companyName : company.name();
            criteria.save(canonicalCompany, directory.role(roleTitle).title(), hiringProgram, yop, metric,
                    targetValue, targetText, mandatory, sourceTitle, sourceUrl, observedAt, confirmSource);
            return "redirect:/admin/company-role-criteria?saved=true";
        } catch (IllegalArgumentException ex) {
            return "redirect:/admin/company-role-criteria?error=" + URLEncoder.encode(ex.getMessage(), StandardCharsets.UTF_8);
        }
    }

    public record Metric(String key, String label) {}
}
