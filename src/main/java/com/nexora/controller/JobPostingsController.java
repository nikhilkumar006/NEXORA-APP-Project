package com.nexora.controller;

import com.nexora.model.Student;
import com.nexora.repository.StudentRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Student-facing opportunity hub. Live listings are rendered by Unstop itself; no local fake listings are seeded. */
@Controller
public class JobPostingsController {
    private final StudentRepository students;

    public JobPostingsController(StudentRepository students) {
        this.students = students;
    }

    @GetMapping("/opportunities")
    public String opportunities(Authentication authentication, Model model) {
        Student student = resolveStudent(authentication);
        if (student == null) return "redirect:/login?error=account";
        model.addAttribute("student", student);
        model.addAttribute("studentInitial", student.getFullName() == null || student.getFullName().isBlank()
                ? "N" : student.getFullName().trim().substring(0, 1).toUpperCase(java.util.Locale.ROOT));
        return "opportunities";
    }

    /** Backwards-compatible route for old bookmarks. */
    @GetMapping("/job-postings")
    public String oldJobPostingsRoute() {
        return "redirect:/opportunities";
    }

    private Student resolveStudent(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getName() != null && !"anonymousUser".equals(authentication.getName())) {
            return students.findByLoginId(authentication.getName()).orElse(null);
        }
        return null;
    }
}
