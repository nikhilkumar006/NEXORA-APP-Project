package com.nexora.career;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Persisted credit-weighted semester GPA / cumulative GPA calculator. */
@Service
public class GradeCalculatorService {
    private static final Map<String, Integer> GRADE_POINTS = gradePointMap();
    private final JdbcTemplate jdbc;

    public GradeCalculatorService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<SubjectGrade> subjects(String registrationNo, int semester) {
        return jdbc.query("SELECT id, semester_number, subject_name, credits, letter_grade, grade_points " +
                        "FROM grade_subject_records WHERE registration_no=? AND semester_number=? ORDER BY id",
                (rs, row) -> new SubjectGrade(rs.getLong("id"), rs.getInt("semester_number"),
                        rs.getString("subject_name"), rs.getInt("credits"), rs.getString("letter_grade"),
                        rs.getInt("grade_points")), registrationNo, semester);
    }

    public List<SemesterSummary> semesterSummaries(String registrationNo) {
        Map<Integer,SemesterSummary> summaries = new LinkedHashMap<>();
        jdbc.query("SELECT semester_number, COUNT(*) AS subject_count, SUM(credits) AS total_credits, " +
                        "SUM(credits * grade_points) AS weighted_points FROM grade_subject_records " +
                        "WHERE registration_no=? GROUP BY semester_number ORDER BY semester_number",
                rs -> {
                    int credits = rs.getInt("total_credits");
                    double weighted = rs.getDouble("weighted_points");
                    int semester = rs.getInt("semester_number");
                    summaries.put(semester, new SemesterSummary(semester, rs.getInt("subject_count"), credits,
                            roundGpa(credits == 0 ? 0 : weighted / credits)));
                }, registrationNo);
        jdbc.query("SELECT h.semester_number,h.sgpa,h.total_credits FROM student_semester_gpa_history h " +
                        "WHERE h.registration_no=? AND NOT EXISTS (SELECT 1 FROM grade_subject_records g WHERE g.registration_no=h.registration_no AND g.semester_number=h.semester_number) ORDER BY h.semester_number",
                rs -> {
                    summaries.putIfAbsent(rs.getInt("semester_number"), new SemesterSummary(
                            rs.getInt("semester_number"), 0, rs.getInt("total_credits"), rs.getDouble("sgpa")));
                }, registrationNo);
        return summaries.values().stream().sorted(java.util.Comparator.comparingInt(SemesterSummary::semester)).toList();
    }

    public CalculatorSummary summary(String registrationNo) {
        List<SemesterSummary> semesters = semesterSummaries(registrationNo);
        int credits = 0, subjects = 0;
        double weighted = 0;
        for (SemesterSummary item : semesters) {
            credits += item.totalCredits();
            subjects += item.subjectCount();
            weighted += item.sgpa() * item.totalCredits();
        }
        return new CalculatorSummary(credits, subjects, roundGpa(credits == 0 ? 0 : weighted / credits), semesters);
    }

    /** Import a past semester's official/previously calculated SGPA into CGPA without adding a third calculator mode. */
    @Transactional
    public boolean saveHistoricalSemesterGpa(String registrationNo, int semester, BigDecimal sgpa, int totalCredits, int currentSemester) {
        if (registrationNo == null || registrationNo.isBlank()) throw new IllegalArgumentException("Student account is missing.");
        if (semester < 1 || semester >= currentSemester || currentSemester > 8) throw new IllegalArgumentException("Only a semester before your current semester can be imported here.");
        if (sgpa == null || sgpa.compareTo(BigDecimal.ZERO) < 0 || sgpa.compareTo(BigDecimal.TEN) > 0) throw new IllegalArgumentException("SGPA must be between 0 and 10.");
        if (totalCredits < 1 || totalCredits > 40) throw new IllegalArgumentException("Total credits must be between 1 and 40.");
        Integer detailed = jdbc.queryForObject("SELECT COUNT(*) FROM grade_subject_records WHERE registration_no=? AND semester_number=?", Integer.class, registrationNo, semester);
        if (detailed != null && detailed > 0) throw new IllegalArgumentException("This semester already has subject-level results. Edit those results in the SGPA workflow instead.");
        jdbc.update("INSERT INTO student_semester_gpa_history(registration_no,semester_number,sgpa,total_credits,source) VALUES(?,?,?,?, 'STUDENT_ENTERED') ON DUPLICATE KEY UPDATE sgpa=VALUES(sgpa),total_credits=VALUES(total_credits),source='STUDENT_ENTERED',updated_at=CURRENT_TIMESTAMP", registrationNo, semester, sgpa.setScale(2,RoundingMode.HALF_UP), totalCredits);
        return syncCumulativeIfComplete(registrationNo, currentSemester);
    }

