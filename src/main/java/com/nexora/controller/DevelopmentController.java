package com.nexora.controller;

import com.nexora.development.DevelopmentService;
import com.nexora.development.CareerTarget;
import com.nexora.development.Skill;
import com.nexora.development.StudentProject;
import com.nexora.development.ExperienceEntry;
import com.nexora.development.DevelopmentInsight;
import com.nexora.career.CareerGpsService;
import com.nexora.career.CareerService;
import com.nexora.career.CareerProfile;
import com.nexora.career.CareerGpsSelection;
import com.nexora.career.market.CareerDirectoryService;
import com.nexora.career.market.LiveCareerMarketService;
import com.nexora.repository.StudentRepository;
import com.nexora.model.Student;
import com.nexora.career.GradeCalculatorService;
import com.nexora.career.PlacementPlannerService;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping({"/development", "/development-gps"})
public class DevelopmentController {
    private final DevelopmentService service;
    private final CareerDirectoryService directory;
    private final LiveCareerMarketService liveMarket;
    private final CareerGpsService careerGps;
    private final CareerService careerProfiles;
    private final StudentRepository students;
    private final JdbcTemplate jdbc;
    private final GradeCalculatorService gradeCalculator;
    private final PlacementPlannerService placementPlanner;
    private final DevelopmentGpsPageCache pageCache;
    public DevelopmentController(DevelopmentService service, CareerDirectoryService directory, LiveCareerMarketService liveMarket, CareerGpsService careerGps, CareerService careerProfiles, StudentRepository students, JdbcTemplate jdbc, GradeCalculatorService gradeCalculator, PlacementPlannerService placementPlanner, DevelopmentGpsPageCache pageCache) {
        this.service = service;
        this.directory = directory;
        this.liveMarket = liveMarket;
        this.careerGps = careerGps;
        this.careerProfiles = careerProfiles;
        this.students = students;
        this.jdbc = jdbc;
        this.gradeCalculator = gradeCalculator;
        this.placementPlanner = placementPlanner;
        this.pageCache = pageCache;
    }

