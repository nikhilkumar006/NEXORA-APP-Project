package com.nexora.academic;

public class TopicDependency {

    private final String prerequisiteTopic;
    private final String dependentTopic;
    private final String relationshipType;

    public TopicDependency(String prerequisiteTopic,
                           String dependentTopic,
                           String relationshipType) {

        this.prerequisiteTopic = prerequisiteTopic;
        this.dependentTopic = dependentTopic;
        this.relationshipType = relationshipType;
    }

    public String getPrerequisiteTopic() {
        return prerequisiteTopic;
    }

    public String getDependentTopic() {
        return dependentTopic;
    }

    public String getRelationshipType() {
        return relationshipType;
    }
}