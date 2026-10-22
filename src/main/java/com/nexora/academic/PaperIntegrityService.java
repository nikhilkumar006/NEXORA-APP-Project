package com.nexora.academic;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Tracks metadata-tampering attempts on student-submitted semester papers. */
@Service
public class PaperIntegrityService {
    private final JdbcTemplate jdbc;

    public PaperIntegrityService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean isBanned(String registrationNo) {
        if (registrationNo == null || registrationNo.isBlank() || "STUDENT".equalsIgnoreCase(registrationNo)) return false;
        List<Boolean> rows = jdbc.query("SELECT banned FROM student_paper_integrity WHERE registration_no=?",
                (rs, n) -> rs.getBoolean(1), registrationNo);
        return !rows.isEmpty() && Boolean.TRUE.equals(rows.get(0));
    }

    /** First three confirmed metadata-tampering violations warn; the fourth permanently disables the account. */
    @Transactional
    public ViolationResult recordMetadataViolation(String registrationNo, String reason) {
        if (registrationNo == null || registrationNo.isBlank() || "STUDENT".equalsIgnoreCase(registrationNo)) {
            return new ViolationResult(0, false, "The uploaded paper metadata could not be verified.");
        }
        jdbc.update("INSERT IGNORE INTO student_paper_integrity(registration_no,violation_count,banned) VALUES(?,0,FALSE)", registrationNo);
        List<ViolationState> states = jdbc.query("SELECT violation_count,banned FROM student_paper_integrity WHERE registration_no=? FOR UPDATE",
                (rs, n) -> new ViolationState(rs.getInt(1), rs.getBoolean(2)), registrationNo);
        if (states.isEmpty()) return new ViolationResult(0, false, "The uploaded paper metadata could not be verified.");
        ViolationState state = states.get(0);
        if (state.banned()) return new ViolationResult(state.count(), true, "This account has been permanently disabled for repeated paper-metadata tampering.");
        int next = state.count() + 1;
        boolean banned = next > 3;
        String safeReason = reason == null ? "Paper metadata mismatch" : reason.replaceAll("[\\r\\n]+", " ");
        if (safeReason.length() > 900) safeReason = safeReason.substring(0, 900);
        jdbc.update("UPDATE student_paper_integrity SET violation_count=?,banned=?,last_reason=?,last_violation_at=CURRENT_TIMESTAMP WHERE registration_no=?",
                next, banned, safeReason, registrationNo);
        if (banned) jdbc.update("UPDATE students SET active=FALSE WHERE registration_no=?", registrationNo);
        if (banned) return new ViolationResult(next, true,
                "Account permanently banned after the fourth confirmed paper-metadata tampering attempt. Contact the institution to appeal.");
        return new ViolationResult(next, false,
                "Warning " + next + " of 3: " + safeReason + ". Further metadata tampering is not allowed; the fourth confirmed violation permanently disables the account.");
    }

    private record ViolationState(int count, boolean banned) {}
    public record ViolationResult(int count, boolean banned, String message) {}
}
