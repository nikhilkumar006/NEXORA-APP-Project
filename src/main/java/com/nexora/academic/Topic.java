package com.nexora.academic;

public class Topic {

    private final String name;
    private final String category;
    private final String prerequisites;
    private final String relatedTopics;
    private final String status;

    public Topic(
            String name,
            String category,
            String prerequisites,
            String relatedTopics,
            String status
    ) {
        this.name = name;
        this.category = category;
        this.prerequisites = prerequisites;
        this.relatedTopics = relatedTopics;
        this.status = status;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public String getPrerequisites() {
        return prerequisites;
    }

    public String getRelatedTopics() {
        return relatedTopics;
    }

    /*
     * Backward-compatible getter.
     *
     * Existing Thymeleaf/templates or older code may still
     * call getLeadsTo(). Keeping this prevents the current
     * application from breaking while the UI is migrated.
     */
    public String getLeadsTo() {
        return relatedTopics;
    }

    public String getStatus() {
        return status;
    }

    /** Unit name alias used by academic intelligence views. */
    public String getUnit() {
        return status;
    }
}