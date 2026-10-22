package com.nexora.controller;

import com.nexora.academic.AcademicSubject;
import com.nexora.academic.AcademicSubjectService;
import com.nexora.analysis.AcademicRecommendationService;
import com.nexora.repository.StudentRepository;
import com.nexora.subscription.SubscriptionService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class AcademicRecommendationController {
    private final AcademicSubjectService subjects;
    private final AcademicRecommendationService recommendations;
    private final StudentRepository students;
    private final SubscriptionService subscriptions;

    public AcademicRecommendationController(AcademicSubjectService subjects, AcademicRecommendationService recommendations,
                                            StudentRepository students, SubscriptionService subscriptions) {
        this.subjects = subjects;
        this.recommendations = recommendations;
        this.students = students;
        this.subscriptions = subscriptions;
    }

    @GetMapping("/academic/{subjectCode}/recommendation")
    public String recommendation(@PathVariable String subjectCode, Authentication auth, Model model) {
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null || "anonymousUser".equals(auth.getName()))
            return "redirect:/login";
        var student = students.findByLoginId(auth.getName()).orElse(null);
        if (student == null) return "redirect:/login?error=account";
        if (!subscriptions.hasPlanAtLeast(student.getRegistrationNo(), "PREMIUM"))
            return "redirect:/profile?upgrade=premium#subscription";
        AcademicSubject subject = subjects.getSubjectByCode(subjectCode);
        if (subject == null) return "redirect:/academic";
        model.addAttribute("subject", subject);
        model.addAttribute("recommendations", recommendations.recommend(subjectCode));
        return "academic-recommendation";
    }
}
