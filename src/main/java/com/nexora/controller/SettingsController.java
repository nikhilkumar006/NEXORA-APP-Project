package com.nexora.controller;

import com.nexora.model.Student;
import com.nexora.repository.StudentRepository;
import com.nexora.subscription.SubscriptionService;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Controller
public class SettingsController {
    private final StudentRepository students;
    private final JdbcTemplate jdbc;
    private final SubscriptionService subscriptions;

    public SettingsController(StudentRepository students, JdbcTemplate jdbc, SubscriptionService subscriptions) {
        this.students = students;
        this.jdbc = jdbc;
        this.subscriptions = subscriptions;
    }

    @GetMapping({"/settings", "/profile"})
    public String page(Authentication authentication, Model model,
                       @RequestParam(required = false) String saved,
                       @RequestParam(required = false) String error,
                       @RequestParam(required = false) String paymentSubmitted,
                       @RequestParam(required = false) String upgrade) {
        Student student = resolveStudent(authentication);
        if (student == null) return "redirect:/dashboard";
        String reg = student.getRegistrationNo();
        try {
            jdbc.update("INSERT IGNORE INTO student_settings(registration_no) VALUES(?)", reg);
            model.addAttribute("settings", jdbc.queryForMap("SELECT email_notifications, in_app_notifications, weekly_summary, profile_visibility FROM student_settings WHERE registration_no=?", reg));
        } catch (DataAccessException ignored) {
            model.addAttribute("settings", Map.of("email_notifications", true, "in_app_notifications", true, "weekly_summary", false, "profile_visibility", false));
        }
        model.addAttribute("student", student);
        model.addAttribute("studentInitial", initial(student.getFullName()));
        model.addAttribute("skillCount", count("SELECT COUNT(*) FROM student_skills WHERE registration_no=?", reg));
        model.addAttribute("projectCount", count("SELECT COUNT(*) FROM student_projects WHERE registration_no=?", reg));
        model.addAttribute("achievementCount", count("SELECT COUNT(*) FROM achievement_records WHERE registration_no=?", reg));
        model.addAttribute("evidenceCount", count("SELECT COUNT(*) FROM student_evidence WHERE registration_no=?", reg));
        Map<String,Object> marks;
        try {
            marks = jdbc.query("SELECT tenth_percentage,twelfth_percentage FROM student_academic_marks WHERE registration_no=?", rs -> {
                if (!rs.next()) return Map.of();
                Map<String,Object> found = new HashMap<>();
                found.put("tenth", rs.getBigDecimal("tenth_percentage"));
                found.put("twelfth", rs.getBigDecimal("twelfth_percentage"));
                return found;
            }, reg);
        } catch (DataAccessException ignored) { marks = Map.of(); }
        model.addAttribute("academicMarks", marks);
        var currentPlan = subscriptions.current(reg);
        model.addAttribute("currentPlan", currentPlan.getOrDefault("plan_name", "Free"));
        model.addAttribute("currentPlanCode", currentPlan.getOrDefault("plan_code", "FREE"));
        // Convert plan records to ordinary maps before rendering. This keeps the template
        // independent of record-component property resolution across different IDE/runtime builds.
        model.addAttribute("plans", subscriptions.plans().stream().map(plan -> {
            Map<String, Object> item = new HashMap<>();
            item.put("code", plan.code());
            item.put("name", plan.name());
            item.put("semesterPrice", plan.semesterPrice());
            item.put("annualPrice", plan.annualPrice());
            item.put("description", plan.description());
            return item;
        }).toList());
        model.addAttribute("saved", saved != null);
        model.addAttribute("error", error);
        model.addAttribute("paymentSubmitted", paymentSubmitted != null);
        model.addAttribute("upgradeRequired", "premium".equalsIgnoreCase(upgrade));
        return "settings";
    }

    @PostMapping("/settings")
    public String save(Authentication authentication,
                       @RequestParam(defaultValue = "false") boolean emailNotifications,
                       @RequestParam(defaultValue = "false") boolean inAppNotifications,
                       @RequestParam(defaultValue = "false") boolean weeklySummary,
                       @RequestParam(defaultValue = "false") boolean profileVisibility) {
        Student student = resolveStudent(authentication);
        if (student == null) return "redirect:/dashboard";
        jdbc.update("INSERT INTO student_settings(registration_no,email_notifications,in_app_notifications,weekly_summary,profile_visibility) VALUES(?,?,?,?,?) " +
                        "ON DUPLICATE KEY UPDATE email_notifications=VALUES(email_notifications), in_app_notifications=VALUES(in_app_notifications), " +
                        "weekly_summary=VALUES(weekly_summary), profile_visibility=VALUES(profile_visibility), updated_at=CURRENT_TIMESTAMP",
                student.getRegistrationNo(), emailNotifications, inAppNotifications, weeklySummary, profileVisibility);
        return "redirect:/settings?saved=true#preferences";
    }

    @PostMapping("/settings/profile")
    @Transactional
    public String updateProfile(Authentication authentication,
                                @RequestParam String fullName,
                                @RequestParam(required = false) String department,
                                @RequestParam(required = false) String program,
                                @RequestParam(required = false) String section,
                                @RequestParam(required = false) BigDecimal tenthPercentage,
                                @RequestParam(required = false) BigDecimal twelfthPercentage,
                                RedirectAttributes redirect) {
        Student student = resolveStudent(authentication);
        if (student == null) return "redirect:/dashboard";
        String name = fullName == null ? "" : fullName.trim().replaceAll("\\s+", " ");
        if (name.length() < 2 || name.length() > 180) {
            redirect.addAttribute("error", "name");
            return "redirect:/settings#profile-details";
        }
        if (!validPercent(tenthPercentage) || !validPercent(twelfthPercentage)) {
            redirect.addAttribute("error", "marks");
            return "redirect:/settings#profile-details";
        }
        student.setFullName(name);
        student.setDepartment(clean(department, 180, student.getDepartment()));
        student.setProgram(clean(program, 120, student.getProgram()));
        student.setSection(clean(section, 50, student.getSection()));
        students.updateProfile(student);
        jdbc.update("INSERT INTO student_academic_marks(registration_no,tenth_percentage,twelfth_percentage) VALUES(?,?,?) ON DUPLICATE KEY UPDATE tenth_percentage=VALUES(tenth_percentage),twelfth_percentage=VALUES(twelfth_percentage)", student.getRegistrationNo(), tenthPercentage, twelfthPercentage);
        redirect.addAttribute("saved", "true");
        return "redirect:/settings#profile-details";
    }

    private Student resolveStudent(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getName() != null && !"anonymousUser".equals(authentication.getName())) {
            return students.findByLoginId(authentication.getName()).orElse(null);
        }
        return null;
    }
    private int count(String sql, String registrationNo) {
        try { Integer result = jdbc.queryForObject(sql, Integer.class, registrationNo); return result == null ? 0 : result; }
        catch (DataAccessException ignored) { return 0; }
    }
    private boolean validPercent(BigDecimal value) { return value == null || (value.compareTo(BigDecimal.ZERO) >= 0 && value.compareTo(new BigDecimal("100")) <= 0); }
    private String clean(String value, int max, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String cleaned = value.trim(); return cleaned.length() > max ? cleaned.substring(0, max) : cleaned;
    }
    private String initial(String name) { return name == null || name.isBlank() ? "N" : name.trim().substring(0, 1).toUpperCase(java.util.Locale.ROOT); }
}
