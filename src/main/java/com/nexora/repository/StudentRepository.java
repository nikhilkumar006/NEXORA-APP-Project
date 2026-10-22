package com.nexora.repository;

import com.nexora.model.Student;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import com.nexora.academic.AcademicPeriod;
import java.time.LocalDate;
import java.time.ZoneId;

@Repository
public class StudentRepository {
    private final JdbcTemplate jdbcTemplate;
    public StudentRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }
    private Student mapStudent(ResultSet rs, int rowNum) throws SQLException {
        Student s = new Student();
        s.setStudentId(rs.getLong("student_id"));
        s.setRegistrationNo(rs.getString("registration_no"));
        s.setCollegeEmail(rs.getString("college_email"));
        s.setFullName(rs.getString("full_name"));
        s.setDepartment(rs.getString("department"));
        s.setProgram(rs.getString("program"));
        s.setSection(rs.getString("section"));
        s.setBatchStartYear(rs.getInt("batch_start_year"));
        s.setBatchEndYear(rs.getInt("batch_end_year"));
        s.setCurrentYear(rs.getInt("current_year"));
        s.setCurrentSemester(rs.getInt("current_semester"));
        s.setActive(rs.getBoolean("active"));
        s.setAccountCreated(rs.getBoolean("account_created"));
        s.setPasswordHash(rs.getString("password_hash"));
        return s;
    }
    public Optional<Student> findByLoginId(String loginId) {
        String value = loginId == null ? "" : loginId.trim();
        String email = value.toLowerCase().contains("@") ? value.toLowerCase() : value.toLowerCase() + "@srmist.edu.in";
        // Keep the indexed lookup simple: avoid OR/LOWER(TRIM(column)) expressions that can
        // force a table scan. Email addresses are normalized to lowercase on registration.
        List<Student> students;
        if (value.contains("@")) {
            students = jdbcTemplate.query("SELECT * FROM students WHERE college_email = ? LIMIT 1", this::mapStudent, email);
        } else {
            students = jdbcTemplate.query("SELECT * FROM students WHERE registration_no = ? LIMIT 1", this::mapStudent, value.toUpperCase());
            if (students.isEmpty()) {
                students = jdbcTemplate.query("SELECT * FROM students WHERE college_email = ? LIMIT 1", this::mapStudent, email);
            }
        }
        if (students.isEmpty()) return Optional.empty();
        Student student = students.get(0);
        // Calculate the current period once, update the database only if it actually changed,
        // and update this in-memory object. This avoids an unnecessary second SELECT on every request.
        try {
            AcademicPeriod.Current period = AcademicPeriod.calculate(
                    student.getBatchStartYear(), LocalDate.now(ZoneId.of("Asia/Kolkata")));
            if (student.getCurrentYear() != period.yearOfStudy()
                    || student.getCurrentSemester() != period.semester()) {
                jdbcTemplate.update("UPDATE students SET current_year=?, current_semester=? WHERE registration_no=?",
                        period.yearOfStudy(), period.semester(), student.getRegistrationNo());
                student.setCurrentYear(period.yearOfStudy());
                student.setCurrentSemester(period.semester());
            }
        } catch (Exception ignored) {
            // Keep the last known values if a legacy student record has an invalid cohort.
        }
        return Optional.of(student);
    }


    public Optional<Student> findFirstActive() {
        List<Student> students = jdbcTemplate.query(
                "SELECT * FROM students WHERE active=TRUE ORDER BY student_id ASC LIMIT 1",
                this::mapStudent);
        return students.stream().findFirst();
    }

    public void refreshAcademicPeriod(String registrationNo, int batchStartYear) {
        try {
            AcademicPeriod.Current p = AcademicPeriod.calculate(batchStartYear, LocalDate.now(ZoneId.of("Asia/Kolkata")));
            jdbcTemplate.update("UPDATE students SET current_year=?, current_semester=? WHERE registration_no=? AND (current_year <> ? OR current_semester <> ?)",
                    p.yearOfStudy(), p.semester(), registrationNo, p.yearOfStudy(), p.semester());
        } catch (Exception ignored) { }
    }

    public Optional<Student> findByRegistrationAndEmail(String registrationNo, String email) {
        List<Student> students = jdbcTemplate.query("SELECT * FROM students WHERE registration_no = ? AND LOWER(college_email) = LOWER(?) LIMIT 1", this::mapStudent, registrationNo, email);
        return students.stream().findFirst();
    }
    public boolean existsByRegistrationOrEmail(String registrationNo, String email) {
        Integer n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM students WHERE registration_no = ? OR LOWER(college_email)=LOWER(?)", Integer.class, registrationNo, email);
        return n != null && n > 0;
    }
    public void createStudent(Student s) {
        jdbcTemplate.update("INSERT INTO students(registration_no,college_email,full_name,department,program,section,batch_start_year,batch_end_year,current_year,current_semester,active,account_created,password_hash) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                s.getRegistrationNo(),s.getCollegeEmail(),s.getFullName(),s.getDepartment(),s.getProgram(),s.getSection(),s.getBatchStartYear(),s.getBatchEndYear(),s.getCurrentYear(),s.getCurrentSemester(),true,true,s.getPasswordHash());
    }
    public void createAccount(String registrationNo, String passwordHash) {
        jdbcTemplate.update("UPDATE students SET password_hash=?, account_created=TRUE WHERE registration_no=?", passwordHash, registrationNo);
    }
    public void updateProfile(Student s) {
        jdbcTemplate.update("UPDATE students SET full_name=?,department=?,program=?,section=?,batch_start_year=?,batch_end_year=?,current_year=?,current_semester=? WHERE registration_no=?",
                s.getFullName(),s.getDepartment(),s.getProgram(),s.getSection(),s.getBatchStartYear(),s.getBatchEndYear(),s.getCurrentYear(),s.getCurrentSemester(),s.getRegistrationNo());
    }
}
