package com.nexora.controller;

import com.nexora.career.CareerGpsSelection;
import com.nexora.career.CareerGpsService;
import com.nexora.career.CareerProfile;
import com.nexora.career.CareerService;
import com.nexora.career.CompanyRoleCriteriaService;
import com.nexora.career.CompanyRoleCriteriaService.CompanyRoleCriterion;
import com.nexora.career.Trajectory;
import com.nexora.career.market.CareerDirectoryService;
import com.nexora.career.market.CareerDirectoryService.CareerRole;
import com.nexora.model.Student;
import com.nexora.repository.StudentRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Locale;

@Controller
public class CareerGpsController {
    private static final List<String> BANDS = List.of("Standard", "Dream", "Super Dream", "Marquee");
    private static final List<String> COMPANIES = List.of(
            "Adobe", "Amazon", "Atlassian", "Accenture", "Cognizant", "Deloitte", "Flipkart",
            "Freshworks", "Google", "IBM", "Infosys", "Microsoft", "NVIDIA", "Oracle",
            "PayPal", "ServiceNow", "TCS", "Walmart Global Tech", "Wipro", "Zoho"
    );

    private final CareerService careers;
    private final CareerGpsService gps;
    private final CareerDirectoryService directory;
    private final StudentRepository students;
    private final CompanyRoleCriteriaService companyRoleCriteria;
    private final JdbcTemplate jdbc;
    private final com.nexora.career.PlacementPlannerService placementPlanner;
    private final DevelopmentGpsPageCache developmentPageCache;

    public CareerGpsController(CareerService careers, CareerGpsService gps,
                               CareerDirectoryService directory, StudentRepository students,
                               CompanyRoleCriteriaService companyRoleCriteria, JdbcTemplate jdbc,
                               com.nexora.career.PlacementPlannerService placementPlanner,
                               DevelopmentGpsPageCache developmentPageCache) {
        this.careers = careers;
        this.gps = gps;
        this.directory = directory;
        this.students = students;
        this.companyRoleCriteria = companyRoleCriteria;
        this.jdbc = jdbc;
        this.placementPlanner = placementPlanner;
        this.developmentPageCache = developmentPageCache;
    }

