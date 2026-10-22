package com.nexora.academic;

import java.util.ArrayList;
import java.util.List;

public class AcademicUnit {

    private String unitId;

    private String unitName;
    private String description;

    private List<AcademicTopic> topics;


    // ============================================================
    // DEFAULT CONSTRUCTOR
    // ============================================================

    public AcademicUnit() {

        this.topics = new ArrayList<>();
    }


    // ============================================================
    // EXISTING CONSTRUCTOR
    // ============================================================

    public AcademicUnit(
            String unitName,
            String description,
            List<AcademicTopic> topics
    ) {

        this.unitName = unitName;
        this.description = description;

        this.topics =
                topics != null
                        ? topics
                        : new ArrayList<>();
    }


    // ============================================================
    // UNIT ID
    // ============================================================

    public String getUnitId() {
        return unitId;
    }

    public void setUnitId(String unitId) {
        this.unitId = unitId;
    }


    // ============================================================
    // UNIT NAME
    // ============================================================

    public String getUnitName() {
        return unitName;
    }

    public void setUnitName(String unitName) {
        this.unitName = unitName;
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
    // TOPICS
    // ============================================================

    public List<AcademicTopic> getTopics() {
        return topics;
    }

    public void setTopics(List<AcademicTopic> topics) {

        this.topics =
                topics != null
                        ? topics
                        : new ArrayList<>();
    }
}