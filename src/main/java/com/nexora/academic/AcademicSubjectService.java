package com.nexora.academic;

import java.util.List;

public interface AcademicSubjectService {

    List<AcademicSubject> getAllSubjects();

    List<AcademicSubject> getSubjectsForSemester(int semester);

    AcademicSubject getSubjectByCode(String subjectCode);
}