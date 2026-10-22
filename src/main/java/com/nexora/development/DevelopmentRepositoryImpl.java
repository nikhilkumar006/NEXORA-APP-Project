package com.nexora.development;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class DevelopmentRepositoryImpl implements DevelopmentRepository {
    private final JdbcTemplate jdbc;

    public DevelopmentRepositoryImpl(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public List<Skill> findSkills(String registrationNo) {
        return jdbc.query("""
                SELECT id, registration_no, skill_name, skill_level, status, notes
                FROM student_skills WHERE registration_no=? ORDER BY id DESC
                """, (rs, n) -> new Skill(rs.getLong("id"), rs.getString("registration_no"),
                rs.getString("skill_name"), rs.getString("skill_level"), rs.getString("status"), rs.getString("notes")), registrationNo);
    }

    @Override
    public void addSkill(String registrationNo, String name, String level, String status, String notes) {
        jdbc.update("INSERT INTO student_skills(registration_no,skill_name,skill_level,status,notes) VALUES(?,?,?,?,?)",
                registrationNo, name, level, status, notes);
    }

    @Override
    public void deleteSkill(String registrationNo, long id) {
        jdbc.update("DELETE FROM student_skills WHERE id=? AND registration_no=?", id, registrationNo);
    }

    @Override
    public List<StudentProject> findProjects(String registrationNo) {
        return jdbc.query("""
                SELECT id, registration_no, title, description, technologies, role, status
                FROM student_projects WHERE registration_no=? ORDER BY id DESC
                """, (rs, n) -> new StudentProject(rs.getLong("id"), rs.getString("registration_no"),
                rs.getString("title"), rs.getString("description"), rs.getString("technologies"),
                rs.getString("role"), rs.getString("status")), registrationNo);
    }

    @Override
    public void addProject(String registrationNo, String title, String description, String technologies, String role, String status) {
        jdbc.update("INSERT INTO student_projects(registration_no,title,description,technologies,role,status) VALUES(?,?,?,?,?,?)",
                registrationNo, title, description, technologies, role, status);
    }

    @Override
    public List<ExperienceEntry> findExperiences(String registrationNo) {
        return jdbc.query("""
                SELECT id, registration_no, experience_type, title, organization, description, start_date, end_date
                FROM student_experiences WHERE registration_no=? ORDER BY id DESC
                """, (rs, n) -> new ExperienceEntry(rs.getLong("id"), rs.getString("registration_no"),
                rs.getString("experience_type"), rs.getString("title"), rs.getString("organization"),
                rs.getString("description"), rs.getString("start_date"), rs.getString("end_date")), registrationNo);
    }

    @Override
    public void addExperience(String registrationNo, String type, String title, String organization, String description, String startDate, String endDate) {
        jdbc.update("INSERT INTO student_experiences(registration_no,experience_type,title,organization,description,start_date,end_date) VALUES(?,?,?,?,?,?,?)",
                registrationNo, type, title, organization, description, startDate, endDate);
    }

    @Override
    public CareerTarget findCareerTarget(String registrationNo) {
        List<CareerTarget> rows = jdbc.query("SELECT registration_no,target_role,notes FROM career_targets WHERE registration_no=?",
                (rs,n) -> new CareerTarget(rs.getString("registration_no"), rs.getString("target_role"), rs.getString("notes")), registrationNo);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public void saveCareerTarget(String registrationNo, String targetRole, String notes) {
        jdbc.update("""
                INSERT INTO career_targets(registration_no,target_role,notes) VALUES(?,?,?)
                ON DUPLICATE KEY UPDATE target_role=VALUES(target_role), notes=VALUES(notes)
                """, registrationNo, targetRole, notes);
    }
}
