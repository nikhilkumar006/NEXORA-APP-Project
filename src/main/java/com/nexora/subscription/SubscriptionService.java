package com.nexora.subscription;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class SubscriptionService {
    private final JdbcTemplate jdbc;
    private final Path screenshotRoot;
    /** Local-only switch for reviewing Premium features without payment. Disabled by default. */
    @Value("${nexora.premium-review-access.enabled:false}")
    private boolean premiumReviewAccessEnabled;
    /** User-facing subscription catalogue: Free and Premium only. */
    private final List<SubscriptionPlan> plans = List.of(
            new SubscriptionPlan("FREE", "Free", 0, 0, "Core NEXORA academic and career access"),
            new SubscriptionPlan("PREMIUM", "Premium", 49, 89, "Full NEXORA academic and career intelligence")
    );

    public SubscriptionService(JdbcTemplate jdbc,
                                @Value("${nexora.subscription.screenshot-dir:./data/subscription-payments}") String screenshotDir) {
        this.jdbc = jdbc;
        this.screenshotRoot = Path.of(screenshotDir).toAbsolutePath().normalize();
    }

    public List<SubscriptionPlan> plans() { return plans; }

    public boolean hasPlanAtLeast(String registrationNo, String requiredPlanCode) {
        // In the explicitly selected local review profile, show Premium-labelled features as usable.
        // This is disabled by default and must not be enabled on a public deployment.
        if (premiumReviewAccessEnabled && "PREMIUM".equalsIgnoreCase(requiredPlanCode)) return true;
        Map<String, Object> active = current(registrationNo);
        String currentCode = String.valueOf(active.getOrDefault("plan_code", "FREE")).toUpperCase(Locale.ROOT);
        String requiredCode = requiredPlanCode == null ? "PREMIUM" : requiredPlanCode.toUpperCase(Locale.ROOT);
        int currentLevel = planLevel(currentCode);
        int requiredLevel = planLevel(requiredCode);
        return requiredLevel >= 0 && currentLevel >= requiredLevel;
    }

    private int planLevel(String code) {
        return switch (code) { case "FREE" -> 0; case "BASIC" -> 1; case "PREMIUM" -> 2; default -> -1; };
    }

    public Map<String,Object> current(String reg) {
        try {
            List<Map<String,Object>> rows = jdbc.queryForList("""
                SELECT * FROM student_subscriptions
                WHERE registration_no=? AND status='ACTIVE' AND (expires_at IS NULL OR expires_at>CURRENT_TIMESTAMP)
                  AND (plan_code='FREE' OR payment_status='APPROVED')
                ORDER BY id DESC LIMIT 1
                """, reg);
            if (!rows.isEmpty()) return rows.get(0);
        } catch (Exception ignored) {
            // A first-run database may not yet have the optional subscription table.
            // The UI remains usable and shows Free until schema initialization is complete.
        }
        Map<String,Object> free = new LinkedHashMap<>();
        free.put("plan_code", "FREE"); free.put("plan_name", "Free"); free.put("billing_cycle", "NONE");
        free.put("price", 0); free.put("status", "ACTIVE"); free.put("payment_status", "FREE");
        return free;
    }

    public List<Map<String,Object>> pendingRequests() {
        return jdbc.queryForList("SELECT * FROM subscription_requests WHERE status='PENDING' ORDER BY id DESC");
    }

    public void createPaymentRequest(String reg, String studentName, String email, String plan, String cycle,
                                     String transactionId, String payerUpiId, LocalDate date, LocalTime time,
                                     MultipartFile screenshot, String note) throws IOException {
        SubscriptionPlan p = plan(plan);
        if (p.code().equals("FREE")) throw new IllegalArgumentException("Free plan does not require payment.");
        if (!"SEMESTER".equalsIgnoreCase(cycle) && !"ANNUAL".equalsIgnoreCase(cycle)) throw new IllegalArgumentException("Choose Semester or Yearly billing.");
        if (transactionId == null || transactionId.isBlank()) throw new IllegalArgumentException("Transaction ID / UTR is required.");
        if (date == null || time == null) throw new IllegalArgumentException("Payment date and time are required.");
        String normalizedTransactionId = transactionId.trim().replaceAll("\\s+", "");
        if (normalizedTransactionId.length() < 6 || normalizedTransactionId.length() > 120)
            throw new IllegalArgumentException("Enter a valid transaction reference between 6 and 120 characters.");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM subscription_requests WHERE LOWER(transaction_id)=LOWER(?)", Integer.class, normalizedTransactionId) > 0)
            throw new IllegalArgumentException("That transaction ID has already been submitted.");

        String screenshotPath = null;
        if (screenshot != null && !screenshot.isEmpty()) {
            if (screenshot.getSize() > 8L * 1024 * 1024) throw new IllegalArgumentException("Payment screenshot must be 8 MB or smaller.");
            String extension = extension(screenshot.getOriginalFilename()).toLowerCase(Locale.ROOT);
            if (!Set.of("png", "jpg", "jpeg", "webp").contains(extension))
                throw new IllegalArgumentException("Payment evidence must be a PNG, JPG, JPEG or WEBP image.");
            String contentType = Objects.toString(screenshot.getContentType(), "").toLowerCase(Locale.ROOT);
            if (!contentType.isBlank() && !Set.of("image/png", "image/jpeg", "image/webp").contains(contentType))
                throw new IllegalArgumentException("The uploaded payment evidence is not a supported image type.");
            Files.createDirectories(screenshotRoot);
            String safe = UUID.randomUUID() + "-payment." + extension;
            Path target = screenshotRoot.resolve(safe).normalize();
            if (!target.startsWith(screenshotRoot)) throw new IllegalArgumentException("Invalid screenshot path.");
            try (var input = screenshot.getInputStream()) {
                Files.copy(input, target);
            }
            screenshotPath = target.toString();
        }
        jdbc.update("""
            INSERT INTO subscription_requests
            (registration_no,student_name,student_email,plan_code,plan_name,billing_cycle,amount,transaction_id,payer_upi_id,payment_date,payment_time,payment_screenshot_path,note,status)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?, 'PENDING')
            """, reg, studentName, email, p.code(), p.name(), cycle.toUpperCase(), p.price(cycle), normalizedTransactionId, payerUpiId, date, time, screenshotPath, note);
    }

    @Transactional
    public void approveRequest(long id, String admin) {
        Map<String,Object> r = requestForUpdate(id);
        if (r == null || !"PENDING".equalsIgnoreCase(String.valueOf(r.get("status")))) return;
        String planCode = String.valueOf(r.get("plan_code"));
        String cycle = String.valueOf(r.get("billing_cycle"));
        SubscriptionPlan p = plan(planCode);
        String reg = String.valueOf(r.get("registration_no"));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expires = "SEMESTER".equalsIgnoreCase(cycle) ? now.plusMonths(6) : now.plusYears(1);
        jdbc.update("""
            INSERT INTO student_subscriptions(registration_no,plan_code,plan_name,price,billing_cycle,status,payment_status,started_at,expires_at)
            VALUES(?,?,?,?,?,'ACTIVE','APPROVED',CURRENT_TIMESTAMP,?)
            ON DUPLICATE KEY UPDATE plan_code=VALUES(plan_code),plan_name=VALUES(plan_name),price=VALUES(price),billing_cycle=VALUES(billing_cycle),status='ACTIVE',payment_status='APPROVED',started_at=CURRENT_TIMESTAMP,expires_at=VALUES(expires_at)
            """, reg, p.code(), p.name(), p.price(cycle), cycle.toUpperCase(), java.sql.Timestamp.valueOf(expires));
        int changed = jdbc.update("UPDATE subscription_requests SET status='APPROVED',reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP WHERE id=? AND status='PENDING'", admin, id);
        if (changed != 1) throw new IllegalStateException("Payment request was already reviewed; no duplicate approval was applied.");
    }

    public void rejectRequest(long id, String admin) {
        jdbc.update("UPDATE subscription_requests SET status='REJECTED',reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP WHERE id=? AND status='PENDING'", admin, id);
    }

    public Path paymentScreenshot(long id) {
        Map<String,Object> r = request(id);
        if (r == null || r.get("payment_screenshot_path") == null) return null;
        Path p = Path.of(String.valueOf(r.get("payment_screenshot_path"))).toAbsolutePath().normalize();
        return p.startsWith(screenshotRoot) && Files.isRegularFile(p) ? p : null;
    }

    private Map<String,Object> request(long id) {
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM subscription_requests WHERE id=?", id);
        return rows.isEmpty()?null:rows.get(0);
    }
    private Map<String,Object> requestForUpdate(long id) {
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM subscription_requests WHERE id=? FOR UPDATE", id);
        return rows.isEmpty()?null:rows.get(0);
    }
    private SubscriptionPlan plan(String code) { return plans.stream().filter(p->p.code().equalsIgnoreCase(code)).findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown subscription plan.")); }
    private String extension(String name) { String n=name==null?"png":name; int i=n.lastIndexOf('.'); return i>0?n.substring(i+1).replaceAll("[^A-Za-z0-9]",""):"png"; }
}
