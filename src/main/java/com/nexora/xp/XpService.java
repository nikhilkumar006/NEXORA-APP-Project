package com.nexora.xp;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** XP ledger and manually reviewed UPI reward claims for approved paper contributions. */
@Service
public class XpService {
    public static final int REWARD_THRESHOLD_XP = 50;
    public static final int REWARD_AMOUNT_RUPEES = 25;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;

    public XpService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Total XP displayed on My XP; new event types can share this ledger. */
    public int total(String registrationNo) {
        Integer n = jdbc.queryForObject("SELECT COALESCE(SUM(xp),0) FROM student_xp_events WHERE registration_no=?", Integer.class, registrationNo);
        return n == null ? 0 : n;
    }

    /** Only approved unique paper contributions count toward the manual contributor reward. */
    public int contributionXp(String registrationNo) {
        Integer n = jdbc.queryForObject("SELECT COALESCE(SUM(xp),0) FROM student_xp_events WHERE registration_no=? AND event_type='ACADEMIC_UPLOAD_APPROVED'", Integer.class, registrationNo);
        return n == null ? 0 : n;
    }

    public int availableContributionXp(String registrationNo) {
        Integer reserved = jdbc.queryForObject("SELECT COALESCE(SUM(points_cost),0) FROM reward_redemptions WHERE registration_no=? AND status IN ('REQUESTED','PAID')", Integer.class, registrationNo);
        return Math.max(0, contributionXp(registrationNo) - (reserved == null ? 0 : reserved));
    }

    public List<XpEvent> recentEvents(String registrationNo, int limit) {
        int safeLimit = Math.min(100, Math.max(1, limit));
        return jdbc.query("SELECT id,event_type,xp,description,created_at FROM student_xp_events WHERE registration_no=? ORDER BY created_at DESC,id DESC LIMIT " + safeLimit,
                (rs, row) -> new XpEvent(rs.getLong("id"), rs.getString("event_type"), rs.getInt("xp"),
                        rs.getString("description"), Optional.ofNullable(rs.getTimestamp("created_at")).map(Timestamp::toLocalDateTime).orElse(null)), registrationNo);
    }

    public List<RewardRedemption> redemptions(String registrationNo) {
        return jdbc.query("SELECT id,reward_code,reward_name,points_cost,payout_amount,upi_id,status,requested_at,reviewed_at,payment_reference,admin_note " +
                        "FROM reward_redemptions WHERE registration_no=? ORDER BY requested_at DESC,id DESC",
                (rs, row) -> new RewardRedemption(rs.getLong("id"), rs.getString("reward_code"), rs.getString("reward_name"),
                        rs.getInt("points_cost"), rs.getBigDecimal("payout_amount"), rs.getString("upi_id"), rs.getString("status"),
                        Optional.ofNullable(rs.getTimestamp("requested_at")).map(Timestamp::toLocalDateTime).orElse(null),
                        Optional.ofNullable(rs.getTimestamp("reviewed_at")).map(Timestamp::toLocalDateTime).orElse(null),
                        rs.getString("payment_reference"), rs.getString("admin_note")), registrationNo);
    }

    @Transactional
    public RewardRedemption createRedemption(String registrationNo, String upiId) {
        if (registrationNo == null || registrationNo.isBlank()) throw new IllegalArgumentException("Your student account could not be identified.");
        String upi = upiId == null ? "" : upiId.trim();
        if (upi.length() < 5 || upi.length() > 255 || !upi.matches("(?i)^[a-z0-9][a-z0-9._-]{0,100}@[a-z0-9][a-z0-9.-]{1,100}$")) {
            throw new IllegalArgumentException("Enter a valid UPI ID, such as name@bank.");
        }
        // Serialize balance checks per student so two simultaneous requests cannot reserve the same XP.
        List<String> lockedRows = jdbc.query("SELECT registration_no FROM students WHERE registration_no=? FOR UPDATE",
                (rs, row) -> rs.getString(1), registrationNo);
        if (lockedRows.isEmpty()) throw new IllegalArgumentException("Your active student record could not be found.");
        int available = availableContributionXp(registrationNo);
        if (available < REWARD_THRESHOLD_XP) throw new IllegalArgumentException("You need at least " + REWARD_THRESHOLD_XP + " available contribution XP to request this reward.");

        String code = createUniqueCode();
        jdbc.update("INSERT INTO reward_redemptions(registration_no,reward_code,reward_name,points_cost,payout_amount,upi_id,status) VALUES(?,?,?,?,?,?, 'REQUESTED')",
                registrationNo, code, "Contributor Reward — UPI transfer", REWARD_THRESHOLD_XP, REWARD_AMOUNT_RUPEES, upi);
        return jdbc.queryForObject("SELECT id,reward_code,reward_name,points_cost,payout_amount,upi_id,status,requested_at,reviewed_at,payment_reference,admin_note " +
                        "FROM reward_redemptions WHERE reward_code=?", (rs, row) -> new RewardRedemption(rs.getLong("id"), rs.getString("reward_code"),
                        rs.getString("reward_name"), rs.getInt("points_cost"), rs.getBigDecimal("payout_amount"), rs.getString("upi_id"),
                        rs.getString("status"), Optional.ofNullable(rs.getTimestamp("requested_at")).map(Timestamp::toLocalDateTime).orElse(null),
                        Optional.ofNullable(rs.getTimestamp("reviewed_at")).map(Timestamp::toLocalDateTime).orElse(null),
                        rs.getString("payment_reference"), rs.getString("admin_note")), code);
    }

