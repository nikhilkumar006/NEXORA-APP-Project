package com.nexora.service;

import com.nexora.model.Student;
import com.nexora.repository.StudentRepository;
import com.nexora.security.OtpRateLimitService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;

@Service
public class RegistrationService {
    private static final int OTP_EXPIRY_MINUTES = 5;
    private static final int MAX_ATTEMPTS = 5;

    private final RegistrationRepository repository;
    private final StudentRepository studentRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;
    private final OtpRateLimitService rateLimit;
    private final SecureRandom random = new SecureRandom();

    public RegistrationService(RegistrationRepository repository,
                               StudentRepository studentRepository,
                               PasswordEncoder passwordEncoder,
                               EmailVerificationService emailVerificationService,
                               OtpRateLimitService rateLimit) {
        this.repository = repository;
        this.studentRepository = studentRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailVerificationService = emailVerificationService;
        this.rateLimit = rateLimit;
    }

    public String start(RegistrationData data) {
        String validation = validate(data);
        if (validation != null) return validation;
        RegistrationData emailNormalized = data.withEmail(StudentLoginId.normalizeEmail(data.email()));
        if (studentRepository.existsByRegistrationOrEmail(emailNormalized.registrationNo().trim().toUpperCase(), emailNormalized.email()))
            return "An account already exists for this registration number or email.";
        if (!rateLimit.allowSend(emailNormalized.email(), "REGISTER"))
            return "Please wait 30 seconds between verification-code requests. A maximum of 10 codes can be sent per hour for this email address.";

        String otp = String.format("%06d", random.nextInt(1_000_000));
        RegistrationData normalized = emailNormalized.normalized(passwordEncoder.encode(data.password()));
        repository.deleteExpiredForEmail(normalized.email());
        repository.save(normalized, passwordEncoder.encode(otp), Instant.now().plusSeconds(OTP_EXPIRY_MINUTES * 60L));
        try {
            emailVerificationService.sendOtp(normalized.email(), otp);
        } catch (RuntimeException ex) {
            repository.deleteForEmail(normalized.email());
            return ex.getMessage();
        }
        return null;
    }

    @Transactional
    public String verify(String email, String otp) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
        String code = otp == null ? "" : otp.trim();
        RegistrationRepository.PendingRegistration pending = repository.find(normalizedEmail).orElse(null);
        if (pending == null) return "No pending registration was found. Start registration again.";
        if (pending.expiresAt().isBefore(Instant.now())) {
            repository.deleteForEmail(normalizedEmail);
            return "The verification code has expired. Start registration again.";
        }
        if (pending.attempts() >= MAX_ATTEMPTS) {
            repository.deleteForEmail(normalizedEmail);
            return "Too many incorrect attempts. Start registration again.";
        }
        if (!passwordEncoder.matches(code, pending.otpHash())) {
            repository.incrementAttempts(normalizedEmail);
            return "Incorrect verification code.";
        }
        if (studentRepository.existsByRegistrationOrEmail(pending.registrationNo(), pending.email())) {
            repository.deleteForEmail(normalizedEmail);
            return "An account already exists for this registration number or email.";
        }
        Student s = new Student();
        s.setRegistrationNo(pending.registrationNo());
        s.setCollegeEmail(pending.email());
        s.setFullName(pending.fullName());
        s.setDepartment(pending.department());
        s.setProgram(pending.program());
        s.setSection(pending.section());
        s.setBatchStartYear(pending.batchStart());
        s.setBatchEndYear(pending.batchEnd());
        s.setCurrentYear(pending.currentYear());
        s.setCurrentSemester(pending.currentSemester());
        s.setActive(true);
        s.setAccountCreated(true);
        s.setPasswordHash(pending.passwordHash());
        studentRepository.createStudent(s);
        repository.deleteForEmail(normalizedEmail);
        return null;
    }

    public String resend(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase();
        RegistrationRepository.PendingRegistration pending = repository.find(normalized).orElse(null);
        if (pending == null) return "No pending registration was found. Start registration again.";
        if (!rateLimit.allowSend(normalized, "REGISTER"))
            return "Please wait 30 seconds between verification-code requests. A maximum of 10 codes can be sent per hour for this email address.";
        String otp = String.format("%06d", random.nextInt(1_000_000));
        repository.updateOtp(normalized, passwordEncoder.encode(otp), Instant.now().plusSeconds(OTP_EXPIRY_MINUTES * 60L));
        try {
            emailVerificationService.sendOtp(normalized, otp);
        } catch (RuntimeException ex) {
            return ex.getMessage();
        }
        return null;
    }

    private String validate(RegistrationData d) {
        if (d == null || d.registrationNo().isBlank() || d.email().isBlank() || d.password().isBlank()
                || d.confirmPassword().isBlank() || d.fullName().isBlank()) return "All required fields must be filled.";
        String normalizedEmail = StudentLoginId.normalizeEmail(d.email());
        if (!normalizedEmail.endsWith("@srmist.edu.in")) return "Use your official @srmist.edu.in email address.";
        if (!d.password().equals(d.confirmPassword())) return "Passwords do not match.";
        if (d.password().length() < 8) return "Password must contain at least 8 characters.";
        if (d.batchStart() < 2020 || d.batchEnd() < d.batchStart() || d.currentYear() < 1 || d.currentYear() > 4 || d.currentSemester() < 1 || d.currentSemester() > 8)
            return "Enter valid academic details.";
        return null;
    }

    public record RegistrationData(String registrationNo, String email, String password, String confirmPassword,
                                   String fullName, String department, String program, String section,
                                   int batchStart, int batchEnd, int currentYear, int currentSemester,
                                   String passwordHash) {
        public RegistrationData withEmail(String normalizedEmail) { return new RegistrationData(registrationNo, normalizedEmail, password, confirmPassword, fullName, department, program, section, batchStart, batchEnd, currentYear, currentSemester, passwordHash); }
        public RegistrationData normalized(String hash) {
            return new RegistrationData(registrationNo.trim().toUpperCase(), email.trim().toLowerCase(), "", "",
                    fullName.trim(), department == null ? "" : department.trim(), program == null ? "" : program.trim(),
                    section == null ? "" : section.trim(), batchStart, batchEnd, currentYear, currentSemester, hash);
        }
    }
}
