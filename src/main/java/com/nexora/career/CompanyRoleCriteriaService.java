package com.nexora.career;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Source-provenanced, exact company + canonical role + hiring programme criteria. */
@Service
public class CompanyRoleCriteriaService {
    private static final Set<String> METRICS = Set.of(
            "class_10_marks", "class_12_marks", "cgpa", "degree_branch", "graduation_year",
            "backlogs", "experience", "required_skills", "preferred_skills", "certifications",
            "work_location", "other"
    );
    private static final Set<String> NUMERIC_METRICS = Set.of("class_10_marks", "class_12_marks", "cgpa", "graduation_year", "backlogs");
    private static final String NOT_SPECIFIED = "Not specified";
    private final JdbcTemplate jdbc;

    public CompanyRoleCriteriaService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<CompanyRoleCriterion> findVerified(String company, String role, String programme, int yop) {
        if (blank(company) || blank(role) || blank(programme)) return List.of();
        return jdbc.query("""
                SELECT company_name,role_title,hiring_program,yop,metric,target_value,target_text,mandatory_flag,verified_flag,
                       source_title,source_url,observed_at
                FROM company_role_criteria
                WHERE LOWER(TRIM(company_name))=LOWER(TRIM(?))
                  AND LOWER(TRIM(role_title))=LOWER(TRIM(?))
                  AND LOWER(TRIM(hiring_program))=LOWER(TRIM(?))
                  AND yop=? AND verified_flag=TRUE
                  AND source_title IS NOT NULL AND source_title<>''
                  AND source_url IS NOT NULL AND source_url<>'' AND observed_at IS NOT NULL
                ORDER BY mandatory_flag DESC,metric
                """, (rs, row) -> new CompanyRoleCriterion(
                rs.getString("company_name"), rs.getString("role_title"), rs.getString("hiring_program"),
                rs.getInt("yop"), rs.getString("metric"), rs.getObject("target_value", Double.class),
                rs.getString("target_text"), rs.getBoolean("mandatory_flag"), rs.getBoolean("verified_flag"), rs.getString("source_title"),
                rs.getString("source_url"), rs.getDate("observed_at").toLocalDate()), company, role, programme, yop);
    }

    /** Programme choices only come from verified records for the exact employer/role/YOP. */
    public List<String> programs(String company, String role, int yop) {
        if (blank(company) || blank(role)) return List.of(NOT_SPECIFIED);
        List<String> programs = jdbc.query("""
                SELECT DISTINCT hiring_program FROM company_role_criteria
                WHERE LOWER(TRIM(company_name))=LOWER(TRIM(?))
                  AND LOWER(TRIM(role_title))=LOWER(TRIM(?)) AND yop=? AND verified_flag=TRUE
                  AND source_title IS NOT NULL AND source_title<>''
                  AND source_url IS NOT NULL AND source_url<>'' AND observed_at IS NOT NULL
                ORDER BY hiring_program
                """, (rs, row) -> rs.getString(1), company, role, yop);
        if (!programs.contains(NOT_SPECIFIED)) programs.add(0, NOT_SPECIFIED);
        return List.copyOf(programs);
    }

    /** Evidence records for a company profile; only explicitly verified records are exposed. */
    public List<CompanyRoleCriterion> findVerifiedForCompany(String company, List<String> aliases) {
        if (blank(company)) return List.of();
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        names.add(company.trim());
        if (aliases != null) aliases.stream().filter(v -> !blank(v)).map(String::trim).forEach(names::add);
        String placeholders = String.join(",", java.util.Collections.nCopies(names.size(), "?"));
        String sql = """
                SELECT company_name,role_title,hiring_program,yop,metric,target_value,target_text,mandatory_flag,verified_flag,
                       source_title,source_url,observed_at
                FROM company_role_criteria
                WHERE LOWER(TRIM(company_name)) IN (%s) AND verified_flag=TRUE
                  AND source_title IS NOT NULL AND source_title<>''
                  AND source_url IS NOT NULL AND source_url<>'' AND observed_at IS NOT NULL
                ORDER BY observed_at DESC, role_title, hiring_program, metric LIMIT 200
                """.formatted(placeholders);
        Object[] args = names.stream().map(v -> v.toLowerCase(Locale.ROOT)).toArray();
        return jdbc.query(sql, (rs, row) -> mapCriterion(rs), args);
    }

    /** Exact role plus curated aliases; these rows are eligibility evidence, not vacancy claims. */
    public List<CompanyRoleCriterion> findVerifiedForRole(String role) {
        return findVerifiedForRoleTitles(role == null ? List.of() : List.of(role));
    }

    public List<CompanyRoleCriterion> findVerifiedForRoleTitles(String canonicalRole, List<String> aliases) {
        java.util.LinkedHashSet<String> titles = new java.util.LinkedHashSet<>();
        if (!blank(canonicalRole)) titles.add(canonicalRole.trim());
        if (aliases != null) aliases.stream().filter(v -> !blank(v)).map(String::trim).forEach(titles::add);
        return findVerifiedForRoleTitles(new java.util.ArrayList<>(titles));
    }