    @GetMapping
    public String page(Authentication auth, Model model) {
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null
                || auth.getName().isBlank() || "anonymousUser".equals(auth.getName())) return "redirect:/login?error=account";
        Student student = safeValue(() -> students.findByLoginId(auth.getName()).orElse(null), null);
        if (student == null) return "redirect:/login?error=account";
        String reg = student.getRegistrationNo();
        // Most students revisit this page while updating one item. A brief per-student cache
        // makes back-navigation fast; every Development GPS mutation evicts it immediately.
        Map<String, Object> cachedModel = pageCache.get(reg);
        if (cachedModel != null) {
            model.addAllAttributes(cachedModel);
            return "development";
        }
        // Optional evidence tables may be missing on older local databases. Keep GPS usable
        // and display empty states while the schema is repaired rather than a generic 500 page.
        List<Skill> studentSkills = safeList(() -> service.getSkills(reg));
        List<StudentProject> studentProjects = safeList(() -> service.getProjects(reg));
        List<ExperienceEntry> studentExperiences = safeList(() -> service.getExperiences(reg));
        model.addAttribute("skills", studentSkills);
        model.addAttribute("projects", studentProjects);
        model.addAttribute("experiences", studentExperiences);
        CareerTarget safeCareerTarget = safeValue(() -> service.getCareerTarget(reg), null);
        CareerGpsSelection gpsSelection = safeValue(() -> careerGps.find(reg).orElse(null), null);
        CareerProfile savedCareerProfile = safeValue(() -> careerProfiles.profile(reg).orElse(null), null);
        // Resolve one canonical role for the whole GPS page. Prefer Career Selection, then
        // Career Guidance, then the legacy Development target so existing records keep mapping.
        com.nexora.career.market.CareerDirectoryService.CareerRole mappedRole = safeValue(
                () -> directory.role(gpsSelection == null ? null : gpsSelection.targetRole()), null);
        if (mappedRole == null) mappedRole = safeValue(
                () -> directory.role(savedCareerProfile == null ? null : savedCareerProfile.targetRole()), null);
        if (mappedRole == null) mappedRole = safeValue(
                () -> directory.role(safeCareerTarget == null ? null : safeCareerTarget.targetRole()), null);
        String effectiveTargetRole = mappedRole == null ? "" : mappedRole.title();
        // Feed the resolved canonical role to recommendations too. Merely displaying a role
        // from Career Guidance while passing an empty legacy target would otherwise show generic skills.
        final CareerTarget effectiveCareerTarget = mappedRole != null
                && (safeCareerTarget == null || directory.role(safeCareerTarget.targetRole()) == null)
                ? new CareerTarget(reg, mappedRole.title(), safeCareerTarget == null ? "" : safeCareerTarget.notes())
                : safeCareerTarget;
        model.addAttribute("careerTarget", effectiveCareerTarget);
        model.addAttribute("gpsTargetRole", effectiveTargetRole);
        model.addAttribute("careerRoles", safeList(() -> directory.roles()));
        List<String> recommendedSkills = safeList(() -> service.getRecommendedSkillsFor(effectiveCareerTarget, gpsSelection, studentSkills));
        model.addAttribute("recommendedSkills", recommendedSkills);
        model.addAttribute("insights", safeList(() -> service.getInsightsFor(effectiveCareerTarget, gpsSelection, studentSkills, studentProjects, studentExperiences, recommendedSkills)));
        java.util.Map<String,List<String>> graph = safeValue(() -> service.getSkillGraph(), java.util.Map.of());
        java.util.Map<String,List<String>> safeGraph = new LinkedHashMap<>();
        if (graph != null) graph.forEach((key, value) -> safeGraph.put(key, value == null ? List.of() : value));
        model.addAttribute("skillGraph", safeGraph);
        // Prepare display rows in Java so the template does not need to inspect Map.Entry
        // objects or join nested collections during rendering.
        List<Map<String, String>> skillGraphRows = new ArrayList<>();
        safeGraph.forEach((name, prerequisites) -> {
            if (name == null || name.isBlank()) return;
            Map<String, String> row = new LinkedHashMap<>();
            row.put("name", name);
            row.put("prerequisites", prerequisites == null ? "" : String.join(", ", prerequisites));
            skillGraphRows.add(row);
        });
        model.addAttribute("skillGraphRows", skillGraphRows);
        // Return cached live market evidence immediately. Refresh happens in the background
        // so a slow government/public website cannot block the Development GPS page.
        com.nexora.career.market.LiveCareerMarketService.Snapshot marketSnapshot = safeValue(liveMarket::getQuick, null);
        model.addAttribute("liveMarket", com.nexora.career.market.LiveCareerMarketView.from(marketSnapshot));
        // Read the student's marks and fallback profile values in one round-trip on modern
        // schemas. If an older database lacks one of the optional tables, retain the safe fallback path.
        AcademicMarks storedMarks = readAcademicMarks(reg);
        BigDecimal tenth = storedMarks.tenth();
        BigDecimal twelfth = storedMarks.twelfth();
        GradeCalculatorService.CalculatorSummary gradeSummary = safeValue(() -> gradeCalculator.summary(reg), null);
        // Display the CGPA calculated from saved grades even while the student is still
        // entering semesters; do not hide a valid partial cumulative result behind a dash.
        BigDecimal cgpa = gradeSummary != null && gradeSummary.totalCredits() > 0 ? BigDecimal.valueOf(gradeSummary.cgpa()) : null;
        if (cgpa == null) cgpa = storedMarks.profileCgpa();
        model.addAttribute("tenthValue", tenth == null ? "—" : tenth.stripTrailingZeros().toPlainString() + "%");
        model.addAttribute("twelfthValue", twelfth == null ? "—" : twelfth.stripTrailingZeros().toPlainString() + "%");
        model.addAttribute("cgpaValue", cgpa == null ? "—" : cgpa.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
        // Resolve each official criterion once and reuse it for both the status and readiness table.
        Double tenthThreshold = verifiedAcademicThreshold(gpsSelection, student, "tenth");
        Double twelfthThreshold = verifiedAcademicThreshold(gpsSelection, student, "twelfth");
        Double cgpaThreshold = verifiedAcademicThreshold(gpsSelection, student, "cgpa");
        String tenthEligibility = eligibility("tenth", tenth, tenthThreshold);
        String twelfthEligibility = eligibility("twelfth", twelfth, twelfthThreshold);
        String cgpaEligibility = eligibility("cgpa", cgpa, cgpaThreshold);
        model.addAttribute("tenthEligibility", tenthEligibility);
        model.addAttribute("twelfthEligibility", twelfthEligibility);
        model.addAttribute("cgpaEligibility", cgpaEligibility);
        model.addAttribute("hasTenthEligibility", tenthEligibility != null && !tenthEligibility.isBlank());
        model.addAttribute("hasTwelfthEligibility", twelfthEligibility != null && !twelfthEligibility.isBlank());
        model.addAttribute("hasCgpaEligibility", cgpaEligibility != null && !cgpaEligibility.isBlank());
        model.addAttribute("academicReadinessRows", academicReadinessRows(student, tenth, twelfth, cgpa, tenthThreshold, twelfthThreshold, cgpaThreshold));
        List<Map<String,Object>> fetchedMilestones = safeValue(() -> milestones(reg, effectiveTargetRole), new ArrayList<>());
        List<Map<String,Object>> milestones = fetchedMilestones == null ? new ArrayList<>() : fetchedMilestones;
        model.addAttribute("milestones", milestones);
        boolean targetSelected = !effectiveTargetRole.isBlank();
        model.addAttribute("hasMilestones", targetSelected && !milestones.isEmpty());
        // Milestone values are stored as Integer objects by addMilestone(). Casting an Object
        // directly to the primitive double forces a Double cast at runtime and can throw
        // ClassCastException (Integer cannot be cast to Double), which used to break this page.
        int progress = targetSelected ? averageMilestoneProgress(milestones) : 0;
        model.addAttribute("developmentGpsPercentage", progress);
        model.addAttribute("targetCareerTitle", effectiveTargetRole);
        model.addAttribute("targetSelected", targetSelected);
        boolean onCampus = gpsSelection == null || gpsSelection.primaryPath() == null || !gpsSelection.primaryPath().toLowerCase(java.util.Locale.ROOT).contains("off-campus");
        int yop = student == null ? 0 : placementPlanner.resolveYearOfPassing(student.getBatchEndYear(), student.getBatchStartYear());
        String band = gpsSelection == null ? "Dream" : gpsSelection.targetBand();
        List<Map<String,String>> plannerCriteria = onCampus
                ? safeValue(() -> placementPlanner.criteria(yop, band), List.of()) : List.of();
        List<Map<String,String>> safePlannerCriteria = plannerCriteria == null ? List.of() : plannerCriteria;
        model.addAttribute("placementPlannerCriteria", safePlannerCriteria);
        model.addAttribute("hasPlacementPlannerCriteria", !safePlannerCriteria.isEmpty());
        // Off-campus planner: prefer verified employer/programme requirements when the
        // student has selected a company; otherwise provide transparent role-specific
        // preparation checkpoints rather than fabricating employer eligibility rules.
        List<Map<String,String>> offCampusPlannerCriteria = new ArrayList<>();
        if (!onCampus) {
            if (gpsSelection != null && gpsSelection.targetCompany() != null && !gpsSelection.targetCompany().isBlank()
                    && gpsSelection.targetRole() != null && !gpsSelection.targetRole().isBlank()) {
                try {
                    List<Map<String,Object>> verifiedRows = jdbc.queryForList(
                            "SELECT metric,target_value,target_text,source_title FROM company_role_criteria WHERE company_name=? AND role_title=? AND hiring_program=? AND yop=? AND verified_flag=TRUE ORDER BY mandatory_flag DESC,metric",
                            gpsSelection.targetCompany(), gpsSelection.targetRole(),
                            gpsSelection.targetProgram() == null ? "Not specified" : gpsSelection.targetProgram(), yop);
                    for (Map<String,Object> sourceRow : verifiedRows) {
                        Map<String,String> row = new LinkedHashMap<>();
                        row.put("metric", String.valueOf(sourceRow.getOrDefault("metric", "Requirement")));
                        Object targetValue = sourceRow.get("target_value");
                        Object targetText = sourceRow.get("target_text");
                        row.put("target", targetText != null && !targetText.toString().isBlank() ? targetText.toString()
                                : targetValue == null ? "See source" : targetValue.toString());
                        row.put("source", "Verified: " + String.valueOf(sourceRow.getOrDefault("source_title", "employer source")));
                        offCampusPlannerCriteria.add(row);
                    }
                } catch (Exception ignored) { /* use role preparation plan below */ }
            }
            if (offCampusPlannerCriteria.isEmpty()) {
                com.nexora.career.market.CareerDirectoryService.CareerRole selectedRole = safeValue(
                        () -> directory.role(targetRole(gpsSelection)), null);
                List<String> roleSkills = selectedRole == null || selectedRole.skills() == null ? List.of() : selectedRole.skills();
                int index = 0;
                for (String skill : roleSkills.stream().filter(java.util.Objects::nonNull).filter(v -> !v.isBlank()).distinct().limit(5).toList()) {
                    Map<String,String> row = new LinkedHashMap<>();
                    row.put("metric", "Role skill"); row.put("target", skill);
                    row.put("source", "Career preparation guide · not a verified company requirement");
                    offCampusPlannerCriteria.add(row); index++;
                }
                addPlannerRow(offCampusPlannerCriteria, "Portfolio projects", "2 role-aligned projects", "NEXORA preparation benchmark");
                addPlannerRow(offCampusPlannerCriteria, "Problem solving", "Weekly coding practice", "NEXORA preparation benchmark");
                addPlannerRow(offCampusPlannerCriteria, "Interview preparation", "Technical + behavioural mock rounds", "NEXORA preparation benchmark");
                addPlannerRow(offCampusPlannerCriteria, "Application materials", "Role-specific resume + public portfolio", "NEXORA preparation benchmark");
            }
        }
        model.addAttribute("offCampusPlannerCriteria", offCampusPlannerCriteria);
        model.addAttribute("hasOffCampusCriteria", !offCampusPlannerCriteria.isEmpty());
        model.addAttribute("selectedCompany", gpsSelection == null ? "" : (gpsSelection.targetCompany() == null ? "" : gpsSelection.targetCompany()));
        String plannerNote = safeValue(placementPlanner::note, "");
        model.addAttribute("placementPlannerNote", plannerNote == null ? "" : plannerNote.trim());
        model.addAttribute("placementPlannerYop", yop);
        model.addAttribute("placementPlannerBand", band == null || band.isBlank() ? "Dream" : band);
        model.addAttribute("onCampusPath", onCampus);
        model.addAttribute("marketInsightFetchedAt", marketSnapshot == null || marketSnapshot.lastFetchedAt() == null ? "" : marketSnapshot.lastFetchedAt());
        model.addAttribute("hasSkillGraph", !skillGraphRows.isEmpty());
        pageCache.put(reg, model.asMap());
        return "development";
    }