    public List<RewardRedemption> pendingRedemptions() {
        return jdbc.query("SELECT id,reward_code,reward_name,points_cost,payout_amount,upi_id,status,requested_at,reviewed_at,payment_reference,admin_note,registration_no " +
                        "FROM reward_redemptions WHERE status='REQUESTED' ORDER BY requested_at ASC,id ASC",
                (rs, row) -> new RewardRedemption(rs.getLong("id"), rs.getString("reward_code"), rs.getString("reward_name"),
                        rs.getInt("points_cost"), rs.getBigDecimal("payout_amount"), rs.getString("upi_id"), rs.getString("status"),
                        Optional.ofNullable(rs.getTimestamp("requested_at")).map(Timestamp::toLocalDateTime).orElse(null),
                        Optional.ofNullable(rs.getTimestamp("reviewed_at")).map(Timestamp::toLocalDateTime).orElse(null),
                        rs.getString("payment_reference"), rs.getString("admin_note"), rs.getString("registration_no")));
    }

    @Transactional
    public void markPaid(long id, String code, String paymentReference, String reviewedBy) {
        String cleanCode = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        String reference = paymentReference == null ? "" : paymentReference.trim();
        if (cleanCode.isBlank() || reference.isBlank() || reference.length() > 120) throw new IllegalArgumentException("A matching one-time redemption code and UPI transaction reference are required.");
        Integer usedReference = jdbc.queryForObject("SELECT COUNT(*) FROM reward_redemptions WHERE payment_reference=? AND id<>?", Integer.class, reference, id);
        if (usedReference != null && usedReference > 0) throw new IllegalArgumentException("That UPI transaction reference is already recorded against another reward claim.");
        Integer changed = jdbc.update("UPDATE reward_redemptions SET status='PAID',payment_reference=?,reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP,paid_at=CURRENT_TIMESTAMP " +
                "WHERE id=? AND reward_code=? AND status='REQUESTED'", reference, reviewedBy, id, cleanCode);
        if (changed == 0) throw new IllegalArgumentException("The code does not match a pending claim, or the code has already been used.");
    }

    @Transactional
    public void reject(long id, String note, String reviewedBy) {
        String cleanNote = note == null ? "Rejected by administrator" : note.trim();
        if (cleanNote.length() > 1000) cleanNote = cleanNote.substring(0, 1000);
        int changed = jdbc.update("UPDATE reward_redemptions SET status='REJECTED',admin_note=?,reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP WHERE id=? AND status='REQUESTED'",
                cleanNote, reviewedBy, id);
        if (changed == 0) throw new IllegalArgumentException("This claim is not pending or has already been reviewed.");
    }

    public void awardUploadApproval(String registrationNo, long resourceId, int xp, String title) {
        if (registrationNo == null || registrationNo.isBlank() || resourceId <= 0 || xp <= 0) return;
        jdbc.update("INSERT IGNORE INTO student_xp_events(registration_no,event_type,reference_id,xp,description) VALUES(?,?,?,?,?)",
                registrationNo, "ACADEMIC_UPLOAD_APPROVED", resourceId, xp, title == null ? "Approved academic contribution" : title.substring(0, Math.min(500, title.length())));
    }

    /** Award 5 XP for the first server-graded passing attempt (>=75%) for a chapter. */
    @Transactional
    public boolean awardChapterTestPass(String registrationNo, String subjectCode, int chapterIndex, long attemptId, java.math.BigDecimal scorePercent) {
        if (registrationNo == null || registrationNo.isBlank() || subjectCode == null || subjectCode.isBlank()
                || chapterIndex < 1 || attemptId <= 0 || scorePercent == null || scorePercent.compareTo(java.math.BigDecimal.valueOf(75)) < 0) return false;
        int inserted = jdbc.update("INSERT IGNORE INTO student_chapter_test_rewards(registration_no,subject_code,chapter_index,attempt_id,score_percent) VALUES(?,?,?,?,?)",
                registrationNo, subjectCode, chapterIndex, attemptId, scorePercent);
        if (inserted != 1) return false;
        jdbc.update("INSERT IGNORE INTO student_xp_events(registration_no,event_type,reference_id,xp,description) VALUES(?,?,?,?,?)",
                registrationNo, "CHAPTER_TEST_PASSED", attemptId, 5,
                "Full chapter test passed with " + scorePercent.stripTrailingZeros().toPlainString() + "% — " + subjectCode + " chapter " + chapterIndex);
        return true;
    }

    private String createUniqueCode() {
        byte[] bytes = new byte[8];
        String code;
        Integer count;
        do {
            RANDOM.nextBytes(bytes);
            code = "NX-RW-" + HexFormat.of().withUpperCase().formatHex(bytes);
            count = jdbc.queryForObject("SELECT COUNT(*) FROM reward_redemptions WHERE reward_code=?", Integer.class, code);
        } while (count != null && count > 0);
        return code;
    }

    public record XpEvent(long id, String eventType, int xp, String description, LocalDateTime createdAt) {}
    public record RewardRedemption(long id, String rewardCode, String rewardName, int pointsCost,
                                   java.math.BigDecimal payoutAmount, String upiId, String status,
                                   LocalDateTime requestedAt, LocalDateTime reviewedAt, String paymentReference,
                                   String adminNote, String registrationNo) {
        public RewardRedemption(long id, String rewardCode, String rewardName, int pointsCost,
                                java.math.BigDecimal payoutAmount, String upiId, String status,
                                LocalDateTime requestedAt, LocalDateTime reviewedAt, String paymentReference,
                                String adminNote) {
            this(id, rewardCode, rewardName, pointsCost, payoutAmount, upiId, status, requestedAt, reviewedAt, paymentReference, adminNote, null);
        }
    }
}