    private List<CompanyRoleCriterion> findVerifiedForRoleTitles(List<String> roleTitles) {
        if (roleTitles == null || roleTitles.isEmpty()) return List.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(roleTitles.size(), "?"));
        String sql = """
                SELECT company_name,role_title,hiring_program,yop,metric,target_value,target_text,mandatory_flag,verified_flag,
                       source_title,source_url,observed_at
                FROM company_role_criteria
                WHERE LOWER(TRIM(role_title)) IN (%s) AND verified_flag=TRUE
                  AND source_title IS NOT NULL AND source_title<>''
                  AND source_url IS NOT NULL AND source_url<>'' AND observed_at IS NOT NULL
                ORDER BY observed_at DESC, company_name, hiring_program, metric LIMIT 100
                """.formatted(placeholders);
        Object[] args = roleTitles.stream().map(v -> v.toLowerCase(Locale.ROOT)).toArray();
        return jdbc.query(sql, (rs, row) -> mapCriterion(rs), args);
    }

    public List<CompanyRoleCriterion> recent() {
        return jdbc.query("""
                SELECT company_name,role_title,hiring_program,yop,metric,target_value,target_text,mandatory_flag,
                       verified_flag,source_title,source_url,observed_at
                FROM company_role_criteria ORDER BY verified_flag DESC,observed_at DESC,yop DESC,company_name,role_title LIMIT 300
                """, (rs, row) -> mapCriterion(rs));
    }

    private CompanyRoleCriterion mapCriterion(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new CompanyRoleCriterion(rs.getString("company_name"), rs.getString("role_title"),
                rs.getString("hiring_program"), rs.getInt("yop"), rs.getString("metric"),
                rs.getObject("target_value", Double.class), rs.getString("target_text"),
                rs.getBoolean("mandatory_flag"), rs.getBoolean("verified_flag"),
                rs.getString("source_title"), rs.getString("source_url"),
                rs.getDate("observed_at") == null ? null : rs.getDate("observed_at").toLocalDate());
    }

    public void save(String company, String role, String program, int yop, String metric,
                     Double targetValue, String targetText, boolean mandatory,
                     String sourceTitle, String sourceUrl, LocalDate observedAt, boolean confirmSource) {
        String cleanCompany = clean(company, 180);
        String cleanRole = clean(role, 180);
        String cleanProgram = clean(program, 180);
        String cleanMetric = metric == null ? "" : metric.trim().toLowerCase(Locale.ROOT);
        String cleanTitle = clean(sourceTitle, 250);
        String cleanUrl = clean(sourceUrl, 500);
        String cleanText = clean(targetText, 500);
        if (cleanCompany.isBlank() || cleanRole.isBlank() || cleanProgram.isBlank())
            throw new IllegalArgumentException("Company, canonical role and hiring programme are required.");
        if (yop < 2020 || yop > 2100) throw new IllegalArgumentException("Enter a valid year of passing.");
        if (!METRICS.contains(cleanMetric)) throw new IllegalArgumentException("Choose a supported eligibility criterion.");
        if (NUMERIC_METRICS.contains(cleanMetric) && targetValue == null)
            throw new IllegalArgumentException("This criterion requires its numeric threshold in addition to the source wording.");
        if (targetValue == null && cleanText.isBlank()) throw new IllegalArgumentException("Enter a numeric threshold or the exact criterion wording.");
        if (targetValue != null && targetValue < 0) throw new IllegalArgumentException("Criterion thresholds cannot be negative.");
        if (cleanTitle.isBlank()) throw new IllegalArgumentException("Source title is required.");
        if (!isHttpUrl(cleanUrl)) throw new IllegalArgumentException("Source URL must be a valid HTTP or HTTPS URL.");
        if (observedAt == null || observedAt.isAfter(LocalDate.now())) throw new IllegalArgumentException("Enter a source observation date that is not in the future.");
        if (!confirmSource) throw new IllegalArgumentException("Confirm that you checked this exact company-role-programme criterion against the source.");
        jdbc.update("""
                INSERT INTO company_role_criteria
                  (company_name,role_title,hiring_program,yop,metric,target_value,target_text,mandatory_flag,
                   verified_flag,source_title,source_url,observed_at)
                VALUES(?,?,?,?,?,?,?,?,TRUE,?,?,?)
                ON DUPLICATE KEY UPDATE target_value=VALUES(target_value),target_text=VALUES(target_text),
                  mandatory_flag=VALUES(mandatory_flag),verified_flag=TRUE,source_title=VALUES(source_title),
                  source_url=VALUES(source_url),observed_at=VALUES(observed_at)
                """, cleanCompany, cleanRole, cleanProgram, yop, cleanMetric, targetValue,
                cleanText.isBlank() ? null : cleanText, mandatory, cleanTitle, cleanUrl, observedAt);
    }

    public String notSpecifiedLabel() { return NOT_SPECIFIED; }

    private boolean isHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            return uri.getHost() != null && uri.getUserInfo() == null && scheme != null
                    && (scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"));
        } catch (RuntimeException ex) { return false; }
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String clean(String value, int max) {
        if (value == null) return "";
        String text = value.trim();
        return text.length() > max ? text.substring(0, max) : text;
    }

    public record CompanyRoleCriterion(String companyName, String roleTitle, String hiringProgram, int yop,
                                       String metric, Double targetValue, String targetText, boolean mandatory,
                                       boolean verified, String sourceTitle, String sourceUrl, LocalDate observedAt) {
        public String displayTarget() {
            if (targetText != null && !targetText.isBlank()) return targetValue == null ? targetText : targetValue + " · " + targetText;
            return targetValue == null ? "—" : String.valueOf(targetValue);
        }
        public String displayMetric() {
            return switch (metric == null ? "" : metric.toLowerCase(Locale.ROOT)) {
                case "class_10_marks" -> "Class 10 marks";
                case "class_12_marks" -> "Class 12 marks";
                case "cgpa" -> "CGPA / GPA";
                case "degree_branch" -> "Degree / branch";
                case "graduation_year" -> "Year of passing";
                case "backlogs" -> "Backlogs / arrears";
                case "experience" -> "Experience";
                case "required_skills" -> "Required skills";
                case "preferred_skills" -> "Preferred skills";
                case "certifications" -> "Certifications";
                case "work_location" -> "Work location";
                default -> "Other stated criterion";
            };
        }
    }
}
