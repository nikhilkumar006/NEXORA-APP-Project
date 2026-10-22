package com.nexora.academic;

import java.util.List;

public interface AcademicSubjectRepository {

    List<AcademicSubject> findAll();

    AcademicSubject findBySubjectCode(String subjectCode);
    List<AcademicSubject> findBySemester(int semester);
}