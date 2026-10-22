package com.nexora.academic;

import com.nexora.xp.XpService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.sql.PreparedStatement;
import java.sql.Statement;

/** Server-side full-chapter MCQ grading. No answer key is sent to the browser. */
@Service
public class ChapterTestService {
    private final JdbcTemplate jdbc;
    private final XpService xp;

    public ChapterTestService(JdbcTemplate jdbc, XpService xp) {
        this.jdbc = jdbc;
        this.xp = xp;
    }

    public List<Question> questions(String subjectCode, int chapterIndex) {
        return jdbc.query("SELECT id,question_text,option_a,option_b,option_c,option_d,marks FROM chapter_test_questions WHERE subject_code=? AND chapter_index=? AND active=TRUE ORDER BY id",
                (rs, row) -> new Question(rs.getLong("id"), rs.getString("question_text"),
                        List.of(new Option("A", rs.getString("option_a")), new Option("B", rs.getString("option_b")),
                                new Option("C", rs.getString("option_c")), new Option("D", rs.getString("option_d"))),
                        rs.getBigDecimal("marks") == null ? BigDecimal.ONE : rs.getBigDecimal("marks")), subjectCode, chapterIndex);
    }

    @Transactional
    public TestResult grade(String registrationNo, String subjectCode, int chapterIndex, String chapterName,
                            Map<Long, String> submittedAnswers) {
        if (registrationNo == null || registrationNo.isBlank()) throw new IllegalArgumentException("Student account could not be identified.");
        List<KeyedQuestion> questions = jdbc.query("SELECT id,correct_option,marks FROM chapter_test_questions WHERE subject_code=? AND chapter_index=? AND active=TRUE ORDER BY id",
                (rs, row) -> new KeyedQuestion(rs.getLong("id"), rs.getString("correct_option"),
                        rs.getBigDecimal("marks") == null ? BigDecimal.ONE : rs.getBigDecimal("marks")), subjectCode, chapterIndex);
        if (questions.isEmpty()) throw new IllegalArgumentException("This chapter test has not been configured yet.");
        BigDecimal obtained = BigDecimal.ZERO;
        BigDecimal maximum = BigDecimal.ZERO;
        for (KeyedQuestion q : questions) {
            BigDecimal marks = q.marks().max(BigDecimal.ZERO);
            maximum = maximum.add(marks);
            String answer = submittedAnswers == null ? null : submittedAnswers.get(q.id());
            if (answer != null && answer.trim().equalsIgnoreCase(q.correctOption())) obtained = obtained.add(marks);
        }
        if (maximum.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalStateException("This test has no positive marks configured.");
        BigDecimal percent = obtained.multiply(BigDecimal.valueOf(100)).divide(maximum, 2, RoundingMode.HALF_UP);
        String status = percent.compareTo(BigDecimal.valueOf(75)) >= 0 ? "PASSED" : "COMPLETED";
        // Capture immutable values for the JDBC statement lambda. The scoring loop above
        // updates obtained and maximum, so those local variables are not effectively final.
        final BigDecimal finalObtained = obtained;
        final BigDecimal finalMaximum = maximum;
        final String finalStatus = status;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO student_tests(registration_no,subject_code,chapter,test_type,marks_obtained,max_marks,status) VALUES(?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, registrationNo);
            statement.setString(2, subjectCode);
            statement.setString(3, chapterName);
            statement.setString(4, "FULL_CHAPTER");
            statement.setBigDecimal(5, finalObtained);
            statement.setBigDecimal(6, finalMaximum);
            statement.setString(7, finalStatus);
            return statement;
        }, keyHolder);
        Number generatedId = keyHolder.getKey();
        Long attemptId = generatedId == null ? null : generatedId.longValue();
        boolean awarded = false;
        if (attemptId != null && percent.compareTo(BigDecimal.valueOf(75)) >= 0) {
            awarded = xp.awardChapterTestPass(registrationNo, subjectCode, chapterIndex, attemptId, percent);
        }
        return new TestResult(attemptId == null ? 0L : attemptId, obtained, maximum, percent, awarded,
                percent.compareTo(BigDecimal.valueOf(75)) >= 0);
    }

    public record Option(String letter, String text) {}
    public record Question(long id, String text, List<Option> options, BigDecimal marks) {}
    private record KeyedQuestion(long id, String correctOption, BigDecimal marks) {}
    public record TestResult(long attemptId, BigDecimal marksObtained, BigDecimal maxMarks,
                             BigDecimal percent, boolean xpAwarded, boolean passed) {}
}
