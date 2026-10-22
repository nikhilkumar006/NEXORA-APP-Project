package com.nexora.controller;

import com.nexora.career.CareerGpsSelection;
import com.nexora.career.CareerGpsService;
import com.nexora.career.CareerService;
import com.nexora.career.CareerProfile;
import com.nexora.career.market.CareerDirectoryService;
import com.nexora.career.market.CareerDirectoryService.CareerRole;
import com.nexora.development.CareerTarget;
import com.nexora.development.DevelopmentService;
import com.nexora.development.Skill;
import com.nexora.model.Student;
import com.nexora.subscription.SubscriptionService;
import com.nexora.repository.StudentRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

@Controller
public class RecommendedCoursesController {
    private final StudentRepository students;
    private final CareerGpsService gps;
    private final CareerService careers;
    private final CareerDirectoryService directory;
    private final DevelopmentService development;
    private final JdbcTemplate jdbc;
    private final SubscriptionService subscriptions;
    private final DevelopmentGpsPageCache developmentPageCache;

    public RecommendedCoursesController(StudentRepository students, CareerGpsService gps, CareerService careers,
                                        CareerDirectoryService directory, DevelopmentService development,
                                        JdbcTemplate jdbc, SubscriptionService subscriptions,
                                        DevelopmentGpsPageCache developmentPageCache) {
        this.students = students;
        this.gps = gps;
        this.careers = careers;
        this.directory = directory;
        this.development = development;
        this.jdbc = jdbc;
        this.subscriptions = subscriptions;
        this.developmentPageCache = developmentPageCache;
    }

    @GetMapping("/recommended-courses")
    public String page(Authentication authentication, @RequestParam(required = false) String q, Model model) {
        Student student = resolveStudent(authentication);
        if (student == null) return "redirect:/login?error=account";
        String reg = student.getRegistrationNo();
        try { jdbc.update("INSERT IGNORE INTO student_settings(registration_no) VALUES(?)", reg); } catch (org.springframework.dao.DataAccessException ignored) { }
        CareerGpsSelection selection = null;
        CareerProfile careerProfile = null;
        CareerTarget developmentTarget = null;
        try { selection = gps.find(reg).orElse(null); } catch (org.springframework.dao.DataAccessException ignored) { }
        try { careerProfile = careers.profile(reg).orElse(null); } catch (org.springframework.dao.DataAccessException ignored) { }
        try { developmentTarget = development.getCareerTarget(reg); } catch (org.springframework.dao.DataAccessException ignored) { }
        CareerRole role = selection == null ? null : directory.role(selection.targetRole());
        if (role == null && careerProfile != null) role = directory.role(careerProfile.targetRole());
        if (role == null && developmentTarget != null) role = directory.role(developmentTarget.targetRole());
        List<String> required = role == null || role.skills() == null ? List.of() : role.skills();
        List<String> skillsToBuild = skillsToBuild(reg, role);
        List<String> focusSkills = skillsToBuild.isEmpty() ? required : skillsToBuild;
        List<ResourceCard> candidates = role == null ? List.of() : catalog(role, focusSkills);
        List<ResourceCard> focused = new ArrayList<>();
        if (role != null) {
            for (ResourceCard resource : candidates) {
                if (resource.type().startsWith("PAID CATALOGUE") || focusSkills.stream().anyMatch(skill -> skillMatches(skill, resource.skill()))) focused.add(resource);
            }
            if (focused.stream().noneMatch(resource -> !resource.type().startsWith("PAID CATALOGUE"))) {
                java.util.Set<String> fallbackKeys = fallbackResourceKeys(role);
                for (ResourceCard resource : candidates) if (fallbackKeys.contains(resource.key()) && !focused.contains(resource)) focused.add(resource);
            }
        }
        // Keep the role-matched catalogue separate from the search result. Progress must be
        // computed from the same complete catalogue regardless of the current search term.
        List<ResourceCard> roleMatchedResources = List.copyOf(focused);
        String query = q == null ? "" : q.trim();
        if (query.length() > 100) query = query.substring(0, 100);
        List<ResourceCard> visibleResources = roleMatchedResources;
        if (!query.isBlank()) {
            String needle = query.toLowerCase(Locale.ROOT);
            visibleResources = roleMatchedResources.stream().filter(resource -> (resource.title() + " " + resource.provider() + " " + resource.skill() + " " + resource.description())
                    .toLowerCase(Locale.ROOT).contains(needle)).toList();
        }
        List<ResourceProgress> progress;
        try {
            progress = jdbc.query("SELECT resource_key,status FROM student_learning_resources WHERE registration_no=?",
                    (rs, row) -> new ResourceProgress(rs.getString("resource_key"), rs.getString("status")), reg);
        } catch (org.springframework.dao.DataAccessException ignored) { progress = List.of(); }
        model.addAttribute("student", student);
        model.addAttribute("studentInitial", student.getFullName() == null || student.getFullName().isBlank()
                ? "N" : student.getFullName().trim().substring(0, 1).toUpperCase(Locale.ROOT));
        model.addAttribute("role", role);
        model.addAttribute("targetRoleName", role == null ? "No target role selected" : role.title());
        model.addAttribute("skillsToBuild", skillsToBuild);
        model.addAttribute("resourcesAvailableForTarget", role != null && !visibleResources.isEmpty());
        model.addAttribute("query", query);
        model.addAttribute("resources", visibleResources);
        Map<String, String> progressByKey = new HashMap<>();
        progress.forEach(item -> progressByKey.put(item.key(), item.status()));
        model.addAttribute("progressByKey", progressByKey);
        List<ResourceCard> trackedResources = roleMatchedResources.stream().filter(resource -> !resource.type().startsWith("PAID CATALOGUE")).toList();
        long completedTracked = trackedResources.stream().filter(resource -> "COMPLETED".equalsIgnoreCase(progressByKey.get(resource.key()))).count();
        int courseProgressPercent = trackedResources.isEmpty() ? 0 : (int) Math.round(completedTracked * 100.0 / trackedResources.size());
        model.addAttribute("courseProgressPercent", courseProgressPercent);
        model.addAttribute("courseCompletedCount", completedTracked);
        model.addAttribute("courseTotalCount", trackedResources.size());
        // Completed resources remain in the library so students can reopen them or change status.
        // The query only controls which cards are displayed, never the progress denominator.
        return "recommended-courses";
    }

