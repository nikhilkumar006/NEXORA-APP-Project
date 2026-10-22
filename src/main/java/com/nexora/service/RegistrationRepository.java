package com.nexora.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class RegistrationRepository {
    private final JdbcTemplate jdbc;
    public RegistrationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void save(RegistrationService.RegistrationData d, String otpHash, Instant expiresAt) {
        jdbc.update("INSERT INTO pending_student_registrations(registration_no,college_email,full_name,department,program,section,batch_start_year,batch_end_year,current_year,current_semester,password_hash,otp_hash,otp_expires_at,attempts) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,0) ON DUPLICATE KEY UPDATE registration_no=VALUES(registration_no),full_name=VALUES(full_name),department=VALUES(department),program=VALUES(program),section=VALUES(section),batch_start_year=VALUES(batch_start_year),batch_end_year=VALUES(batch_end_year),current_year=VALUES(current_year),current_semester=VALUES(current_semester),password_hash=VALUES(password_hash),otp_hash=VALUES(otp_hash),otp_expires_at=VALUES(otp_expires_at),attempts=0,created_at=CURRENT_TIMESTAMP",
                d.registrationNo(), d.email(), d.fullName(), d.department(), d.program(), d.section(), d.batchStart(), d.batchEnd(), d.currentYear(), d.currentSemester(), d.passwordHash(), otpHash, expiresAt);
    }

    public Optional<PendingRegistration> find(String email) {
        List<PendingRegistration> rows = jdbc.query("SELECT * FROM pending_student_registrations WHERE college_email=? LIMIT 1", (rs,n) ->
                new PendingRegistration(rs.getString("registration_no"), rs.getString("college_email"), rs.getString("full_name"), rs.getString("department"), rs.getString("program"), rs.getString("section"), rs.getInt("batch_start_year"), rs.getInt("batch_end_year"), rs.getInt("current_year"), rs.getInt("current_semester"), rs.getString("password_hash"), rs.getString("otp_hash"), rs.getTimestamp("otp_expires_at").toInstant(), rs.getInt("attempts")), email);
        return rows.stream().findFirst();
    }

    public void incrementAttempts(String email) { jdbc.update("UPDATE pending_student_registrations SET attempts=attempts+1 WHERE college_email=?", email); }
    public void updateOtp(String email, String hash, Instant expiry) { jdbc.update("UPDATE pending_student_registrations SET otp_hash=?,otp_expires_at=?,attempts=0 WHERE college_email=?", hash, expiry, email); }
    public void deleteForEmail(String email) { jdbc.update("DELETE FROM pending_student_registrations WHERE college_email=?", email); }
    public void deleteExpiredForEmail(String email) { jdbc.update("DELETE FROM pending_student_registrations WHERE college_email=? AND otp_expires_at < CURRENT_TIMESTAMP", email); }

    public record PendingRegistration(String registrationNo, String email, String fullName, String department, String program, String section,
                                      int batchStart, int batchEnd, int currentYear, int currentSemester, String passwordHash,
                                      String otpHash, Instant expiresAt, int attempts) {}
}
