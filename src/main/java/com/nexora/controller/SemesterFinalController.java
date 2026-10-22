package com.nexora.controller;

import com.nexora.academic.AcademicAnalysisService;
import com.nexora.academic.AcademicSubject;
import com.nexora.academic.AcademicSubjectService;
import com.nexora.academic.AcademicUnit;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Controller
public class SemesterFinalController {
    private final AcademicSubjectService subjects;
    private final AcademicAnalysisService analysis;
    public SemesterFinalController(AcademicSubjectService subjects, AcademicAnalysisService analysis) { this.subjects = subjects; this.analysis = analysis; }

    @GetMapping("/academic/{subjectCode}/semester-final")
    public String semesterFinal(@PathVariable String subjectCode, Model model) {
        AcademicSubject subject = subjects.getSubjectByCode(subjectCode);
        if (subject == null) return "redirect:/academic";
        if (!subject.isSemesterFinalAvailable()) return "redirect:/academic/" + subjectCode;
        List<AcademicUnit> units = analysis.getUnits(subjectCode);
        model.addAttribute("subject", subject);
        model.addAttribute("units", units);
        return "academic-semester-final";
    }
}