    @GetMapping({"/career-gps", "/career-selection"})
    public String page(Authentication authentication, Model model,
                       @RequestParam(required = false) String saved,
                       @RequestParam(required = false) String error,
                       @RequestParam(required = false) String role) {
        Student student = findStudent(authentication);
        if (student == null) return "redirect:/login?error=account";

        String registrationNo = student.getRegistrationNo();
        CareerProfile savedProfile = careers.profile(registrationNo).orElse(null);
        CareerProfile profile = savedProfile != null ? savedProfile :
                new CareerProfile(registrationNo, "On-campus placement", "Off-campus placement",
                        "Keep options open", "Not selected", "Dream", "SRM-PLACEMENT-2026");
        CareerGpsSelection selection = gps.find(registrationNo).orElse(null);
        boolean hasSavedPath = selection != null || savedProfile != null;
        String primaryPath = selection != null ? selection.primaryPath() : profile.primaryPath();
        if (!isCampusPath(primaryPath) && !isOffCampusPath(primaryPath)) primaryPath = "On-campus placement";

        CareerRole roleInfo = directory.role(selection == null ? null : selection.targetRole());
        if (roleInfo == null) roleInfo = directory.role(profile.targetRole());
        String targetRole = roleInfo == null ? "" : roleInfo.title();
        boolean roleChosen = roleInfo != null;
        // A role detail page can open Career Selection with ?role=<slug-or-title>.
        // Honour that explicit intent so students do not arrive at a blank/unselected form.
        if (role != null && !role.isBlank()) {
            CareerRole requestedRole = directory.role(role);
            if (requestedRole != null) {
                // A Career Guidance role-link pre-fills the target only. Let the student
                // choose on-campus/off-campus independently; do not silently switch paths.
                targetRole = requestedRole.title();
                roleChosen = true;
            }
        }
        String targetBand = validBand(selection != null ? selection.targetBand() : profile.targetBand());
        String sourceVersion = validSource(selection != null ? selection.sourceVersion() : profile.sourceVersion());
        String targetCompany = selection == null || selection.targetCompany() == null ? "" : selection.targetCompany();

        int currentSemester = student.getCurrentSemester();
        // The development progress query is only needed after the secondary-path unlock semester.
        // Previously it executed seven aggregate SQL queries on every visit, even for first/second-year students.
        int devProgress = currentSemester >= 6 ? developmentProgress(registrationNo, targetRole) : 100;
        boolean secondaryUnlocked = currentSemester >= 6 && devProgress <= 75;

        model.addAttribute("student", student);
        model.addAttribute("studentInitial", student.getFullName() == null || student.getFullName().isBlank()
                ? "N" : student.getFullName().trim().substring(0, 1).toUpperCase(Locale.ROOT));
        model.addAttribute("profile", profile);
        model.addAttribute("selection", selection);
        model.addAttribute("hasSavedPath", hasSavedPath);
        model.addAttribute("primaryPath", primaryPath);
        model.addAttribute("targetRole", targetRole);
        model.addAttribute("roleChosen", roleChosen);
        model.addAttribute("targetBand", targetBand);
        model.addAttribute("sourceVersion", sourceVersion);
        model.addAttribute("targetCompany", targetCompany);
        model.addAttribute("targetProgram", selection == null || selection.targetProgram() == null
                || selection.targetProgram().isBlank() ? "Not specified" : selection.targetProgram());
        model.addAttribute("placementPlannerYop", placementPlanner.resolveYearOfPassing(student.getBatchEndYear(), student.getBatchStartYear()));
        model.addAttribute("bands", BANDS);
        java.util.LinkedHashSet<String> companyOptions = new java.util.LinkedHashSet<>(COMPANIES);
        directory.companies().forEach(company -> {
            companyOptions.add(company.name());
            companyOptions.addAll(company.aliases());
        });
        model.addAttribute("companies", companyOptions.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList());
        model.addAttribute("roles", directory.roles());
        model.addAttribute("careerCount", directory.roles().size());
        model.addAttribute("saved", saved);
        model.addAttribute("error", error != null ? error : (role != null && !role.isBlank() && directory.role(role) == null ? "role" : null));
        model.addAttribute("currentSemester", currentSemester);
        model.addAttribute("secondaryUnlocked", secondaryUnlocked);
        model.addAttribute("secondaryLockReason", currentSemester < 6
                ? "Secondary path unlocks in Semester 6 (3.5th year)."
                : devProgress > 75 ? "Secondary path is offered only when Development GPS is 75% or lower."
                : "Checking related careers against your recorded skills…");
        model.addAttribute("secondarySelection", currentSemester >= 6 ? secondarySelection(registrationNo) : java.util.Map.of());
        // Eligible secondary career options are loaded on demand in a separate endpoint so this form
        // can render immediately. The endpoint rechecks the eligibility rules before returning options.
        return "career-gps";
    }

