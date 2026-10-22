package com.nexora.academic;

import java.util.List;

public interface AcademicQuestionRepository {
    void replaceForResource(long resourceId, String subjectCode, String resourceType, Integer year,
                            String extractionMethod, List<ExtractedAcademicQuestion> questions);
    void deleteForResource(long resourceId);
    List<ExtractedAcademicQuestion> findForResource(long resourceId);
    List<ExtractedAcademicQuestion> findForSubject(String subjectCode, String resourceType);
    int countForSubject(String subjectCode, String resourceType);
}
