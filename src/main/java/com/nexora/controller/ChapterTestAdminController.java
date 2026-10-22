package com.nexora.controller;

import com.nexora.academic.AcademicSubjectService;
import com.nexora.academic.TopicService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

/** Staff/admin page for loading the actual chapter test bank; it does not seed sample questions. */
@Controller
public class ChapterTestAdminController {
    private final JdbcTemplate jdbc;
    private final AcademicSubjectService subjects;
    private final TopicService topics;

    public ChapterTestAdminController(JdbcTemplate jdbc, AcademicSubjectService subjects, TopicService topics) {
        this.jdbc = jdbc; this.subjects = subjects; this.topics = topics;
    }

    @GetMapping("/staff/chapter-tests")
    public String page(Model model) {
        model.addAttribute("subjects", subjects.getAllSubjects());
        model.addAttribute("questionCounts", jdbc.queryForList("SELECT subject_code,chapter_index,COUNT(*) AS question_count FROM chapter_test_questions WHERE active=TRUE GROUP BY subject_code,chapter_index ORDER BY subject_code,chapter_index"));
        return "staff-chapter-tests";
    }

    @PostMapping("/staff/chapter-tests")
    public String add(@RequestParam String subjectCode, @RequestParam int chapterIndex,
                      @RequestParam String questionText, @RequestParam String optionA,
                      @RequestParam String optionB, @RequestParam String optionC,
                      @RequestParam String optionD, @RequestParam String correctOption,
                      @RequestParam(defaultValue="1") BigDecimal marks, RedirectAttributes redirect) {
        if (subjects.getSubjectByCode(subjectCode) == null) return fail(redirect, "Choose a valid subject.");
        int configuredChapterCount = topics.getUnitsForSubject(subjectCode).size();
        if (chapterIndex < 1 || chapterIndex > 20 || (configuredChapterCount > 0 && chapterIndex > configuredChapterCount))
            return fail(redirect, "Choose a valid chapter index for the selected subject.");
        if (questionText == null || questionText.isBlank() || questionText.length() > 8000 || optionA == null || optionA.isBlank() || optionB == null || optionB.isBlank() || optionC == null || optionC.isBlank() || optionD == null || optionD.isBlank())
            return fail(redirect, "Question text and all four answer options are required.");
        String key = correctOption == null ? "" : correctOption.trim().toUpperCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("A","B","C","D").contains(key)) return fail(redirect, "Correct answer must be A, B, C or D.");
        if (marks == null || marks.compareTo(BigDecimal.ZERO) <= 0 || marks.compareTo(BigDecimal.valueOf(100)) > 0) return fail(redirect, "Marks must be greater than zero and at most 100.");
        jdbc.update("INSERT INTO chapter_test_questions(subject_code,chapter_index,question_text,option_a,option_b,option_c,option_d,correct_option,marks,active) VALUES(?,?,?,?,?,?,?,?,?,TRUE)",
                subjectCode, chapterIndex, questionText.trim(), optionA.trim(), optionB.trim(), optionC.trim(), optionD.trim(), key, marks);
        redirect.addFlashAttribute("savedMessage", "Question added to the real chapter test bank. Students can now see it in the selected chapter.");
        return "redirect:/staff/chapter-tests";
    }

    private String fail(RedirectAttributes redirect, String message) {
        redirect.addFlashAttribute("errorMessage", message);
        return "redirect:/staff/chapter-tests";
    }
}
