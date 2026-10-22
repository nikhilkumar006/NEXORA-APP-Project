package com.nexora.controller;

import com.nexora.model.Student;
import com.nexora.academic.AcademicPeriod;
import com.nexora.academic.DashboardGreeting;
import com.nexora.repository.StudentRepository;
import com.nexora.subscription.SubscriptionService;
import com.nexora.xp.XpService;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Controller
public class DashboardController {
    private final StudentRepository students;
    private final SubscriptionService subscriptions;
    private final XpService xp;
    public DashboardController(StudentRepository students, SubscriptionService subscriptions, XpService xp) {
        this.students = students;
        this.subscriptions = subscriptions;
        this.xp = xp;
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model,
                            @RequestParam(required = false) String rewardRequested,
                            @RequestParam(required = false) String rewardError) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null || "anonymousUser".equals(authentication.getName())) {
            return "redirect:/login";
        }
        Student student = students.findByLoginId(authentication.getName()).orElse(null);
        if (student == null) return "redirect:/login?error=account";
        // StudentRepository keeps the academic period current and avoids redundant reads/writes.
        ZonedDateTime nowIst = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
        AcademicPeriod.Current state = AcademicPeriod.calculate(student.getBatchStartYear(), nowIst.toLocalDate());
        var currentPlan = subscriptions.current(student.getRegistrationNo());

        model.addAttribute("student", student);
        model.addAttribute("greeting", DashboardGreeting.forHour(nowIst.getHour()));
        model.addAttribute("academicYearLabel", ordinal(state.yearOfStudy()) + " Year");
        model.addAttribute("semesterLabel", state.semester() + ordinalSuffix(state.semester()) + " Semester");
        model.addAttribute("currentPlan", String.valueOf(currentPlan.getOrDefault("plan_name", "Free")));
        String registrationNo = student.getRegistrationNo();
        model.addAttribute("totalXp", xp.total(registrationNo));
        model.addAttribute("contributionXp", xp.contributionXp(registrationNo));
        model.addAttribute("availableContributionXp", xp.availableContributionXp(registrationNo));
        model.addAttribute("xpEvents", xp.recentEvents(registrationNo, 5));
        model.addAttribute("redemptions", xp.redemptions(registrationNo));
        model.addAttribute("rewardRequested", rewardRequested != null);
        model.addAttribute("rewardError", rewardError);
        model.addAttribute("rewardThreshold", XpService.REWARD_THRESHOLD_XP);
        model.addAttribute("rewardAmount", XpService.REWARD_AMOUNT_RUPEES);
        return "dashboard";
    }

    private String ordinal(int value) {
        if (value % 100 >= 11 && value % 100 <= 13) return value + "th";
        return switch (value % 10) {
            case 1 -> value + "st";
            case 2 -> value + "nd";
            case 3 -> value + "rd";
            default -> value + "th";
        };
    }

    private String ordinalSuffix(int value) {
        if (value % 100 >= 11 && value % 100 <= 13) return "th";
        return switch (value % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
    }

}
