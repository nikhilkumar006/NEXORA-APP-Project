package com.nexora.academic;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class TopicServiceImpl implements TopicService {

    private final AcademicAnalysisService academicAnalysisService;

    public TopicServiceImpl(
            AcademicAnalysisService academicAnalysisService
    ) {
        this.academicAnalysisService =
                academicAnalysisService;
    }


    /*
     * ============================================================
     * UNIT STRUCTURE
     * ============================================================
     */

    @Override
    public List<AcademicUnit> getUnitsForSubject(
            String subjectCode
    ) {

        return academicAnalysisService.getUnits(
                subjectCode
        );
    }


    /*
     * ============================================================
     * TOPICS
     * ============================================================
     */

    @Override
    public List<Topic> getTopicsForSubject(
            String subjectCode
    ) {

        List<Topic> topics =
                new ArrayList<>();

        List<AcademicTopic> academicTopics =
                academicAnalysisService.getTopics(
                        subjectCode
                );

        for (AcademicTopic academicTopic :
                academicTopics) {

            topics.add(
                    new Topic(
                            academicTopic.getTopic(),
                            "Academic Topic",
                            joinList(
                                    academicTopic.getPrerequisites()
                            ),
                            joinList(
                                    academicTopic.getRelatedTopics()
                            ),
                            academicTopic.getUnit()
                    )
            );
        }

        return topics;
    }


    /*
     * ============================================================
     * DEPENDENCIES
     * ============================================================
     */

    @Override
    public List<TopicDependency> getDependenciesForSubject(
            String subjectCode
    ) {

        List<TopicDependency> dependencies =
                new ArrayList<>();

        List<AcademicTopic> academicTopics =
                academicAnalysisService.getTopics(
                        subjectCode
                );

        for (AcademicTopic academicTopic :
                academicTopics) {

            String dependentTopic =
                    academicTopic.getTopic();

            List<String> prerequisites =
                    academicTopic.getPrerequisites();

            if (prerequisites == null) {
                continue;
            }

            for (String prerequisite :
                    prerequisites) {

                if (prerequisite == null
                        || prerequisite.isBlank()) {

                    continue;
                }

                dependencies.add(
                        new TopicDependency(
                                prerequisite,
                                dependentTopic,
                                "PREREQUISITE"
                        )
                );
            }
        }

        return dependencies;
    }


    /*
     * ============================================================
     * FIND TOPIC
     * ============================================================
     */

    @Override
    public Topic getTopicByName(
            String subjectCode,
            String topicName
    ) {

        if (topicName == null
                || topicName.isBlank()) {

            return null;
        }

        for (Topic topic :
                getTopicsForSubject(subjectCode)) {

            if (topic.getName()
                    .equalsIgnoreCase(topicName)) {

                return topic;
            }
        }

        return null;
    }


    /*
     * ============================================================
     * DIRECT PREREQUISITES
     * ============================================================
     */

    @Override
    public List<String> getPrerequisites(
            String subjectCode,
            String topicName
    ) {

        List<String> prerequisites =
                new ArrayList<>();

        for (TopicDependency dependency :
                getDependenciesForSubject(
                        subjectCode
                )) {

            if (dependency
                    .getDependentTopic()
                    .equalsIgnoreCase(topicName)) {

                prerequisites.add(
                        dependency
                                .getPrerequisiteTopic()
                );
            }
        }

        return prerequisites;
    }


    /*
     * ============================================================
     * DEPENDENT TOPICS
     * ============================================================
     */

    @Override
    public List<String> getDependentTopics(
            String subjectCode,
            String topicName
    ) {

        List<String> dependentTopics =
                new ArrayList<>();

        for (TopicDependency dependency :
                getDependenciesForSubject(
                        subjectCode
                )) {

            if (dependency
                    .getPrerequisiteTopic()
                    .equalsIgnoreCase(topicName)) {

                dependentTopics.add(
                        dependency
                                .getDependentTopic()
                );
            }
        }

        return dependentTopics;
    }


    /*
     * ============================================================
     * FULL PREREQUISITE CHAIN
     * ============================================================
     */

    @Override
    public List<String> getFullPrerequisiteChain(
            String subjectCode,
            String topicName
    ) {

        Set<String> visited =
                new LinkedHashSet<>();

        buildPrerequisiteChain(
                subjectCode,
                topicName,
                visited
        );

        return new ArrayList<>(visited);
    }


    private void buildPrerequisiteChain(
            String subjectCode,
            String topicName,
            Set<String> visited
    ) {

        for (TopicDependency dependency :
                getDependenciesForSubject(
                        subjectCode
                )) {

            if (dependency
                    .getDependentTopic()
                    .equalsIgnoreCase(topicName)) {

                String prerequisite =
                        dependency
                                .getPrerequisiteTopic();

                if (!visited.contains(
                        prerequisite
                )) {
                    // Mark before descending so malformed/cyclic prerequisite
                    // data can never recurse forever during page rendering.
                    visited.add(prerequisite);
                    buildPrerequisiteChain(
                            subjectCode,
                            prerequisite,
                            visited
                    );
                }
            }
        }
    }


    /*
     * ============================================================
     * UTILITY
     * ============================================================
     */

    private String joinList(
            List<String> values
    ) {

        if (values == null
                || values.isEmpty()) {

            return "";
        }

        return String.join(
                ", ",
                values
        );
    }
}