    @PostMapping("/recommended-courses/progress")
    public String updateProgress(Authentication authentication, @RequestParam String resourceKey,
                                 @RequestParam String status, @RequestParam(defaultValue = "") String q) {
        Student student = resolveStudent(authentication);
        if (student == null) return "redirect:/login?error=account";
        CareerGpsSelection selection = null;
        CareerProfile careerProfile = null;
        CareerTarget developmentTarget = null;
        try { selection = gps.find(student.getRegistrationNo()).orElse(null); } catch (org.springframework.dao.DataAccessException ignored) { }
        try { careerProfile = careers.profile(student.getRegistrationNo()).orElse(null); } catch (org.springframework.dao.DataAccessException ignored) { }
        try { developmentTarget = development.getCareerTarget(student.getRegistrationNo()); } catch (org.springframework.dao.DataAccessException ignored) { }
        CareerRole role = selection == null ? null : directory.role(selection.targetRole());
        if (role == null && careerProfile != null) role = directory.role(careerProfile.targetRole());
        if (role == null && developmentTarget != null) role = directory.role(developmentTarget.targetRole());
        List<String> required = role == null || role.skills() == null ? List.of() : role.skills();
        List<String> skillsToBuild = skillsToBuild(student.getRegistrationNo(), role);
        List<String> focusSkills = skillsToBuild.isEmpty() ? required : skillsToBuild;
        ResourceCard resource = catalog(role, focusSkills).stream().filter(item -> item.key().equals(resourceKey)).findFirst().orElse(null);
        if (resource == null || resource.type().startsWith("PAID CATALOGUE")) return redirectCourses(q, null);
        if (!isResourceRelevantToRole(role, resource, focusSkills)) return redirectCourses(q, "role-resource");
        String safeStatus = List.of("SAVED", "IN_PROGRESS", "COMPLETED").contains(status) ? status : "SAVED";
        jdbc.update("INSERT INTO student_learning_resources(registration_no,resource_key,resource_title,resource_url,status) VALUES(?,?,?,?,?) " +
                        "ON DUPLICATE KEY UPDATE resource_title=VALUES(resource_title),resource_url=VALUES(resource_url),status=VALUES(status),updated_at=CURRENT_TIMESTAMP",
                student.getRegistrationNo(), resource.key(), resource.title(), resource.url(), safeStatus);
        developmentPageCache.evict(student.getRegistrationNo());
        return redirectCourses(q, null);
    }

