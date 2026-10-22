package com.nexora.controller;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DevelopmentControllerProgressTest {
    @Test
    void averagesIntegerMilestonePercentagesWithoutClassCastException() {
        List<Map<String, Object>> milestones = List.of(
                Map.of("percent", 0),
                Map.of("percent", 50),
                Map.of("percent", 100)
        );
        assertEquals(50, DevelopmentController.averageMilestoneProgress(milestones));
    }

    @Test
    void returnsZeroForEmptyOrMissingPercentagesAndBoundsResult() {
        assertEquals(0, DevelopmentController.averageMilestoneProgress(List.of()));
        assertEquals(0, DevelopmentController.averageMilestoneProgress(List.of(Map.of("label", "missing"))));
        assertEquals(100, DevelopmentController.averageMilestoneProgress(List.of(Map.of("percent", 150))));
        assertEquals(0, DevelopmentController.averageMilestoneProgress(List.of(Map.of("percent", -10))));
    }
}
