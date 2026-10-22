package com.nexora.academic;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AcademicQuestionRepositoryImpl implements AcademicQuestionRepository {
    private final JdbcTemplate jdbc;
    public AcademicQuestionRepositoryImpl(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public void replaceForResource(long resourceId, String subjectCode, String resourceType, Integer year,
                                              String extractionMethod, List<ExtractedAcademicQuestion> questions) {
        jdbc.update("DELETE FROM academic_extracted_questions WHERE resource_id=?", resourceId);
        for (ExtractedAcademicQuestion q : questions) {
            jdbc.update("""
                INSERT INTO academic_extracted_questions
                (resource_id, subject_code, resource_type, exam_year, question_number, question_text,
                 topic, question_model, marks, confidence, extraction_method)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """, resourceId, subjectCode, resourceType, year, q.getQuestionNumber(), q.getQuestionText(),
                    q.getTopic(), q.getQuestionModel(), q.getMarks(), q.getConfidence(), extractionMethod);
        }
    }

    @Override public void deleteForResource(long resourceId) { jdbc.update("DELETE FROM academic_extracted_questions WHERE resource_id=?", resourceId); }

    @Override public List<ExtractedAcademicQuestion> findForResource(long resourceId) {
        return jdbc.query("""
            SELECT question_number, question_text, topic, question_model, marks, confidence
            FROM academic_extracted_questions q JOIN academic_resources r ON r.id=q.resource_id WHERE q.resource_id=? AND COALESCE(r.approval_status,'APPROVED')='APPROVED' ORDER BY q.id
            """, (rs, n) -> new ExtractedAcademicQuestion(rs.getString("question_number"), rs.getString("question_text"),
                    rs.getString("topic"), rs.getString("question_model"), rs.getInt("marks"), rs.getDouble("confidence")), resourceId);
    }

    @Override public List<ExtractedAcademicQuestion> findForSubject(String subjectCode, String resourceType) {
        return jdbc.query("""
            SELECT q.question_number, q.question_text, q.topic, q.question_model, q.marks, q.confidence
            FROM academic_extracted_questions q JOIN academic_resources r ON r.id=q.resource_id
            WHERE q.subject_code=? AND q.resource_type=? AND COALESCE(r.approval_status,'APPROVED')='APPROVED' ORDER BY q.exam_year DESC, q.id
            """, (rs, n) -> new ExtractedAcademicQuestion(rs.getString("question_number"), rs.getString("question_text"),
                    rs.getString("topic"), rs.getString("question_model"), rs.getInt("marks"), rs.getDouble("confidence")), subjectCode, resourceType);
    }

    @Override public int countForSubject(String subjectCode, String resourceType) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM academic_extracted_questions q JOIN academic_resources r ON r.id=q.resource_id WHERE q.subject_code=? AND q.resource_type=? AND COALESCE(r.approval_status,'APPROVED')='APPROVED'", Integer.class, subjectCode, resourceType);
        return n == null ? 0 : n;
    }
}
