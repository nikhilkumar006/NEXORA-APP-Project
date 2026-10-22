package com.nexora.academic;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AcademicSubjectRepositoryImpl
        implements AcademicSubjectRepository {

    private final JdbcTemplate jdbcTemplate;

    public AcademicSubjectRepositoryImpl(
            JdbcTemplate jdbcTemplate) {

        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<AcademicSubject> findAll() {

        String sql = """
                SELECT subject_code,
                       subject_name,
                       semester_final_available,
                       repository_available,
                       academic_intelligence_available, semester_number, display_order
                FROM academic_subjects
                ORDER BY semester_number, display_order, subject_code
                """;

        return jdbcTemplate.query(
                sql,
                this::mapRow
        );
    }

    @Override
    public AcademicSubject findBySubjectCode(
            String subjectCode) {

        String sql = """
                SELECT subject_code,
                       subject_name,
                       semester_final_available,
                       repository_available,
                       academic_intelligence_available, semester_number, display_order
                FROM academic_subjects
                WHERE subject_code = ?
                """;

        List<AcademicSubject> subjects =
                jdbcTemplate.query(
                        sql,
                        this::mapRow,
                        subjectCode
                );

        return subjects.isEmpty()
                ? null
                : subjects.get(0);
    }

    @Override
    public List<AcademicSubject> findBySemester(int semester) {
        return jdbcTemplate.query("""
                SELECT subject_code, subject_name,
                       semester_final_available, repository_available,
                       academic_intelligence_available, semester_number, display_order
                FROM academic_subjects
                WHERE semester_number=?
                ORDER BY display_order, subject_code
                """, this::mapRow, semester);
    }

    private AcademicSubject mapRow(
            java.sql.ResultSet rs,
            int rowNum) throws java.sql.SQLException {

        return new AcademicSubject(
                rs.getString("subject_code"),
                rs.getString("subject_name"),
                rs.getBoolean("semester_final_available"),
                rs.getBoolean("repository_available"),
                rs.getBoolean("academic_intelligence_available"),
                rs.getInt("semester_number"),
                rs.getInt("display_order")
        );
    }
}