package com.nexora.controller;

import com.nexora.career.market.CareerDirectoryService;
import com.nexora.career.market.CareerDirectoryService.CareerCompany;
import com.nexora.career.market.CareerDirectoryService.CareerFamily;
import com.nexora.career.market.CareerDirectoryService.CareerLocation;
import com.nexora.career.market.CareerDirectoryService.CareerRole;
import com.nexora.career.market.CareerDirectoryService.CareerSkill;
import com.nexora.career.CompanyRoleCriteriaService;
import com.nexora.model.Student;
import com.nexora.repository.StudentRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Locale;

/** Dedicated evidence-aware career explorer pages; no inferred employer eligibility is shown. */
@Controller
public class CareerExplorerController {
    private final CareerDirectoryService directory;
    private final StudentRepository students;
    private final CompanyRoleCriteriaService companyRoleCriteria;

    public CareerExplorerController(CareerDirectoryService directory, StudentRepository students,
                                    CompanyRoleCriteriaService companyRoleCriteria) {
        this.directory = directory;
        this.students = students;
        this.companyRoleCriteria = companyRoleCriteria;
    }

    @GetMapping("/career/role/{slug}")
    public String role(@PathVariable String slug, @RequestParam(required = false) String location,
                       Authentication authentication, Model model) {
        CareerRole role = directory.role(slug);
        if (role == null) return "redirect:/career";
        CareerLocation selectedLocation = null;
        if (location == null || location.isBlank()) {
            if (role.marketEvidence() != null) selectedLocation = directory.location("bengaluru");
        } else if (!"all".equalsIgnoreCase(location)) {
            selectedLocation = directory.location(location);
        }
        populate(model, authentication);
        model.addAttribute("pageType", "role");
        model.addAttribute("role", role);
        model.addAttribute("selectedRoleLocation", selectedLocation == null ? "all" : selectedLocation.slug());
        model.addAttribute("selectedRoleLocationName", selectedLocation == null ? "All locations" : selectedLocation.name());
        model.addAttribute("roleMarketEvidenceVisible", role.marketEvidence() != null
                && selectedLocation != null && "bengaluru".equals(selectedLocation.slug()));
        model.addAttribute("relatedRoles", directory.related(role, 8));
        List<com.nexora.career.CompanyRoleCriteriaService.CompanyRoleCriterion> roleCriteria = companyRoleCriteria.findVerifiedForRoleTitles(role.title(), role.aliases());
        if (selectedLocation != null) {
            String city = selectedLocation.name().toLowerCase(Locale.ROOT);
            List<String> cityTerms = new java.util.ArrayList<>(List.of(city));
            if ("bengaluru".equals(selectedLocation.slug())) cityTerms.addAll(List.of("bangalore", "banglore"));
            java.util.Set<String> matchingProgrammes = roleCriteria.stream()
                    .filter(item -> "work_location".equalsIgnoreCase(item.metric())
                            && cityTerms.stream().anyMatch(term -> item.displayTarget().toLowerCase(Locale.ROOT).contains(term)))
                    .map(item -> item.companyName() + "|" + item.hiringProgram() + "|" + item.yop())
                    .collect(java.util.stream.Collectors.toSet());
            roleCriteria = roleCriteria.stream()
                    .filter(item -> matchingProgrammes.contains(item.companyName() + "|" + item.hiringProgram() + "|" + item.yop()))
                    .toList();
        }
        model.addAttribute("verifiedCompanyRoleEvidence", roleCriteria);
        model.addAttribute("pageTitle", role.title());
        model.addAttribute("pageSubtitle", role.family());
        return "career";
    }

    @GetMapping("/career/family/{slug}")
    public String family(@PathVariable String slug,
                         Authentication authentication, Model model) {
        CareerFamily family = directory.family(slug);
        if (family == null) return "redirect:/career";
        List<CareerRole> rolesInFamily = directory.rolesByFamily(family.slug(), "az");
        populate(model, authentication);
        model.addAttribute("pageType", "family");
        model.addAttribute("family", family);
        model.addAttribute("rolesInFamily", rolesInFamily);
        model.addAttribute("roleCount", rolesInFamily.size());
        model.addAttribute("pageTitle", family.name());
        model.addAttribute("pageSubtitle", family.description());
        return "career";
    }

