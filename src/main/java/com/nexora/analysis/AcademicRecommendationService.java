package com.nexora.analysis;

import com.nexora.academic.AcademicAnalysisService;
import com.nexora.academic.AcademicTopic;
import com.nexora.academic.PYQQuestion;
import com.nexora.academic.PYQRepository;
import org.springframework.stereotype.Service;
import java.util.*;

/**
 * Transparent study-priority rules based only on stored syllabus topics and indexed
 * semester previous-year-question frequency. No assessments or unsupported scores
 * are inferred.
 */
@Service
public class AcademicRecommendationService {
    private final PYQRepository pyq;
    private final AcademicAnalysisService academic;

    public AcademicRecommendationService(PYQRepository pyq, AcademicAnalysisService academic) {
        this.pyq = pyq;
        this.academic = academic;
    }

    public List<StudyRecommendation> recommend(String subjectCode) {
        Map<String, Integer> frequency = new HashMap<>();
        for (PYQQuestion question : pyq.findBySubjectCode(subjectCode)) {
            if (question.getTopic() != null && !question.getTopic().isBlank()) {
                frequency.merge(question.getTopic(), 1, Integer::sum);
            }
        }
        List<StudyRecommendation> recommendations = new ArrayList<>();
        for (AcademicTopic topic : academic.getTopics(subjectCode)) {
            int occurrences = frequency.getOrDefault(topic.getTopic(), 0);
            int priority = occurrences >= 4 ? 3 : occurrences >= 2 ? 2 : occurrences > 0 ? 1 : 0;
            String reason = occurrences > 0
                    ? "Historical frequency: " + occurrences + " mapped semester-question record(s)."
                    : "No indexed semester-question evidence is recorded for this topic yet.";
            recommendations.add(new StudyRecommendation(topic.getTopic(), priority, reason, occurrences));
        }
        recommendations.sort(Comparator.comparingInt(StudyRecommendation::priority).reversed()
                .thenComparing(StudyRecommendation::topic));
        return recommendations;
    }
}