    @PostMapping("/career")
    public String career(Authentication auth) {
        if (registrationNo(auth) == null) return "redirect:/login?error=account";
        return "redirect:/career-selection";
    }

    @PostMapping("/skills")
    public String skill(Authentication auth) {
        if (registrationNo(auth) == null) return "redirect:/login?error=account";
        return "redirect:/development-gps?error=skills-from-evidence";
    }

    @PostMapping("/projects")
    public String project(Authentication auth, @RequestParam String title, @RequestParam(required=false) String description,
                          @RequestParam(required=false) String technologies, @RequestParam(required=false) String role,
                          @RequestParam String status) {
        String reg = registrationNo(auth); if (reg == null) return "redirect:/login?error=account";
        service.addProject(reg, title, description, technologies, role, status); pageCache.evict(reg); return "redirect:/development-gps";
    }

    @PostMapping("/experience")
    public String experience(Authentication auth, @RequestParam String type, @RequestParam String title,
                             @RequestParam(required=false) String organization, @RequestParam(required=false) String description,
                             @RequestParam(required=false) String startDate, @RequestParam(required=false) String endDate) {
        String reg = registrationNo(auth); if (reg == null) return "redirect:/login?error=account";
        service.addExperience(reg, type, title, organization, description, startDate, endDate); pageCache.evict(reg); return "redirect:/development-gps";
    }
    private String targetRole(CareerGpsSelection selection) { return selection == null || selection.targetRole() == null ? "" : selection.targetRole(); }

