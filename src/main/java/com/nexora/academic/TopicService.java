package com.nexora.academic;

import java.util.List;

public interface TopicService {

    /*
     * ============================================================
     * UNIT STRUCTURE
     * ============================================================
     */

    List<AcademicUnit> getUnitsForSubject(String subjectCode);


    /*
     * ============================================================
     * TOPICS
     * ============================================================
     */

    List<Topic> getTopicsForSubject(String subjectCode);


    /*
     * ============================================================
     * DEPENDENCIES
     * ============================================================
     */

    List<TopicDependency> getDependenciesForSubject(
            String subjectCode
    );

    Topic getTopicByName(
            String subjectCode,
            String topicName
    );

    List<String> getPrerequisites(
            String subjectCode,
            String topicName
    );

    List<String> getDependentTopics(
            String subjectCode,
            String topicName
    );

    List<String> getFullPrerequisiteChain(
            String subjectCode,
            String topicName
    );
}