    @GetMapping("/career/company/{slug}")
    public String company(@PathVariable String slug, Authentication authentication, Model model) {
        CareerCompany company = directory.company(slug);
        if (company == null) return "redirect:/career";
        populate(model, authentication);
        model.addAttribute("pageType", "company");
        model.addAttribute("company", company);
        model.addAttribute("verifiedCompanyRoleEvidence", companyRoleCriteria.findVerifiedForCompany(company.name(), company.aliases()));
        model.addAttribute("pageTitle", company.name());
        model.addAttribute("pageSubtitle", "Company identity and official destinations");
        return "career";
    }

    @GetMapping("/career/location/{slug}")
    public String location(@PathVariable String slug, Authentication authentication, Model model) {
        CareerLocation location = directory.location(slug);
        if (location == null) return "redirect:/career";
        populate(model, authentication);
        model.addAttribute("pageType", "location");
        model.addAttribute("location", location);
        model.addAttribute("rolesForLocation", directory.rolesForLocation(location));
        model.addAttribute("pageTitle", location.name());
        model.addAttribute("pageSubtitle", "Location-specific career evidence");
        return "career";
    }

    @GetMapping("/career/skill/{slug}")
    public String skill(@PathVariable String slug, Authentication authentication, Model model) {
        CareerSkill skill = directory.skill(slug);
        if (skill == null) return "redirect:/career";
        populate(model, authentication);
        model.addAttribute("pageType", "skill");
        model.addAttribute("skill", skill);
        model.addAttribute("rolesForSkill", directory.rolesForSkill(skill.slug()));
        model.addAttribute("pageTitle", skill.name());
        model.addAttribute("pageSubtitle", "Explore roles associated with this skill");
        return "career";
    }

    private void populate(Model model, Authentication authentication) {
        Student student = null;
        if (authentication != null && authentication.isAuthenticated()) {
            String login = authentication.getName();
            if (login != null && !login.isBlank() && !"anonymousUser".equals(login)) {
                student = students.findByLoginId(login).orElse(null);
            }
        }
        // Never substitute another active student's profile when the current principal is unresolved.
        model.addAttribute("studentInitial", student != null && student.getFullName() != null && !student.getFullName().isBlank()
                ? student.getFullName().substring(0, 1).toUpperCase(Locale.ROOT) : "N");
        model.addAttribute("directoryRoles", directory.roles().stream().sorted(java.util.Comparator.comparing(CareerRole::title, String.CASE_INSENSITIVE_ORDER)).toList());
        model.addAttribute("directoryFamilies", directory.families().stream().sorted(java.util.Comparator.comparing(CareerFamily::name, String.CASE_INSENSITIVE_ORDER)).toList());
        model.addAttribute("directoryCompanies", directory.companies());
        model.addAttribute("directoryLocations", directory.locations());
        model.addAttribute("directorySkills", directory.skills());
        model.addAttribute("marketRoles", directory.rolesWithMarketEvidence());
        // Always provide a useful baseline when no source-verified employer rows exist.
        // These are planning estimates only and are never presented as company policy.
        model.addAttribute("eligibilityPlanningBenchmarks", List.of(
                benchmark("Class 10 marks", "60% or above", "General planning estimate; exact employer programme may differ."),
                benchmark("Class 12 marks", "60% or above", "General planning estimate; exact employer programme may differ."),
                benchmark("UG CGPA", "6.0 / 10 or above", "General planning estimate; check the university conversion policy."),
                benchmark("Active backlogs", "Aim for 0 at selection", "Employer and institutional rules differ by drive."),
                benchmark("Degree / branch", "Check role-specific eligible branches", "Do not assume every CSE-related role accepts every degree."),
                benchmark("Graduation year", "Match the programme's batch window", "Check the current hiring notice for the exact YOP.")));
    }

    private java.util.Map<String,String> benchmark(String metric, String target, String note) {
        java.util.Map<String,String> row = new java.util.LinkedHashMap<>();
        row.put("metric", metric); row.put("target", target); row.put("note", note);
        return row;
    }
}