    private AcademicMarks readAcademicMarks(String reg) {
        try {
            Map<String, Object> row = jdbc.queryForMap("""
                SELECT
                  (SELECT tenth_percentage FROM student_academic_marks WHERE registration_no=? LIMIT 1) AS tenth,
                  (SELECT tenth_percentile FROM placement_profiles WHERE registration_no=? LIMIT 1) AS tenth_fallback,
                  (SELECT twelfth_percentage FROM student_academic_marks WHERE registration_no=? LIMIT 1) AS twelfth,
                  (SELECT twelfth_percentile FROM placement_profiles WHERE registration_no=? LIMIT 1) AS twelfth_fallback,
                  (SELECT cgpa FROM placement_profiles WHERE registration_no=? LIMIT 1) AS profile_cgpa
                """, reg, reg, reg, reg, reg);
            BigDecimal tenth = asDecimal(row.get("tenth"));
            if (tenth == null) tenth = asDecimal(row.get("tenth_fallback"));
            BigDecimal twelfth = asDecimal(row.get("twelfth"));
            if (twelfth == null) twelfth = asDecimal(row.get("twelfth_fallback"));
            return new AcademicMarks(tenth, twelfth, asDecimal(row.get("profile_cgpa")));
        } catch (Exception ignored) {
            BigDecimal tenth = decimal("SELECT tenth_percentage FROM student_academic_marks WHERE registration_no=?", reg);
            if (tenth == null) tenth = decimal("SELECT tenth_percentile FROM placement_profiles WHERE registration_no=?", reg);
            BigDecimal twelfth = decimal("SELECT twelfth_percentage FROM student_academic_marks WHERE registration_no=?", reg);
            if (twelfth == null) twelfth = decimal("SELECT twelfth_percentile FROM placement_profiles WHERE registration_no=?", reg);
            BigDecimal cgpa = decimal("SELECT cgpa FROM placement_profiles WHERE registration_no=?", reg);
            return new AcademicMarks(tenth, twelfth, cgpa);
        }
    }

