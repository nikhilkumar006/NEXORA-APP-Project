package com.nexora.academic;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AcademicSubjectServiceImpl
        implements AcademicSubjectService {

    private final AcademicSubjectRepository repository;

    public AcademicSubjectServiceImpl(
            AcademicSubjectRepository repository) {

        this.repository = repository;
    }

    @Override
    public List<AcademicSubject> getAllSubjects() {
        List<AcademicSubject> saved = repository.findAll();
        return saved.isEmpty() ? semesterThreeFallback() : saved;
    }

    @Override
    public List<AcademicSubject> getSubjectsForSemester(int semester) {
        List<AcademicSubject> saved = repository.findBySemester(semester);
        return saved.isEmpty() && semester == 3 ? semesterThreeFallback() : saved;
    }

    @Override
    public AcademicSubject getSubjectByCode(String subjectCode) {
        AcademicSubject saved = repository.findBySubjectCode(subjectCode);
        if (saved != null) return saved;
        return semesterThreeFallback().stream().filter(s -> s.getCode().equalsIgnoreCase(subjectCode == null ? "" : subjectCode)).findFirst().orElse(null);
    }

    private List<AcademicSubject> semesterThreeFallback() {
        return List.of(
                new AcademicSubject("21MAB201T", "Transforms and Boundary Value Problems", true, true, true, 3, 1),
                new AcademicSubject("21DCS201P", "Design Thinking Methodology", false, true, true, 3, 2),
                new AcademicSubject("21CSS201T", "Computer Organization and Architecture", true, true, true, 3, 3),
                new AcademicSubject("21CSC201J", "Data Structures and Algorithms", true, true, true, 3, 4),
                new AcademicSubject("21CSC202J", "Operating System", true, true, true, 3, 5),
                new AcademicSubject("21CSC203P", "Advanced Programming Practice", false, true, true, 3, 6));
    }
}