package com.nexora.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/** Database-backed OTP send throttle. Keeps resend usable while preventing email-abuse loops. */
@Service
public class OtpRateLimitService {
    private static final long COOLDOWN_SECONDS = 30;
    private static final long WINDOW_SECONDS = 3600;
    private static final int MAX_SENDS_PER_HOUR = 10;

    private final JdbcTemplate jdbc;

    public OtpRateLimitService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public synchronized boolean allowSend(String identifier, String action) {
        String normalized = identifier == null ? "" : identifier.trim().toLowerCase(Locale.ROOT);
        String kind = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank() || normalized.length() > 255 || !List.of("LOGIN", "REGISTER").contains(kind)) return false;

        List<RateState> rows = jdbc.query("""
                SELECT window_started_at, last_sent_at, send_count
                FROM otp_rate_limits WHERE identifier=? AND action_type=? FOR UPDATE
                """, (rs, row) -> new RateState(rs.getTimestamp(1).toInstant(),
                rs.getTimestamp(2).toInstant(), rs.getInt(3)), normalized, kind);
        Instant now = Instant.now();
        if (rows.isEmpty()) {
            jdbc.update("INSERT INTO otp_rate_limits(identifier,action_type,window_started_at,last_sent_at,send_count) VALUES(?,?,?,?,1)",
                    normalized, kind, Timestamp.from(now), Timestamp.from(now));
            return true;
        }

        RateState state = rows.get(0);
        if (state.lastSentAt().plusSeconds(COOLDOWN_SECONDS).isAfter(now)) return false;
        boolean resetWindow = state.windowStartedAt().plusSeconds(WINDOW_SECONDS).isBefore(now);
        int count = resetWindow ? 0 : state.sendCount();
        if (count >= MAX_SENDS_PER_HOUR) return false;
        jdbc.update("UPDATE otp_rate_limits SET window_started_at=?, last_sent_at=?, send_count=? WHERE identifier=? AND action_type=?",
                Timestamp.from(resetWindow ? now : state.windowStartedAt()), Timestamp.from(now), count + 1, normalized, kind);
        return true;
    }

    private record RateState(Instant windowStartedAt, Instant lastSentAt, int sendCount) {}
}