    private BigDecimal asDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue());
        try { return new BigDecimal(value.toString().trim()); } catch (RuntimeException ignored) { return null; }
    }

    private Map<String, Integer> readMilestoneCounts(String reg) {
        try {
            Map<String, Object> row = jdbc.queryForMap("""
                SELECT
                  (SELECT COUNT(*) FROM student_projects WHERE registration_no=? AND LOWER(status)='completed') AS projects,
                  (SELECT COUNT(DISTINCT LOWER(TRIM(title))) FROM (
                    SELECT title FROM student_experiences WHERE registration_no=? AND LOWER(experience_type)='internship'
                    UNION ALL SELECT title FROM achievement_records WHERE registration_no=? AND LOWER(achievement_type)='internship'
                  ) AS internship_records) AS internships,
                  (SELECT COUNT(DISTINCT LOWER(TRIM(title))) FROM (
                    SELECT title FROM student_experiences WHERE registration_no=? AND LOWER(experience_type)='hackathon'
                    UNION ALL SELECT title FROM achievement_records WHERE registration_no=? AND LOWER(achievement_type)='hackathon'
                  ) AS hackathon_records) AS hackathons,
                  (SELECT COUNT(DISTINCT LOWER(TRIM(title))) FROM (
                    SELECT title FROM achievement_records WHERE registration_no=? AND LOWER(achievement_type) IN ('certification','course completion')
                    UNION ALL SELECT title FROM student_experiences WHERE registration_no=? AND LOWER(experience_type)='certification'
                  ) AS certification_records) AS certifications,
                  (SELECT COUNT(*) FROM student_problem_logs WHERE registration_no=?) AS problems,
                  (SELECT COUNT(*) FROM student_learning_resources WHERE registration_no=? AND status='COMPLETED') AS courses,
                  (SELECT COUNT(DISTINCT reference_url) FROM student_evidence WHERE registration_no=? AND category='PROJECT' AND metric='github_repository' AND verification_status='API_VERIFIED') AS github
                """, reg, reg, reg, reg, reg, reg, reg, reg, reg, reg);
            Map<String, Integer> result = new LinkedHashMap<>();
            for (String key : List.of("projects", "internships", "hackathons", "certifications", "problems", "courses", "github")) {
                Object value = row.get(key);
                result.put(key, value instanceof Number number ? number.intValue() : 0);
            }
            return result;
        } catch (Exception ignored) {
            // An older local schema may not yet contain one optional evidence table. In that
            // case, let milestones() use its existing individually guarded count queries.
            return null;
        }
    }

    private record AcademicMarks(BigDecimal tenth, BigDecimal twelfth, BigDecimal profileCgpa) { }

    private BigDecimal decimal(String sql, String reg) {
        try { return jdbc.queryForObject(sql, BigDecimal.class, reg); } catch (Exception ignored) { return null; }
    }

    private String eligibility(String wanted, BigDecimal value, Double verifiedThreshold) {
        String label = switch (wanted) { case "tenth" -> "Class 10 marks"; case "twelfth" -> "Class 12 marks"; default -> "CGPA"; };
        if (value == null) return "Not entered yet — add your official " + label + " to assess this benchmark.";
        Double threshold = verifiedThreshold;
        boolean verified = threshold != null;
        if (threshold == null) threshold = wanted.equals("cgpa") ? 6.0 : 60.0;
        BigDecimal required = BigDecimal.valueOf(threshold);
        boolean meets = value.compareTo(required) >= 0;
        String result = meets ? "Meets benchmark" : "Below benchmark";
        return result + (verified ? " · verified selected-policy criterion" : " · NEXORA planning estimate; verify the current notice");
    }

    private Double verifiedAcademicThreshold(CareerGpsSelection selection, Student student, String wanted) {
        if (selection == null || student == null) return null;
        String metricPattern = switch (wanted) {
            case "tenth" -> "class_10_marks";
            case "twelfth" -> "class_12_marks";
            default -> "cgpa";
        };
        try {
            List<Double> values;
            if (selection.primaryPath() != null && selection.primaryPath().toLowerCase(java.util.Locale.ROOT).contains("off-campus")) {
                if (selection.targetCompany() == null || selection.targetCompany().isBlank() || selection.targetRole() == null || selection.targetRole().isBlank()) return null;
                values = jdbc.query("SELECT target_value FROM company_role_criteria WHERE LOWER(TRIM(company_name))=LOWER(TRIM(?)) AND LOWER(TRIM(role_title))=LOWER(TRIM(?)) AND hiring_program=? AND yop=? AND verified_flag=TRUE AND metric=? AND target_value IS NOT NULL AND source_title IS NOT NULL AND source_title<>'' AND source_url IS NOT NULL AND source_url<>'' AND observed_at IS NOT NULL ORDER BY observed_at DESC LIMIT 1",
                        (rs, row) -> rs.getDouble(1), selection.targetCompany(), selection.targetRole(),
                        selection.targetProgram() == null ? "Not specified" : selection.targetProgram(), placementPlanner.resolveYearOfPassing(student.getBatchEndYear(), student.getBatchStartYear()), metricPattern);
            } else {
                String metric = wanted.equals("tenth") ? "tenth_percentile" : wanted.equals("twelfth") ? "twelfth_percentile" : "CGPA";
                values = jdbc.query("SELECT target_value FROM career_criteria WHERE yop=? AND source_version=? AND band=? AND verified_flag=TRUE AND metric=? AND target_value IS NOT NULL AND source_title IS NOT NULL AND source_title<>'' AND source_url IS NOT NULL AND source_url<>'' AND observed_at IS NOT NULL ORDER BY observed_at DESC LIMIT 1",
                        (rs, row) -> rs.getDouble(1), placementPlanner.resolveYearOfPassing(student.getBatchEndYear(), student.getBatchStartYear()), selection.sourceVersion(), selection.targetBand(), metric);
            }
            return values.isEmpty() ? null : values.get(0);
        } catch (Exception ignored) { return null; }
    }

    private List<Map<String,String>> academicReadinessRows(Student student, BigDecimal tenth, BigDecimal twelfth, BigDecimal cgpa,
                                                            Double tenthThreshold, Double twelfthThreshold, Double cgpaThreshold) {
        List<Map<String,String>> rows = new ArrayList<>();
        addAcademicReadinessRow(rows, "Class 10 marks", tenth, "%", tenthThreshold, 60.0,
                "Official school record", "Class 10 marks (%)");
        addAcademicReadinessRow(rows, "Class 12 marks", twelfth, "%", twelfthThreshold, 60.0,
                "Official school record", "Class 12 marks (%)");
        addAcademicReadinessRow(rows, "Current CGPA", cgpa, "/10", cgpaThreshold, 6.0,
                "Calculated from saved semester grades or previously saved placement profile", "CGPA");
        rows.add(readinessRow("Active backlogs", "Check current count", "Confirm the current SRMIST notice or exact employer programme; NEXORA baseline assumes no active backlogs.", "NEXORA planning estimate"));
        rows.add(readinessRow("Degree / branch", "B.Tech CSE · verify role eligibility", "Compare the role's eligible degree branches with the current official notice.", "Role-aligned planning guide"));
        rows.add(readinessRow("Graduation year", student == null ? "Not recorded" : String.valueOf(placementPlanner.resolveYearOfPassing(student.getBatchEndYear(), student.getBatchStartYear())), "Match the selected hiring programme's batch window.", "Student academic profile"));
        return rows;
    }

    private void addAcademicReadinessRow(List<Map<String,String>> rows, String metric, BigDecimal actual, String suffix,
                                        Double verifiedThreshold, double fallbackThreshold, String actualSource, String metricKey) {
        double threshold = verifiedThreshold == null ? fallbackThreshold : verifiedThreshold;
        String actualText = actual == null ? "Not entered yet" : actual.stripTrailingZeros().toPlainString() + suffix;
        String thresholdText = (threshold == Math.rint(threshold) ? String.valueOf((int)threshold) : String.valueOf(threshold)) + suffix;
        String status = actual == null ? "Not assessed — add official marks" : actual.compareTo(BigDecimal.valueOf(threshold)) >= 0 ? "Meets benchmark" : "Below benchmark";
        String source = verifiedThreshold == null ? "NEXORA planning estimate; verify current employer / SRMIST notice" : "Verified selected-policy criterion";
        Map<String,String> row = readinessRow(metric, actualText, "Benchmark: " + thresholdText + " · " + status + " · " + actualSource, source);
        row.put("benchmark", thresholdText);
        row.put("status", status);
        row.put("metricKey", metricKey);
        rows.add(row);
    }

    private Map<String,String> readinessRow(String metric, String actual, String detail, String source) {
        Map<String,String> row = new LinkedHashMap<>();
        row.put("metric", metric); row.put("actual", actual); row.put("detail", detail); row.put("source", source);
        return row;
    }

    private List<Map<String,Object>> milestones(String reg, String career) {
        String c = career.toLowerCase(java.util.Locale.ROOT);
        int projectTarget = c.contains("security") ? 2 : (c.contains("cloud") || c.contains("devops") ? 2 : 3);
        int internshipTarget = 1;
        int hackathonTarget = c.contains("data") || c.contains("ai") || c.contains("machine learning") ? 3 : 5;
        int certificationTarget = c.contains("security") || c.contains("cloud") ? 3 : 2;
        int problemTarget = c.contains("data") || c.contains("ai") ? 30 : (c.contains("security") || c.contains("cloud") ? 20 : 50);
        Map<String, Integer> counts = readMilestoneCounts(reg);
        int projects = counts == null ? count("SELECT COUNT(*) FROM student_projects WHERE registration_no=? AND LOWER(status)='completed'", reg) : counts.getOrDefault("projects", 0);
        int internships = counts == null ? countCombined("SELECT title FROM student_experiences WHERE registration_no=? AND LOWER(experience_type)='internship' UNION ALL SELECT title FROM achievement_records WHERE registration_no=? AND LOWER(achievement_type)='internship'", reg) : counts.getOrDefault("internships", 0);
        int hackathons = counts == null ? countCombined("SELECT title FROM student_experiences WHERE registration_no=? AND LOWER(experience_type)='hackathon' UNION ALL SELECT title FROM achievement_records WHERE registration_no=? AND LOWER(achievement_type)='hackathon'", reg) : counts.getOrDefault("hackathons", 0);
        int certifications = counts == null ? countCombined("SELECT title FROM achievement_records WHERE registration_no=? AND LOWER(achievement_type) IN ('certification','course completion') UNION ALL SELECT title FROM student_experiences WHERE registration_no=? AND LOWER(experience_type)='certification'", reg) : counts.getOrDefault("certifications", 0);
        int problems = counts == null ? count("SELECT COUNT(*) FROM student_problem_logs WHERE registration_no=?", reg) : counts.getOrDefault("problems", 0);
        int completedCourses = counts == null ? count("SELECT COUNT(*) FROM student_learning_resources WHERE registration_no=? AND status='COMPLETED'", reg) : counts.getOrDefault("courses", 0);
        int githubRepositories = counts == null ? count("SELECT COUNT(DISTINCT reference_url) FROM student_evidence WHERE registration_no=? AND category='PROJECT' AND metric='github_repository' AND verification_status='API_VERIFIED'", reg) : counts.getOrDefault("github", 0);
        int githubTarget = c.contains("marquee") ? 5 : (c.contains("super dream") ? 4 : (c.contains("standard") ? 1 : 3));
        List<Map<String,Object>> result = new ArrayList<>();
        addMilestone(result,"Completed projects",projects,projectTarget,"student_projects");
        addMilestone(result,"Internships recorded",internships,internshipTarget,"student_experiences");
        addMilestone(result,"Hackathons",hackathons,hackathonTarget,"achievement_records");
        addMilestone(result,"Certifications / course completions",certifications,certificationTarget,"achievement_records");
        addMilestone(result,"Problem-solving records (connected platforms)",problems,problemTarget,"student_problem_logs");
        addMilestone(result,"Public GitHub repositories (API synced)",githubRepositories,githubTarget,"student_evidence");
        addMilestone(result,"Role-aligned courses marked complete (self-reported)",completedCourses,3,"student_learning_resources");
        return result;
    }

    /** Computes a stable average from milestones whose percentage values may be any Number type. */
    static int averageMilestoneProgress(List<Map<String, Object>> milestones) {
        if (milestones == null || milestones.isEmpty()) return 0;
        double average = milestones.stream()
                .mapToDouble(m -> {
                    Object rawPercent = m == null ? null : m.get("percent");
                    return rawPercent instanceof Number number ? number.doubleValue() : 0.0d;
                })
                .average()
                .orElse(0.0d);
        return (int) Math.round(Math.max(0.0d, Math.min(100.0d, average)));
    }

    private void addMilestone(List<Map<String,Object>> list, String label, int done, int target, String source) {
        Map<String,Object> row = new LinkedHashMap<>(); row.put("label",label); row.put("done",done); row.put("target",target);
        row.put("percent",target <= 0 ? 0 : Math.min(100,(int)Math.round(done*100.0/target))); row.put("source",source); list.add(row);
    }

    private void addPlannerRow(List<Map<String,String>> rows, String metric, String target, String source) {
        Map<String,String> row = new LinkedHashMap<>();
        row.put("metric", metric); row.put("target", target); row.put("source", source); rows.add(row);
    }

    private int countCombined(String sql, String reg) {
        try { Integer n = jdbc.queryForObject("SELECT COUNT(DISTINCT LOWER(TRIM(title))) FROM (" + sql + ") AS combined_records", Integer.class, reg, reg); return n == null ? 0 : n; }
        catch (Exception ignored) { return 0; }
    }

    private int count(String sql, String reg) { try { Integer n = jdbc.queryForObject(sql,Integer.class,reg); return n == null ? 0 : n; } catch (Exception ignored) { return 0; } }

    private <T> T safeValue(java.util.concurrent.Callable<T> supplier, T fallback) {
        try { return supplier.call(); } catch (Exception ignored) { return fallback; }
    }

    private <T> List<T> safeList(java.util.concurrent.Callable<List<T>> supplier) {
        List<T> value = safeValue(supplier, List.of());
        return value == null ? List.of() : value;
    }

    private String registrationNo(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null || "anonymousUser".equals(auth.getName())) return null;
        return students.findByLoginId(auth.getName()).map(s -> s.getRegistrationNo()).orElse(null);
    }
}
