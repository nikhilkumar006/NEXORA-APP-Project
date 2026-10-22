package com.nexora.academic;

import java.util.List;

public interface PYQService {

    List<PYQQuestion> getQuestionsForSubject(String subjectCode);

    List<PYQQuestion> getQuestionsForTopic(String subjectCode,
                                           String topicName);

    List<String> getQuestionModelsForTopic(String subjectCode,
                                           String topicName);

    List<PYQModelStats> getModelStatisticsForTopic(
            String subjectCode,
            String topicName
    );

    List<PYQExamStats> getExamStatisticsForTopic(
            String subjectCode,
            String topicName);
}