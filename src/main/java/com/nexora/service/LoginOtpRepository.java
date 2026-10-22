package com.nexora.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public class LoginOtpRepository {
    private final JdbcTemplate jdbc;
    public LoginOtpRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void save(String registrationNo, String email, String otpHash, Instant expiresAt) {
        jdbc.update("INSERT INTO pending_login_otps(registration_no,college_email,otp_hash,otp_expires_at,attempts) VALUES(?,?,?,?,0) ON DUPLICATE KEY UPDATE college_email=VALUES(college_email),otp_hash=VALUES(otp_hash),otp_expires_at=VALUES(otp_expires_at),attempts=0,created_at=CURRENT_TIMESTAMP",
                registrationNo, email, otpHash, expiresAt);
    }
    public Optional<PendingLoginOtp> find(String registrationNo) {
        return jdbc.query("SELECT registration_no,college_email,otp_hash,otp_expires_at,attempts FROM pending_login_otps WHERE registration_no=? LIMIT 1",
                rs -> rs.next() ? Optional.of(new PendingLoginOtp(rs.getString(1),rs.getString(2),rs.getString(3),rs.getTimestamp(4).toInstant(),rs.getInt(5))) : Optional.empty(), registrationNo);
    }
    public void incrementAttempts(String registrationNo) { jdbc.update("UPDATE pending_login_otps SET attempts=attempts+1 WHERE registration_no=?", registrationNo); }
    public void delete(String registrationNo) { jdbc.update("DELETE FROM pending_login_otps WHERE registration_no=?", registrationNo); }
    public record PendingLoginOtp(String registrationNo,String email,String otpHash,Instant expiresAt,int attempts) {}
}
