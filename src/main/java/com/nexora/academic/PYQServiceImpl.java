package com.nexora.academic;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PYQServiceImpl implements PYQService {

    private final PYQRepository pyqRepository;

    public PYQServiceImpl(PYQRepository pyqRepository) {
        this.pyqRepository = pyqRepository;
    }

    @Override
    public List<PYQQuestion> getQuestionsForSubject(
            String subjectCode) {

        return pyqRepository.findBySubjectCode(
                subjectCode
        );
    }

    @Override
    public List<PYQQuestion> getQuestionsForTopic(
            String subjectCode,
            String topicName) {

        return pyqRepository.findBySubjectCodeAndTopic(
                subjectCode,
                topicName
        );
    }

    @Override
    public List<String> getQuestionModelsForTopic(
            String subjectCode,
            String topicName) {

        Set<String> models = new LinkedHashSet<>();

        List<PYQQuestion> questions =
                getQuestionsForTopic(
                        subjectCode,
                        topicName
                );

        for (PYQQuestion question : questions) {

            if (question.getQuestionModel() != null
                    && !question.getQuestionModel().isBlank()) {

                models.add(
                        question.getQuestionModel()
                );
            }
        }

        return new ArrayList<>(models);
    }

    @Override
    public List<PYQModelStats> getModelStatisticsForTopic(
            String subjectCode,
            String topicName) {

        List<PYQQuestion> questions =
                getQuestionsForTopic(
                        subjectCode,
                        topicName
                );

        int currentYear = java.time.Year.now().getValue();
        int oldestYear = currentYear - 4;

        questions = questions.stream()
                .filter(q -> {
                    try {
                        int y = Integer.parseInt(q.getYear());
                        return y >= oldestYear && y <= currentYear;
                    } catch (Exception ignored) {
                        return false;
                    }
                })
                .toList();

        Map<String, Integer> occurrenceMap =
                new LinkedHashMap<>();

        Map<String, Integer> marksMap =
                new LinkedHashMap<>();

        for (PYQQuestion question : questions) {

            String model =
                    question.getQuestionModel();

            if (model == null || model.isBlank()) {
                continue;
            }

            occurrenceMap.put(
                    model,
                    occurrenceMap.getOrDefault(model, 0) + 1
            );

            marksMap.put(
                    model,
                    marksMap.getOrDefault(model, 0)
                            + question.getMarks()
            );
        }

        List<PYQModelStats> statistics =
                new ArrayList<>();

        for (String model : occurrenceMap.keySet()) {

            statistics.add(
                    new PYQModelStats(
                            model,
                            occurrenceMap.get(model),
                            marksMap.get(model)
                    )
            );
        }

        return statistics;
    }

    @Override
    public List<PYQExamStats> getExamStatisticsForTopic(
            String subjectCode,
            String topicName) {

        return pyqRepository.findExamStatisticsForTopic(
                subjectCode,
                topicName
        );
    }
}