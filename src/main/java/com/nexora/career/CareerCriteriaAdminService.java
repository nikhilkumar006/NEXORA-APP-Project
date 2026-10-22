package com.nexora.career;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CareerCriteriaAdminService {
    private static final Set<String> ALLOWED_METRICS = Set.of(
            "CGPA", "tenth_percentile", "twelfth_percentile", "active_arrears", "aptitude",
            "technical_skills", "internships", "projects", "certifications", "hackathons",
            "coding_problems", "leetcode", "hackerrank", "coding_assessment", "competitive_programming",
            "github_repositories", "implemented_projects", "linkedin_updated", "portfolio", "eportfolio",
            "communication", "group_discussion", "presentation", "technical_mock", "hr_mock",
            "resume_level", "professional_behaviour", "placement_training_attendance",
            "placement_assessment_attendance", "resume_verified", "profile_verified", "industry_exposure"
    );

    private static final Set<String> NUMERIC_METRICS = Set.of(
            "CGPA", "tenth_percentile", "twelfth_percentile", "active_arrears", "aptitude",
            "technical_skills", "coding_problems", "leetcode", "hackerrank", "coding_assessment",
            "internships", "projects", "certifications", "hackathons", "competitive_programming",
            "github_repositories", "implemented_projects", "communication", "group_discussion",
            "presentation", "technical_mock", "hr_mock", "placement_training_attendance",
            "placement_assessment_attendance", "industry_exposure"
    );

    private final JdbcTemplate jdbc;

    public CareerCriteriaAdminService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String, Object>> recent() {
        return jdbc.queryForList("""
                SELECT yop, source_version, band, metric, target_value, target_text, mandatory_flag,
                       verified_flag, source_title, source_url, observed_at
                FROM career_criteria ORDER BY observed_at DESC, yop DESC, band, metric LIMIT 150
                """);
    }

    public List<String> availableSourceVersions() {
        java.util.LinkedHashSet<String> versions = new java.util.LinkedHashSet<>();
        versions.add("SRM-PLACEMENT-2026");
        jdbc.query("""
                SELECT DISTINCT source_version FROM career_criteria
                WHERE verified_flag=TRUE AND source_url IS NOT NULL AND source_url<>''
                  AND source_title IS NOT NULL AND source_title<>'' AND observed_at IS NOT NULL
                ORDER BY source_version
                """, (rs, row) -> rs.getString(1)).stream()
                .filter(java.util.Objects::nonNull).map(String::trim)
                .filter(v -> v.matches("SRM-PLACEMENT-[0-9]{4}")).forEach(versions::add);
        return List.copyOf(versions);
    }

    public void save(int yop, String sourceVersion, String band, String metric, Double targetValue,
                     String targetText, boolean mandatory, String sourceTitle, String sourceUrl,
                     LocalDate observedAt, boolean confirmSource) {
        if (yop < 2020 || yop > 2100) throw new IllegalArgumentException("Enter a valid year of passing.");
        String version = clean(sourceVersion, 80);
        if (!version.matches("SRM-PLACEMENT-[0-9]{4}"))
            throw new IllegalArgumentException("Use a source-version ID in the form SRM-PLACEMENT-YYYY and verify it against the cited SRM institutional document.");
        if (!Set.of("Standard", "Dream", "Super Dream", "Marquee").contains(band))
            throw new IllegalArgumentException("Choose a valid package band.");
        String normalizedMetric = metric == null ? "" : metric.trim();
        if (!ALLOWED_METRICS.contains(normalizedMetric)) throw new IllegalArgumentException("Choose a supported criterion metric.");
        String title = clean(sourceTitle, 250);
        String url = clean(sourceUrl, 500);
        if (title.isBlank()) throw new IllegalArgumentException("Source title is required.");
        if (!isHttpUrl(url)) throw new IllegalArgumentException("Source URL must be a valid HTTP or HTTPS URL.");
        if (observedAt == null) throw new IllegalArgumentException("Source date is required.");
        if (observedAt.isAfter(LocalDate.now())) throw new IllegalArgumentException("Source observation date cannot be in the future.");
        if (!confirmSource) throw new IllegalArgumentException("Confirm that you checked this criterion against the cited institutional document.");
        String text = clean(targetText, 500);
        if (NUMERIC_METRICS.contains(normalizedMetric) && targetValue == null)
            throw new IllegalArgumentException("This numeric criterion requires its numeric threshold as well as the source wording.");
        if (targetValue != null && targetValue < 0) throw new IllegalArgumentException("Criterion thresholds cannot be negative.");
        if (targetValue != null && "CGPA".equals(normalizedMetric) && targetValue > 10)
            throw new IllegalArgumentException("CGPA threshold must be on a 0–10 scale.");
        if (targetValue != null && Set.of("tenth_percentile", "twelfth_percentile").contains(normalizedMetric) && targetValue > 100)
            throw new IllegalArgumentException("Class 10 / Class 12 percentage threshold must be on a 0–100 scale.");
        if (targetValue == null && text.isBlank()) throw new IllegalArgumentException("Enter a numeric target or source wording.");
        jdbc.update("""
                INSERT INTO career_criteria
                  (yop,source_version,band,metric,target_value,target_text,mandatory_flag,verified_flag,source_url,source_title,observed_at)
                VALUES(?,?,?,?,?,?,?,TRUE,?,?,?)
                ON DUPLICATE KEY UPDATE target_value=VALUES(target_value), target_text=VALUES(target_text),
                  mandatory_flag=VALUES(mandatory_flag), verified_flag=TRUE, source_url=VALUES(source_url),
                  source_title=VALUES(source_title), observed_at=VALUES(observed_at)
                """, yop, version, band, normalizedMetric, targetValue, text.isBlank() ? null : text,
                mandatory, url, title, observedAt);
    }

    private boolean isHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            return uri.getHost() != null && uri.getUserInfo() == null && scheme != null
                    && (scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"));
        } catch (RuntimeException ex) { return false; }
    }

    private String clean(String value, int max) {
        if (value == null) return "";
        String cleaned = value.trim();
        return cleaned.length() > max ? cleaned.substring(0, max) : cleaned;
    }
}
