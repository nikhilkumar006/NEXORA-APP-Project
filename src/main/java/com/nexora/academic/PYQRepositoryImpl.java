package com.nexora.academic;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class PYQRepositoryImpl implements PYQRepository {
    private final JdbcTemplate jdbcTemplate;
    public PYQRepositoryImpl(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @Override public List<PYQQuestion> findBySubjectCode(String subjectCode) {
        return dedupe(extracted(subjectCode, null));
    }

    @Override public List<PYQQuestion> findBySubjectCodeAndTopic(String subjectCode, String topicName) {
        return dedupe(extracted(subjectCode, topicName));
    }

    @Override public List<PYQExamStats> findExamStatisticsForTopic(String subjectCode, String topicName) {
        return jdbcTemplate.query("""
            SELECT 'Semester Final' AS exam_type,
                   COALESCE(CAST(q.exam_year AS CHAR), CAST(r.resource_year AS CHAR), 'Unknown') AS exam_year,
                   COUNT(*) AS occurrences, SUM(q.marks) AS total_marks
            FROM academic_extracted_questions q
            JOIN academic_resources r ON r.id=q.resource_id
            WHERE q.subject_code=? AND r.resource_type = 'SEMESTER_PYQS'
              AND COALESCE(r.approval_status,'APPROVED')='APPROVED'
              AND LOWER(TRIM(q.topic))=LOWER(TRIM(?))
              AND COALESCE(q.exam_year,r.resource_year) BETWEEN ? AND ?
            GROUP BY COALESCE(q.exam_year,r.resource_year) ORDER BY COALESCE(q.exam_year,r.resource_year) DESC
            """, (rs,n) -> new PYQExamStats(rs.getString("exam_type"), rs.getString("exam_year"), rs.getInt("occurrences"), rs.getInt("total_marks")), subjectCode, topicName, java.time.Year.now().getValue()-4, java.time.Year.now().getValue());
    }

    private List<PYQQuestion> extracted(String subjectCode, String topic) {
        String sql = """
            SELECT q.id, 'Semester Final' AS exam_type,
                   COALESCE(CAST(q.exam_year AS CHAR), CAST(r.resource_year AS CHAR), 'Unknown') AS exam_year, q.topic, q.question_model,
                   q.question_text, q.marks, COALESCE(r.drive_web_view_link, CONCAT('/academic/resource/',r.id)) AS source, r.original_filename AS source_name
            FROM academic_extracted_questions q
            JOIN academic_resources r ON r.id=q.resource_id
            WHERE q.subject_code=? AND r.resource_type = 'SEMESTER_PYQS'
              AND COALESCE(r.approval_status,'APPROVED')='APPROVED'
        """;
        if (topic != null) sql += " AND LOWER(TRIM(q.topic))=LOWER(TRIM(?))";
        sql += " ORDER BY COALESCE(q.exam_year,r.resource_year) DESC, r.id DESC, q.id";
        return topic == null ? jdbcTemplate.query(sql,this::mapRow,subjectCode) : jdbcTemplate.query(sql,this::mapRow,subjectCode,topic);
    }

    private List<PYQQuestion> dedupe(List<PYQQuestion> input) {
        Map<String,PYQQuestion> unique = new LinkedHashMap<>();
        for (PYQQuestion q : input) {
            String key = normalize(q.getYear()) + "|" + normalize(q.getTopic()) + "|" + normalize(q.getQuestionText());
            unique.putIfAbsent(key, q);
        }
        return new ArrayList<>(unique.values());
    }
    private String normalize(String s){return s==null?"":s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim();}
    private PYQQuestion mapRow(java.sql.ResultSet rs,int rowNum)throws java.sql.SQLException{return new PYQQuestion(String.valueOf(rs.getLong("id")),rs.getString("exam_type"),rs.getString("exam_year"),rs.getString("topic"),rs.getString("question_model"),rs.getString("question_text"),rs.getInt("marks"),rs.getString("source"),rs.getString("source_name"));}
}