    private String redirectCourses(String q, String error) {
        StringBuilder target = new StringBuilder("redirect:/recommended-courses");
        List<String> params = new ArrayList<>();
        if (error != null && !error.isBlank()) params.add("error=" + java.net.URLEncoder.encode(error, java.nio.charset.StandardCharsets.UTF_8));
        if (q != null && !q.isBlank()) params.add("q=" + java.net.URLEncoder.encode(q.trim(), java.nio.charset.StandardCharsets.UTF_8));
        if (!params.isEmpty()) target.append('?').append(String.join("&", params));
        return target.toString();
    }

    private Student resolveStudent(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getName() != null && !"anonymousUser".equals(authentication.getName())) {
            Student found = students.findByLoginId(authentication.getName()).orElse(null);
            if (found != null) return found;
        }
        return null;
    }

    private boolean skillMatches(String required, String resourceSkill) {
        if (required == null || resourceSkill == null) return false;
        String a = required.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
        String b = resourceSkill.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
        if (a.isBlank() || b.isBlank()) return false;
        if (a.equals(b)) return true;
        java.util.Set<String> requiredTokens = new java.util.HashSet<>(java.util.Arrays.asList(a.split("\\s+")));
        java.util.Set<String> resourceTokens = new java.util.HashSet<>(java.util.Arrays.asList(b.split("\\s+")));
        if (requiredTokens.contains("api")) requiredTokens.add("apis");
        if (resourceTokens.contains("apis")) resourceTokens.add("api");
        return requiredTokens.stream().filter(token -> token.length() >= 3).anyMatch(resourceTokens::contains);
    }

    private boolean isResourceRelevantToRole(CareerRole role, ResourceCard resource, List<String> focusSkills) {
        if (role == null || resource == null) return false;
        if (resource.type().startsWith("PAID CATALOGUE")) return false;
        if (focusSkills != null && focusSkills.stream().anyMatch(skill -> skillMatches(skill, resource.skill()))) return true;
        return fallbackResourceKeys(role).contains(resource.key());
    }

    private List<String> skillsToBuild(String registrationNo, CareerRole role) {
        if (role == null || role.skills() == null || role.skills().isEmpty()) return List.of();
        List<Skill> recorded;
        try { recorded = development.getSkills(registrationNo); } catch (Exception ignored) { recorded = List.of(); }
        if (recorded == null) recorded = List.of();
        List<String> completed = recorded.stream().filter(skill -> skill.status() != null &&
                (skill.status().equalsIgnoreCase("COMPLETED") || skill.status().equalsIgnoreCase("VERIFIED")))
                .map(Skill::name).filter(java.util.Objects::nonNull).toList();
        return role.skills().stream().filter(required -> completed.stream().noneMatch(done -> skillMatches(required, done))).toList();
    }

    private java.util.Set<String> fallbackResourceKeys(CareerRole role) {
        if (role == null || role.title() == null) return java.util.Set.of("freecodecamp", "roadmap-sh", "github-skills", "dsa-practice");
        String title = role.title().toLowerCase(Locale.ROOT);
        if (title.contains("data") || title.contains("machine") || title.contains("ai")) return java.util.Set.of("python-docs", "sqlbolt", "pytorch-learn", "tensorflow-tutorials", "postgres-tutorial", "github-skills");
        if (title.contains("security")) return java.util.Set.of("owasp-top10", "linux-journey", "networking-cisco", "github-skills");
        if (title.contains("cloud") || title.contains("devops")) return java.util.Set.of("aws-skill-builder", "kubernetes-learn", "docker-get-started", "linux-journey", "github-skills");
        if (title.contains("web") || title.contains("frontend") || title.contains("ui")) return java.util.Set.of("mdn-web", "html-css-mdn", "react-learn", "github-skills");
        if (title.contains("java") || title.contains("backend") || title.contains("software")) return java.util.Set.of("java-learn", "spring-guides", "sqlbolt", "dsa-practice", "github-skills");
        if (title.contains("network")) return java.util.Set.of("networking-cisco", "linux-journey", "github-skills");
        return java.util.Set.of("freecodecamp", "roadmap-sh", "github-skills", "dsa-practice");
    }

    private List<ResourceCard> catalog(CareerRole role) {
        return catalog(role, role == null || role.skills() == null ? List.of() : role.skills());
    }

    private List<ResourceCard> catalog(CareerRole role, List<String> focusSkills) {
        List<ResourceCard> resources = new ArrayList<>(List.of(
                new ResourceCard("python-docs", "Python Tutorial", "Python Software Foundation", "Python", "Official language guide with examples and core concepts.", "https://docs.python.org/3/tutorial/", "FREE · Official documentation"),
                new ResourceCard("mdn-web", "Web Development Learning Area", "MDN", "JavaScript", "Structured lessons for HTML, CSS, JavaScript and browser fundamentals.", "https://developer.mozilla.org/en-US/docs/Learn_web_development", "FREE · Learning path"),
                new ResourceCard("github-skills", "GitHub Skills", "GitHub", "Git", "Hands-on interactive courses for GitHub workflows and collaboration.", "https://skills.github.com/", "FREE · Interactive course"),
                new ResourceCard("sqlbolt", "Interactive SQL Lessons", "SQLBolt", "SQL", "Practice SQL queries step by step in the browser.", "https://sqlbolt.com/", "FREE · Practice"),
                new ResourceCard("aws-skill-builder", "AWS Skill Builder", "Amazon Web Services", "Cloud", "Official cloud learning plans, digital training and foundational content; some advanced learning is paid.", "https://skillbuilder.aws/", "FREE + PAID · Official catalogue"),
                new ResourceCard("microsoft-learn", "Microsoft Learn", "Microsoft", "Azure", "Official technical modules for Azure, development and data.", "https://learn.microsoft.com/training/", "FREE · Learning path"),
                new ResourceCard("freecodecamp", "freeCodeCamp Curriculum", "freeCodeCamp", "Projects", "Free coding curriculum built around practice and portfolio projects.", "https://www.freecodecamp.org/learn/", "FREE · Curriculum"),
                new ResourceCard("roadmap-sh", "Developer Roadmaps", "roadmap.sh", "System design", "Role-based roadmaps for structuring self-study by topic.", "https://roadmap.sh/", "FREE · Roadmap"),
                new ResourceCard("pytorch-learn", "PyTorch Tutorials", "PyTorch", "PyTorch", "Official tutorials for tensors, model building and machine learning workflows.", "https://pytorch.org/tutorials/", "FREE · Official tutorials"),
                new ResourceCard("owasp-top10", "OWASP Top 10", "OWASP Foundation", "Security", "A widely used awareness guide to major web application security risks.", "https://owasp.org/www-project-top-ten/", "FREE · Reference"),
                new ResourceCard("kubernetes-learn", "Kubernetes Basics", "Kubernetes", "Kubernetes", "Official guided introduction to deploying and scaling containerized apps.", "https://kubernetes.io/docs/tutorials/kubernetes-basics/", "FREE · Official tutorial"),
                new ResourceCard("react-learn", "Learn React", "React", "React", "Official React documentation and interactive examples.", "https://react.dev/learn", "FREE · Official documentation"),
                new ResourceCard("java-learn", "Learn Java", "Oracle / dev.java", "Java", "Official Java learning material covering language basics and core APIs.", "https://dev.java/learn/", "FREE · Official learning path"),
                new ResourceCard("spring-guides", "Spring Guides", "Spring", "Spring Boot", "Official step-by-step guides for building Spring applications and REST services.", "https://spring.io/guides", "FREE · Official tutorials"),
                new ResourceCard("dsa-practice", "Problem Solving Practice", "HackerRank", "DSA", "Practice data structures and algorithms with coding challenges.", "https://www.hackerrank.com/domains/data-structures", "FREE · Practice"),
                new ResourceCard("html-css-mdn", "HTML and CSS Learning", "MDN", "HTML CSS", "Structured web fundamentals with examples and practical exercises.", "https://developer.mozilla.org/en-US/docs/Learn_web_development", "FREE · Learning path"),
                new ResourceCard("docker-get-started", "Docker Get Started", "Docker", "Docker", "Official introduction to containers and Docker workflows.", "https://docs.docker.com/get-started/", "FREE · Official tutorial"),
                new ResourceCard("linux-journey", "Linux Journey", "Linux Journey", "Linux", "Free guided lessons on Linux basics, command line and system concepts.", "https://linuxjourney.com/", "FREE · Learning path"),
                new ResourceCard("tensorflow-tutorials", "TensorFlow Tutorials", "TensorFlow", "Machine Learning", "Official tutorials for machine learning models and workflows.", "https://www.tensorflow.org/tutorials", "FREE · Official tutorials"),
                new ResourceCard("postgres-tutorial", "PostgreSQL Tutorial", "PostgreSQL", "Database", "Official documentation and tutorial for relational database concepts.", "https://www.postgresql.org/docs/current/tutorial.html", "FREE · Official tutorial"),
                new ResourceCard("networking-cisco", "Networking Academy", "Cisco", "Networking", "Cisco Networking Academy learning catalogue for networking and cybersecurity foundations.", "https://www.netacad.com/", "FREE + PAID · Provider catalogue")
        ));
        if (role != null && role.skills() != null) {
            List<String> skills = role.skills().stream().filter(skill -> skill != null && !skill.isBlank())
                    .map(String::trim).distinct().limit(3).toList();
            for (String skill : skills) {
                String query = java.net.URLEncoder.encode(skill, java.nio.charset.StandardCharsets.UTF_8);
                String keySkill = skill.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
                resources.add(new ResourceCard("paid-coursera-" + keySkill, "Paid courses for " + skill, "Coursera", skill,
                        "Live provider search for this exact skill. Check current course dates, fees and enrollment status on the provider page.",
                        "https://www.coursera.org/search?query=" + query, "PAID CATALOGUE · LIVE SEARCH"));
                resources.add(new ResourceCard("paid-udemy-" + keySkill, "Practical courses for " + skill, "Udemy", skill,
                        "Live provider search for this exact skill. Prices, discounts and course availability can change; verify before enrolling.",
                        "https://www.udemy.com/courses/search/?q=" + query, "PAID CATALOGUE · LIVE SEARCH"));
            }
        }
        // Role catalogues occasionally use aliases that do not literally match a resource skill.
        // Use role-family relevance as a grounded fallback rather than showing an empty page.
        if (role != null && resources.stream().noneMatch(r -> !r.type().startsWith("PAID CATALOGUE") && focusSkills != null &&
                focusSkills.stream().anyMatch(skill -> skillMatches(skill, r.skill())))) {
            String title = role.title().toLowerCase(Locale.ROOT);
            List<String> keys = title.contains("data") || title.contains("machine") || title.contains("ai")
                    ? List.of("python-docs", "sqlbolt", "pytorch-learn", "tensorflow-tutorials", "postgres-tutorial", "github-skills")
                    : title.contains("security") ? List.of("owasp-top10", "linux-journey", "networking-cisco", "github-skills")
                    : title.contains("cloud") || title.contains("devops") ? List.of("aws-skill-builder", "kubernetes-learn", "docker-get-started", "linux-journey", "github-skills")
                    : title.contains("web") || title.contains("frontend") || title.contains("ui") ? List.of("mdn-web", "html-css-mdn", "react-learn", "github-skills")
                    : title.contains("java") || title.contains("backend") || title.contains("software") ? List.of("java-learn", "spring-guides", "sqlbolt", "dsa-practice", "github-skills")
                    : title.contains("network") ? List.of("networking-cisco", "linux-journey", "github-skills")
                    : List.of("freecodecamp", "roadmap-sh", "github-skills", "dsa-practice");
            Map<String,ResourceCard> byKey = new HashMap<>(); resources.forEach(r -> byKey.put(r.key(), r));
            for (String key : keys) { ResourceCard r = byKey.get(key); if (r != null && !resources.contains(r)) resources.add(r); }
        }
        return List.copyOf(resources);
    }

    private boolean requiredSkillMatch(CareerRole role, ResourceCard resource) {
        return role.skills() != null && role.skills().stream().anyMatch(skill -> skillMatches(skill, resource.skill()));
    }

    public record ResourceCard(String key, String title, String provider, String skill, String description, String url, String type) {}
    public record ResourceProgress(String key, String status) {}
}
