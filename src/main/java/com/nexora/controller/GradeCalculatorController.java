package com.nexora.controller;

import com.nexora.career.GradeCalculatorService;
import com.nexora.model.Student;
import com.nexora.repository.StudentRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.stream.IntStream;

@Controller
public class GradeCalculatorController {
    private final StudentRepository students;
    private final GradeCalculatorService calculator;

    public GradeCalculatorController(StudentRepository students, GradeCalculatorService calculator) {
        this.students = students;
        this.calculator = calculator;
    }

    @GetMapping("/grade-calculator")
    public String page(Authentication authentication, @RequestParam(required = false) Integer semester,
                       @RequestParam(required = false) String saved, @RequestParam(required = false) String synced,
                       @RequestParam(required = false) String error,
                       @RequestParam(required = false) String historySaved,
                       @RequestParam(required = false) String historySynced,
                       @RequestParam(required = false) String historyError,
                       Model model) {
        Student student = resolveStudent(authentication);
        if (student == null) return "redirect:/dashboard";
        final int selectedSemester = student.getCurrentSemester() < 1 || student.getCurrentSemester() > 8 ? 1 : student.getCurrentSemester();

        model.addAttribute("student", student);
        model.addAttribute("studentInitial", student.getFullName() == null || student.getFullName().isBlank()
                ? "N" : student.getFullName().substring(0, 1).toUpperCase());
        model.addAttribute("selectedSemester", selectedSemester);
        String studentReg = student.getRegistrationNo();
        model.addAttribute("historicalSemesterOptions", IntStream.range(1, selectedSemester).filter(sem -> calculator.subjects(studentReg, sem).isEmpty()).boxed().toList());
        model.addAttribute("semesterSubjects", calculator.subjects(student.getRegistrationNo(), selectedSemester));
        model.addAttribute("gradeChoices", calculator.gradeChoices());
        GradeCalculatorService.CalculatorSummary summary = calculator.summary(student.getRegistrationNo());
        model.addAttribute("summary", summary);
        model.addAttribute("selectedSummary", summary.semesters().stream()
                .filter(item -> item.semester() == selectedSemester).findFirst().orElse(null));
        model.addAttribute("creditOptions", List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        model.addAttribute("saved", saved != null);
        model.addAttribute("synced", "true".equalsIgnoreCase(synced));
        model.addAttribute("error", error);
        model.addAttribute("historySaved", historySaved != null);
        model.addAttribute("historySynced", "true".equalsIgnoreCase(historySynced));
        model.addAttribute("historyError", historyError);
        model.addAttribute("showCgpaTab", historySaved != null || historyError != null);
        return "grade-calculator";
    }

    @PostMapping("/grade-calculator/semester")
    public String save(Authentication authentication, @RequestParam(required = false) Integer semester,
                       @RequestParam("subjectName") List<String> subjectNames,
                       @RequestParam("credits") List<Integer> credits,
                       @RequestParam("grade") List<String> grades) {
        Student student = resolveStudent(authentication);
        if (student == null) return "redirect:/dashboard";
        int currentSemester = student.getCurrentSemester();
        try {
            boolean synced = calculator.saveSemester(student.getRegistrationNo(), currentSemester, subjectNames, credits, grades);
            return "redirect:/grade-calculator?saved=true&synced=" + synced;
        } catch (IllegalArgumentException ex) {
            return "redirect:/grade-calculator?error=invalid";
        }
    }


    @PostMapping("/grade-calculator/history")
    public String saveHistorical(Authentication authentication,
                                 @RequestParam int semester,
                                 @RequestParam BigDecimal sgpa,
                                 @RequestParam int totalCredits) {
        Student student = resolveStudent(authentication);
        if (student == null) return "redirect:/dashboard";
        try {
            boolean synced = calculator.saveHistoricalSemesterGpa(student.getRegistrationNo(), semester, sgpa, totalCredits, student.getCurrentSemester());
            return "redirect:/grade-calculator?historySaved=true&historySynced=" + synced;
        } catch (IllegalArgumentException ex) {
            return "redirect:/grade-calculator?historyError=" + URLEncoder.encode(ex.getMessage(), StandardCharsets.UTF_8);
        }
    }

    @PostMapping("/grade-calculator/delete-semester")
    public String deleteSemester(Authentication authentication, @RequestParam int semester) {
        Student student = resolveStudent(authentication);
        if (student == null) return "redirect:/dashboard";
        try {
            calculator.deleteSemesterResults(student.getRegistrationNo(), semester, student.getCurrentSemester());
            return "redirect:/grade-calculator?deleted=true";
        } catch (IllegalArgumentException ex) {
            return "redirect:/grade-calculator?error=invalid";
        }
    }

    private Student resolveStudent(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated() && authentication.getName() != null
                && !"anonymousUser".equals(authentication.getName())) {
            Student found = students.findByLoginId(authentication.getName()).orElse(null);
            if (found != null) return found;
        }
        return null;
    }
}
