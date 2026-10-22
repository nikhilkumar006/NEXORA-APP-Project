package com.nexora.leaderboard;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Leaderboard uses the authoritative XP ledger, not student-entered achievements or profile fields. */
@Service
public class LeaderboardService {
    private final JdbcTemplate jdbc;

    public LeaderboardService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public int pointsFor(String registrationNo) {
        Integer points = jdbc.queryForObject(
                "SELECT COALESCE(SUM(xp),0) FROM student_xp_events WHERE registration_no=?",
                Integer.class, registrationNo);
        return points == null ? 0 : Math.max(0, points);
    }

    public List<LeaderboardEntry> top(int limit) {
        int safeLimit = Math.min(100, Math.max(1, limit));
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT s.registration_no,s.full_name,COALESCE(x.points,0) AS points " +
                "FROM students s LEFT JOIN (" +
                " SELECT registration_no,SUM(xp) AS points FROM student_xp_events GROUP BY registration_no" +
                ") x ON x.registration_no=s.registration_no " +
                "WHERE s.active=TRUE ORDER BY COALESCE(x.points,0) DESC,s.full_name ASC LIMIT " + safeLimit);
        List<LeaderboardEntry> result = new ArrayList<>();
        int rank = 1;
        for (Map<String, Object> row : rows) {
            Object value = row.get("points");
            int points = value instanceof Number number ? Math.max(0, number.intValue()) : 0;
            result.add(new LeaderboardEntry((String) row.get("registration_no"),
                    (String) row.get("full_name"), points, rank++));
        }
        return result;
    }

    public int rankOf(String registrationNo) {
        int myPoints = pointsFor(registrationNo);
        Integer ahead = jdbc.queryForObject(
                "SELECT COUNT(*) FROM students s WHERE s.active=TRUE AND " +
                "(SELECT COALESCE(SUM(e.xp),0) FROM student_xp_events e WHERE e.registration_no=s.registration_no)>?",
                Integer.class, myPoints);
        return (ahead == null ? 0 : ahead) + 1;
    }
}