    @GetMapping("/career-selection/secondary-candidates")
    @ResponseBody
    public List<String> secondaryCandidates(Authentication authentication) {
        Student student = findStudent(authentication);
        if (student == null || student.getCurrentSemester() < 6) return List.of();
        String registrationNo = student.getRegistrationNo();
        CareerGpsSelection primary = gps.find(registrationNo).orElse(null);
        if (primary == null || developmentProgress(registrationNo, primary.targetRole()) > 75) return List.of();
        CareerRole primaryRole = directory.role(primary.targetRole());
        if (primaryRole == null || primaryRole.skills() == null || primaryRole.skills().isEmpty()) return List.of();

        java.util.LinkedHashSet<String> results = new java.util.LinkedHashSet<>();
        for (CareerRole candidate : directory.roles()) {
            if (candidate.skills() == null || candidate.skills().isEmpty()
                    || candidate.title().equalsIgnoreCase(primaryRole.title())
                    || skillOverlap(primaryRole.skills(), candidate.skills()) < 0.50) continue;
            // Use the exact same readiness calculation as the POST save handler. This endpoint
            // runs on demand after page render, avoiding dozens of readiness queries during GET,
            // while ensuring every option shown here can pass the server-side save validation.
            try {
                if (gps.roleReadiness(registrationNo, candidate.skills()).percent() > 75) {
                    results.add(candidate.title());
                }
            } catch (RuntimeException ignored) {
                // If readiness cannot be calculated, do not show an option that cannot be verified.
            }
        }
        return results.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    @PostMapping("/career-selection/secondary")
    @Transactional
    public String saveSecondary(Authentication authentication, @RequestParam String secondaryRole,
                                @RequestParam String secondaryCompany, @RequestParam(defaultValue="BOTH") String mode) {
        Student student = findStudent(authentication);
        if (student == null) return "redirect:/login?error=account";
        CareerGpsSelection primary = gps.find(student.getRegistrationNo()).orElse(null);
        if (student.getCurrentSemester() < 6 || developmentProgress(student.getRegistrationNo(), primary == null ? "" : primary.targetRole()) > 75) return "redirect:/career-selection?error=secondary-locked";
        CareerRole candidate = directory.role(secondaryRole);
        CareerRole primaryRole = directory.role(primary == null ? "" : primary.targetRole());
        if (candidate == null || primaryRole == null || candidate.title().equalsIgnoreCase(primaryRole.title()) || candidate.skills() == null || primaryRole.skills() == null || skillOverlap(primaryRole.skills(), candidate.skills()) < 0.50 || gps.roleReadiness(student.getRegistrationNo(), candidate.skills()).percent() <= 75) return "redirect:/career-selection?error=secondary-not-qualified";
        String company = secondaryCompany == null ? "" : secondaryCompany.trim();
        if (company.isBlank() || company.length() > 180) return "redirect:/career-selection?error=secondary-company";
        String safeMode = java.util.Set.of("PRIMARY_ONLY", "SECONDARY_ONLY", "BOTH").contains(mode) ? mode : "BOTH";
        jdbc.update("INSERT INTO student_secondary_career_paths(registration_no,secondary_role,secondary_company,mode) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE secondary_role=VALUES(secondary_role),secondary_company=VALUES(secondary_company),mode=VALUES(mode),updated_at=CURRENT_TIMESTAMP", student.getRegistrationNo(), candidate.title(), company, safeMode);
        developmentPageCache.evict(student.getRegistrationNo());
        return "redirect:/career-selection?saved=secondary";
    }

    private int developmentProgress(String reg, String role) {
        try {
            String c = role == null ? "" : role.toLowerCase(Locale.ROOT);
            int projects = count("SELECT COUNT(*) FROM student_projects WHERE registration_no=? AND LOWER(status)='completed'", reg);
            int internships = count("SELECT COUNT(DISTINCT LOWER(TRIM(title))) FROM (SELECT title FROM student_experiences WHERE registration_no=? AND LOWER(experience_type)='internship' UNION ALL SELECT title FROM achievement_records WHERE registration_no=? AND LOWER(achievement_type)='internship') x", reg, reg);
            int hackathons = count("SELECT COUNT(DISTINCT LOWER(TRIM(title))) FROM (SELECT title FROM student_experiences WHERE registration_no=? AND LOWER(experience_type)='hackathon' UNION ALL SELECT title FROM achievement_records WHERE registration_no=? AND LOWER(achievement_type)='hackathon') x", reg, reg);
            int certs = count("SELECT COUNT(DISTINCT LOWER(TRIM(title))) FROM (SELECT title FROM achievement_records WHERE registration_no=? AND LOWER(achievement_type) IN ('certification','course completion') UNION ALL SELECT title FROM student_experiences WHERE registration_no=? AND LOWER(experience_type)='certification') x", reg, reg);
            int problems = count("SELECT COUNT(*) FROM student_problem_logs WHERE registration_no=?", reg);
            int courses = count("SELECT COUNT(*) FROM student_learning_resources WHERE registration_no=? AND status='COMPLETED'", reg);
            int projectTarget = c.contains("security") || c.contains("cloud") || c.contains("devops") ? 2 : 3;
            int hackTarget = c.contains("data") || c.contains("ai") || c.contains("machine learning") ? 3 : 5;
            int certTarget = c.contains("security") || c.contains("cloud") ? 3 : 2;
            int problemTarget = c.contains("data") || c.contains("ai") ? 30 : (c.contains("security") || c.contains("cloud") ? 20 : 50);
            int githubRepos = count("SELECT COUNT(DISTINCT reference_url) FROM student_evidence WHERE registration_no=? AND category='PROJECT' AND metric='github_repository' AND verification_status='API_VERIFIED'", reg);
            int githubTarget = c.contains("marquee") ? 5 : (c.contains("super dream") ? 4 : (c.contains("standard") ? 1 : 3));
            return (int)Math.round(java.util.List.of(Math.min(100,projects*100.0/projectTarget),Math.min(100,internships*100.0),Math.min(100,hackathons*100.0/hackTarget),Math.min(100,certs*100.0/certTarget),Math.min(100,problems*100.0/problemTarget),Math.min(100,githubRepos*100.0/githubTarget),Math.min(100,courses*100.0/3)).stream().mapToDouble(Double::doubleValue).average().orElse(0));
        } catch (Exception ignored) { return 0; }
    }

    private double skillOverlap(List<String> primarySkills, List<String> candidateSkills) {
        if (primarySkills == null || candidateSkills == null || candidateSkills.isEmpty()) return 0;
        java.util.Set<String> primary = primarySkills.stream().filter(java.util.Objects::nonNull).map(this::normalizeSkill).collect(java.util.stream.Collectors.toSet());
        java.util.Set<String> candidate = candidateSkills.stream().filter(java.util.Objects::nonNull).map(this::normalizeSkill).filter(s -> !s.isBlank()).collect(java.util.stream.Collectors.toSet());
        if (candidate.isEmpty()) return 0;
        long overlap = candidate.stream().filter(primary::contains).count();
        return overlap / (double) candidate.size();
    }

    private String normalizeSkill(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""); }

