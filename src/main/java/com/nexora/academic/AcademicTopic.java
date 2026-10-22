package com.nexora.academic;

import java.util.ArrayList;
import java.util.List;

public class AcademicTopic {

    private String topicId;

    private String unit;
    private String topic;
    private String description;

    private List<String> subtopics;
    private List<String> prerequisites;
    private List<String> relatedTopics;


    // ============================================================
    // DEFAULT CONSTRUCTOR
    // ============================================================

    public AcademicTopic() {

        this.subtopics = new ArrayList<>();
        this.prerequisites = new ArrayList<>();
        this.relatedTopics = new ArrayList<>();
    }


    // ============================================================
    // OLD CONSTRUCTOR
    // ============================================================

    public AcademicTopic(
            String unit,
            String topic,
            String description,
            List<String> prerequisites,
            List<String> relatedTopics
    ) {

        this.unit = unit;
        this.topic = topic;
        this.description = description;

        this.subtopics = new ArrayList<>();

        this.prerequisites =
                prerequisites != null
                        ? prerequisites
                        : new ArrayList<>();

        this.relatedTopics =
                relatedTopics != null
                        ? relatedTopics
                        : new ArrayList<>();
    }


    // ============================================================
    // SUBTOPIC-AWARE CONSTRUCTOR
    // ============================================================

    public AcademicTopic(
            String unit,
            String topic,
            String description,
            List<String> subtopics,
            List<String> prerequisites,
            List<String> relatedTopics
    ) {

        this.unit = unit;
        this.topic = topic;
        this.description = description;

        this.subtopics =
                subtopics != null
                        ? subtopics
                        : new ArrayList<>();

        this.prerequisites =
                prerequisites != null
                        ? prerequisites
                        : new ArrayList<>();

        this.relatedTopics =
                relatedTopics != null
                        ? relatedTopics
                        : new ArrayList<>();
    }


    // ============================================================
    // TOPIC ID
    // ============================================================

    public String getTopicId() {
        return topicId;
    }

    public void setTopicId(String topicId) {
        this.topicId = topicId;
    }


    // ============================================================
    // UNIT
    // ============================================================

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }


    // ============================================================
    // TOPIC
    // ============================================================

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }


    // ============================================================
    // DESCRIPTION
    // ============================================================

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }


    // ============================================================
    // SUBTOPICS
    // ============================================================

    public List<String> getSubtopics() {
        return subtopics;
    }

    public void setSubtopics(List<String> subtopics) {

        this.subtopics =
                subtopics != null
                        ? subtopics
                        : new ArrayList<>();
    }


    // ============================================================
    // PREREQUISITES
    // ============================================================

    public List<String> getPrerequisites() {
        return prerequisites;
    }

    public void setPrerequisites(List<String> prerequisites) {

        this.prerequisites =
                prerequisites != null
                        ? prerequisites
                        : new ArrayList<>();
    }


    // ============================================================
    // RELATED TOPICS
    // ============================================================

    public List<String> getRelatedTopics() {
        return relatedTopics;
    }

    public void setRelatedTopics(List<String> relatedTopics) {

        this.relatedTopics =
                relatedTopics != null
                        ? relatedTopics
                        : new ArrayList<>();
    }
}