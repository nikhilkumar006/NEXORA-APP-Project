package com.nexora.career;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import com.nexora.career.market.MarketRole;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class CareerGpsService {
    private final JdbcTemplate jdbc;

    public CareerGpsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<CareerGpsSelection> find(String registrationNo) {
        return jdbc.query("SELECT registration_no, primary_path, target_company, target_program, target_role, target_band, source_version " +
                        "FROM career_gps_selections WHERE registration_no = ?",
                (rs, row) -> new CareerGpsSelection(
                        rs.getString("registration_no"), rs.getString("primary_path"),
                        rs.getString("target_company"), rs.getString("target_program"), rs.getString("target_role"),
                        rs.getString("target_band"), rs.getString("source_version")), registrationNo)
                .stream().findFirst();
    }

    /** Compares a selected role's published core-skill list with skills the student recorded in Development. */
    public RoleReadiness roleReadiness(String registrationNo, MarketRole role) {
        return roleReadiness(registrationNo, role == null ? List.of() : role.coreSkills());
    }

    public RoleReadiness roleReadiness(String registrationNo, List<String> requiredSkills) {
        List<SavedSkill> savedSkills = jdbc.query(
                "SELECT skill_name, skill_level, status FROM student_skills WHERE registration_no=? ORDER BY id DESC",
                (rs, row) -> new SavedSkill(rs.getString("skill_name"), rs.getString("skill_level"), rs.getString("status")),
                registrationNo);
        List<RoleSkillStatus> statuses = new ArrayList<>();
        int covered = 0;
        int completed = 0;
        for (String required : requiredSkills) {
            SavedSkill match = savedSkills.stream().filter(skill -> matchesSkill(required, skill.name())).findFirst().orElse(null);
            String status = match == null ? "Not recorded" : safeStatus(match.status());
            if (match != null) covered++;
            if (match != null && "completed".equalsIgnoreCase(match.status())) completed++;
            // Keep the level expression explicit: an absent skill must never dereference a null match.
            String skillLevel = match == null ? "—" : safeLevel(match.level());
            statuses.add(new RoleSkillStatus(required, status, skillLevel, match != null));
        }
        Integer projectCountValue = jdbc.queryForObject("SELECT COUNT(*) FROM student_projects WHERE registration_no=?", Integer.class, registrationNo);
        Integer completedProjectsValue = jdbc.queryForObject("SELECT COUNT(*) FROM student_projects WHERE registration_no=? AND LOWER(status)='completed'", Integer.class, registrationNo);
        int projectCount = projectCountValue == null ? 0 : projectCountValue;
        int completedProjects = completedProjectsValue == null ? 0 : completedProjectsValue;
        int percent = statuses.isEmpty() ? 0 : (int) Math.round(completed * 100.0 / statuses.size());
        return new RoleReadiness(statuses, covered, completed, statuses.size(), percent, projectCount, completedProjects);
    }

    private boolean matchesSkill(String required, String candidate) {
        if (required == null || candidate == null) return false;
        String normalCandidate = normalise(candidate);
        if (normalCandidate.isBlank()) return false;
        // Slash-separated skills are alternatives where the source combines related tools.
        for (String part : required.split("/")) {
            String normalRequired = normalise(part);
            if (normalRequired.isBlank()) continue;
            if (normalRequired.equals(normalCandidate)) return true;
            if (normalRequired.length() >= 5 && normalCandidate.length() >= 5 &&
                    (normalCandidate.contains(normalRequired) || normalRequired.contains(normalCandidate))) return true;
        }
        return false;
    }

    private String normalise(String value) { return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""); }
    private String safeStatus(String value) {
        if (value == null || value.isBlank()) return "Recorded";
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
        return switch (normalized) {
            case "completed", "complete", "done" -> "Completed";
            case "in progress", "learning", "started" -> "In Progress";
            case "planned", "not started", "pending" -> "Planned";
            default -> value.trim();
        };
    }
    private String safeLevel(String value) { return value == null || value.isBlank() ? "Not specified" : value; }
    private record SavedSkill(String name, String level, String status) {}
    public record RoleSkillStatus(String name, String status, String level, boolean covered) {}
    public record RoleReadiness(List<RoleSkillStatus> skills, int covered, int completed, int total,
                                int percent, int projectCount, int completedProjects) {}

    public void save(CareerGpsSelection selection) {
        jdbc.update("INSERT INTO career_gps_selections(registration_no, primary_path, target_company, target_program, target_role, target_band, source_version) " +
                        "VALUES(?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE primary_path=VALUES(primary_path), " +
                        "target_company=VALUES(target_company), target_program=VALUES(target_program), target_role=VALUES(target_role), " +
                        "target_band=VALUES(target_band), source_version=VALUES(source_version), " +
                        "updated_at=CURRENT_TIMESTAMP",
                selection.registrationNo(), selection.primaryPath(), selection.targetCompany(),
                selection.targetProgram() == null || selection.targetProgram().isBlank() ? "Not specified" : selection.targetProgram(),
                selection.targetRole(), selection.targetBand(), selection.sourceVersion());
    }
}