    private int count(String sql, Object... args) { try { Integer n = jdbc.queryForObject(sql, Integer.class, args); return n == null ? 0 : n; } catch (Exception ignored) { return 0; } }

    private java.util.Map<String,Object> secondarySelection(String reg) {
        try { java.util.List<java.util.Map<String,Object>> rows = jdbc.queryForList("SELECT secondary_role,secondary_company,mode,updated_at FROM student_secondary_career_paths WHERE registration_no=?", reg); return rows.isEmpty() ? java.util.Map.of() : rows.get(0); }
        catch (Exception ignored) { return java.util.Map.of(); }
    }

    @GetMapping("/career-gps/programs")
    @ResponseBody
    public List<String> programs(Authentication authentication, @RequestParam String company,
                                 @RequestParam String role, @RequestParam(required = false) Integer yop) {
        Student student = findStudent(authentication);
        if (student == null) return List.of("Not specified");
        CareerRole roleInfo = directory.role(role);
        if (roleInfo == null || company == null || company.trim().length() > 180) return List.of("Not specified");
        int year = placementPlanner.resolveYearOfPassing(student.getBatchEndYear(), student.getBatchStartYear());
        return companyRoleCriteria.programs(canonicalCompany(company.trim()), roleInfo.title(), year);
    }

    @GetMapping("/career-gps/criteria")
    @ResponseBody
    public List<CompanyRoleCriterionView> companyCriteria(Authentication authentication,
            @RequestParam String company, @RequestParam String role,
            @RequestParam(defaultValue = "Not specified") String program,
            @RequestParam(required = false) Integer yop) {
        Student student = findStudent(authentication);
        if (student == null || company == null || company.trim().length() > 180) return List.of();
        CareerRole roleInfo = directory.role(role);
        if (roleInfo == null) return List.of();
        int year = placementPlanner.resolveYearOfPassing(student.getBatchEndYear(), student.getBatchStartYear());
        return companyRoleCriteria.findVerified(canonicalCompany(company.trim()), roleInfo.title(), program, year).stream()
                .map(c -> new CompanyRoleCriterionView(c.companyName(), c.roleTitle(), c.hiringProgram(), c.yop(),
                        c.displayMetric(), c.displayTarget(), c.mandatory(), c.sourceTitle(), c.sourceUrl(),
                        c.observedAt() == null ? "—" : c.observedAt().toString()))
                .toList();
    }

    public record CompanyRoleCriterionView(String companyName, String roleTitle, String hiringProgram, int yop,
                                           String metric, String target, boolean mandatory, String sourceTitle,
                                           String sourceUrl, String observedAt) {}

