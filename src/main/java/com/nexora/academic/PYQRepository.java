package com.nexora.academic;

import java.util.List;

public interface PYQRepository {

    List<PYQQuestion> findBySubjectCode(
            String subjectCode);

    List<PYQQuestion> findBySubjectCodeAndTopic(
            String subjectCode,
            String topicName);

    List<PYQExamStats> findExamStatisticsForTopic(
            String subjectCode,
            String topicName);
}