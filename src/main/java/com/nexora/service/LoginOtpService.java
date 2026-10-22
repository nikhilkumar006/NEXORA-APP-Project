package com.nexora.service;

import com.nexora.model.Student;
import com.nexora.repository.StudentRepository;
import com.nexora.security.OtpRateLimitService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Optional;

@Service
public class LoginOtpService {
    public static final int OTP_EXPIRY_MINUTES = 5;
    private static final int MAX_ATTEMPTS = 5;

    private final LoginOtpRepository repository;
    private final StudentRepository studentRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailService;
    private final OtpRateLimitService rateLimit;
    private final SecureRandom random = new SecureRandom();

    public LoginOtpService(LoginOtpRepository repository, StudentRepository studentRepository,
                           PasswordEncoder passwordEncoder, EmailVerificationService emailService,
                           OtpRateLimitService rateLimit) {
        this.repository = repository;
        this.studentRepository = studentRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.rateLimit = rateLimit;
    }

    public LoginStartResult start(String loginId, String password) {
        String normalized = StudentLoginId.normalize(loginId);
        if (normalized.isBlank()) return LoginStartResult.error("Enter your SRM email ID or registration number.");
        Optional<Student> found = studentRepository.findByLoginId(normalized);
        if (found.isEmpty()) return LoginStartResult.error("Invalid login details.");
        Student student = found.get();
        if (!student.isActive() || !student.isAccountCreated() || student.getPasswordHash() == null)
            return LoginStartResult.error("This account is inactive or not ready for login.");
        if (!passwordEncoder.matches(password == null ? "" : password, student.getPasswordHash()))
            return LoginStartResult.error("Invalid login details.");
        if (!rateLimit.allowSend(student.getRegistrationNo(), "LOGIN"))
            return LoginStartResult.error("Please wait 30 seconds between OTP requests. A maximum of 10 OTPs can be sent per hour for this account.");

        String otp = String.format("%06d", random.nextInt(1_000_000));
        Instant expiry = Instant.now().plusSeconds(OTP_EXPIRY_MINUTES * 60L);
        repository.save(student.getRegistrationNo(), student.getCollegeEmail(), passwordEncoder.encode(otp), expiry);
        try {
            emailService.sendLoginOtp(student.getCollegeEmail(), otp);
        } catch (RuntimeException ex) {
            repository.delete(student.getRegistrationNo());
            return LoginStartResult.error(ex.getMessage());
        }
        return LoginStartResult.success(student.getRegistrationNo(), student.getCollegeEmail(), expiry);
    }

    public VerifyResult verify(String registrationNo, String otp) {
        String reg = registrationNo == null ? "" : registrationNo.trim().toUpperCase();
        LoginOtpRepository.PendingLoginOtp pending = repository.find(reg).orElse(null);
        if (pending == null) return VerifyResult.error("No pending login was found. Start login again.");
        if (pending.expiresAt().isBefore(Instant.now())) {
            repository.delete(reg);
            return VerifyResult.error("The OTP has expired. Start login again.");
        }
        if (pending.attempts() >= MAX_ATTEMPTS) {
            repository.delete(reg);
            return VerifyResult.error("Too many incorrect attempts. Start login again.");
        }
        if (!passwordEncoder.matches(otp == null ? "" : otp.trim(), pending.otpHash())) {
            repository.incrementAttempts(reg);
            return VerifyResult.error("Incorrect OTP.");
        }
        repository.delete(reg);
        return VerifyResult.success(pending.registrationNo());
    }

    public String resend(String registrationNo) {
        String reg = registrationNo == null ? "" : registrationNo.trim().toUpperCase();
        Optional<Student> student = studentRepository.findByLoginId(reg);
        if (student.isEmpty() || !student.get().isActive()) return "Login session expired. Start again.";
        if (!rateLimit.allowSend(student.get().getRegistrationNo(), "LOGIN"))
            return "Please wait 30 seconds between OTP requests. A maximum of 10 OTPs can be sent per hour for this account.";
        String otp = String.format("%06d", random.nextInt(1_000_000));
        Instant expiry = Instant.now().plusSeconds(OTP_EXPIRY_MINUTES * 60L);
        repository.save(reg, student.get().getCollegeEmail(), passwordEncoder.encode(otp), expiry);
        try {
            emailService.sendLoginOtp(student.get().getCollegeEmail(), otp);
        } catch (RuntimeException ex) {
            return ex.getMessage();
        }
        return null;
    }

    public record LoginStartResult(boolean success, String registrationNo, String email, Instant expiresAt, String error) {
        static LoginStartResult success(String reg, String email, Instant expiry) { return new LoginStartResult(true, reg, email, expiry, null); }
        static LoginStartResult error(String error) { return new LoginStartResult(false, null, null, null, error); }
    }
    public record VerifyResult(boolean success, String registrationNo, String error) {
        static VerifyResult success(String reg) { return new VerifyResult(true, reg, null); }
        static VerifyResult error(String error) { return new VerifyResult(false, null, error); }
    }
}