    @PostMapping({"/career-gps", "/career-selection"})
    @Transactional
    public String save(Authentication authentication,
                       @RequestParam String primaryPath,
                       @RequestParam(defaultValue = "") String targetCompany,
                       @RequestParam(defaultValue = "Not specified") String targetProgram,
                       @RequestParam(defaultValue = "") String targetRole,
                       @RequestParam(defaultValue = "Dream") String targetBand,
                       @RequestParam(defaultValue = "SRM-PLACEMENT-2026") String sourceVersion) {
        Student student = findStudent(authentication);
        if (student == null) return "redirect:/login?error=account";
        String registrationNo = student.getRegistrationNo();
        if (!isCampusPath(primaryPath) && !isOffCampusPath(primaryPath)) {
            return "redirect:/career-selection?error=path";
        }
        String path = isOffCampusPath(primaryPath) ? "Off-campus placement" : "On-campus placement";
        String band = validBand(targetBand);
        String source = validSource(sourceVersion);
        String company = targetCompany == null ? "" : targetCompany.trim();
        if (company.length() > 180) company = company.substring(0, 180);
        company = canonicalCompany(company);
        if ("Off-campus placement".equals(path) && company.isBlank()) {
            return "redirect:/career-selection?error=company";
        }
        if (targetProgram == null || targetProgram.isBlank()) targetProgram = "Not specified";
        if (targetProgram.length() > 180) return "redirect:/career-selection?error=program";
        CareerProfile old = careers.profile(registrationNo).orElse(
                new CareerProfile(registrationNo, path, "Off-campus placement", "Keep options open", "Not selected", band, source));
        CareerGpsSelection previousSelection = gps.find(registrationNo).orElse(null);
        CareerRole selectedRole = directory.role(targetRole);
        // On-campus planning only asks for a package band; retain an existing valid target
        // for downstream modules when the form intentionally omits the role selector.
        if (selectedRole == null && "On-campus placement".equals(path)) {
            selectedRole = directory.role(previousSelection == null ? null : previousSelection.targetRole());
            if (selectedRole == null) selectedRole = directory.role(old.targetRole());
        }
        // A saved target role is the shared key for Career Guidance, Development GPS,
        // and Recommended Courses, irrespective of placement path. Requiring it here
        // prevents the downstream modules from receiving "Not selected" and going blank.
        if (selectedRole == null && "Off-campus placement".equals(path)) return "redirect:/career-selection?error=role";
        String role = selectedRole == null ? "Not selected" : selectedRole.title();
        if ("Off-campus placement".equals(path)) {
            if (company.isBlank()) return "redirect:/career-selection?error=company";
        } else {
            company = "";
            targetProgram = "Not specified";
        }
        int yearOfPassing = placementPlanner.resolveYearOfPassing(student.getBatchEndYear(), student.getBatchStartYear());
        if ("Off-campus placement".equals(path) && !targetProgram.equals("Not specified")
                && !companyRoleCriteria.programs(company, role, yearOfPassing).contains(targetProgram)) {
            return "redirect:/career-selection?error=program";
        }

        
        careers.saveProfile(new CareerProfile(registrationNo, path,
                "Off-campus placement".equals(path) ? "On-campus placement" : "Off-campus placement",
                old.higherStudyPreference() == null ? "Keep options open" : old.higherStudyPreference(),
                role, band, source));
        gps.save(new CareerGpsSelection(registrationNo, path, company, targetProgram, role, band, source));
        developmentPageCache.evict(registrationNo);
        return "redirect:/career-selection?saved=true";
    }

    private String canonicalCompany(String value) {
        if (value == null || value.isBlank()) return "";
        var known = directory.company(value.trim());
        return known == null ? value.trim() : known.name();
    }

    private Student findStudent(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) return null;
        return students.findByLoginId(authentication.getName()).orElse(null);
    }


    private String validBand(String band) { return BANDS.contains(band) ? band : "Dream"; }
    private String validSource(String source) {
        return source != null && source.matches("SRM-PLACEMENT-[0-9]{4}")
                && careers.availableSourceVersions().contains(source) ? source : "SRM-PLACEMENT-2026";
    }
    private boolean isCampusPath(String path) {
        return path != null && "on-campus placement".equals(path.trim().toLowerCase(Locale.ROOT));
    }
    private boolean isOffCampusPath(String path) {
        return path != null && "off-campus placement".equals(path.trim().toLowerCase(Locale.ROOT));
    }
    private String yearLabel(int year) { return year <= 1 ? "1st Year" : year == 2 ? "2nd Year" : year == 3 ? "3rd Year" : "4th Year"; }
}