    @Transactional
    public boolean deleteSemesterResults(String registrationNo, int semester, int currentSemester) {
        if (registrationNo == null || registrationNo.isBlank()) throw new IllegalArgumentException("Student account is missing.");
        if (semester < 1 || semester > 8) throw new IllegalArgumentException("Choose a valid semester.");
        jdbc.update("DELETE FROM student_semester_gpa_history WHERE registration_no=? AND semester_number=?", registrationNo, semester);
        jdbc.update("DELETE FROM grade_subject_records WHERE registration_no=? AND semester_number=?", registrationNo, semester);
        // Never leave a stale cumulative GPA in the placement profile after a semester is removed.
        jdbc.update("UPDATE placement_profiles SET cgpa=NULL WHERE registration_no=?", registrationNo);
        return syncCumulativeIfComplete(registrationNo, currentSemester);
    }

    private boolean syncCumulativeIfComplete(String registrationNo, int currentSemester) {
        CalculatorSummary summary = summary(registrationNo);
        java.util.Set<Integer> savedSemesters = summary.semesters().stream().map(SemesterSummary::semester).collect(java.util.stream.Collectors.toSet());
        boolean complete = currentSemester >= 1 && currentSemester <= 8;
        for (int current = 1; current <= currentSemester && complete; current++) if (!savedSemesters.contains(current)) complete = false;
        if (complete) {
            jdbc.update("INSERT INTO placement_profiles(registration_no,cgpa) VALUES(?,?) ON DUPLICATE KEY UPDATE cgpa=VALUES(cgpa),updated_at=CURRENT_TIMESTAMP", registrationNo, BigDecimal.valueOf(summary.cgpa()).setScale(2,RoundingMode.HALF_UP));
            return true;
        }
        return false;
    }

    @Transactional
    public boolean saveSemester(String registrationNo, int semester, List<String> subjectNames,
                             List<Integer> credits, List<String> grades) {
        if (registrationNo == null || registrationNo.isBlank()) throw new IllegalArgumentException("Student account is missing.");
        if (semester < 1 || semester > 8) throw new IllegalArgumentException("Choose a semester between 1 and 8.");
        if (subjectNames == null || credits == null || grades == null ||
                subjectNames.size() != credits.size() || subjectNames.size() != grades.size()) {
            throw new IllegalArgumentException("Every subject must have a name, credit value and grade.");
        }
        if (subjectNames.size() > 30) throw new IllegalArgumentException("A semester can contain at most 30 subjects.");

        List<ValidatedSubject> validated = new ArrayList<>();
        for (int i = 0; i < subjectNames.size(); i++) {
            String name = subjectNames.get(i) == null ? "" : subjectNames.get(i).trim();
            if (name.isEmpty()) continue; // Ignore the empty starter row in the form.
            if (name.length() > 180) throw new IllegalArgumentException("Subject names must be 180 characters or fewer.");
            Integer credit = credits.get(i);
            if (credit == null || credit < 1 || credit > 10) throw new IllegalArgumentException("Credits must be between 1 and 10.");
            String grade = grades.get(i) == null ? "" : grades.get(i).trim().toUpperCase();
            Integer points = GRADE_POINTS.get(grade);
            if (points == null) throw new IllegalArgumentException("Select a valid grade for every subject.");
            validated.add(new ValidatedSubject(name, credit, grade, points));
        }
        if (validated.isEmpty()) throw new IllegalArgumentException("Add at least one subject before saving.");

        jdbc.update("DELETE FROM student_semester_gpa_history WHERE registration_no=? AND semester_number=?", registrationNo, semester);
        jdbc.update("DELETE FROM grade_subject_records WHERE registration_no=? AND semester_number=?", registrationNo, semester);
        for (ValidatedSubject subject : validated) {
            jdbc.update("INSERT INTO grade_subject_records(registration_no, semester_number, subject_name, credits, letter_grade, grade_points) " +
                            "VALUES(?,?,?,?,?,?)", registrationNo, semester, subject.name(), subject.credits(),
                    subject.grade(), subject.points());
        }

        Integer currentSemesterValue = jdbc.queryForObject("SELECT current_semester FROM students WHERE registration_no=?", Integer.class, registrationNo);
        int currentSemester = currentSemesterValue == null ? 0 : currentSemesterValue;
        return syncCumulativeIfComplete(registrationNo, currentSemester);
    }

    public List<GradeChoice> gradeChoices() {
        return GRADE_POINTS.entrySet().stream().map(e -> new GradeChoice(e.getKey(), e.getValue())).toList();
    }

    public int pointFor(String grade) {
        return grade == null ? 0 : GRADE_POINTS.getOrDefault(grade.trim().toUpperCase(), 0);
    }

    private static double roundGpa(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static Map<String, Integer> gradePointMap() {
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("O", 10);
        result.put("A+", 9);
        result.put("A", 8);
        result.put("B+", 7);
        result.put("B", 6);
        result.put("C", 5);
        result.put("F", 0);
        result.put("AB", 0);
        result.put("I", 0);
        return Collections.unmodifiableMap(result);
    }

    private record ValidatedSubject(String name, int credits, String grade, int points) {}
    public record SubjectGrade(long id, int semester, String name, int credits, String grade, int gradePoints) {}
    public record SemesterSummary(int semester, int subjectCount, int totalCredits, double sgpa) {}
    public record CalculatorSummary(int totalCredits, int totalSubjects, double cgpa, List<SemesterSummary> semesters) {}
    public record GradeChoice(String grade, int points) {}
}
