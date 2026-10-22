package com.nexora.career;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlacementPlannerServiceTest {
    private final PlacementPlannerService service = new PlacementPlannerService(new ObjectMapper());

    @Test
    void loadsSourceBackedBenchmarksForSupportedYopsAndBands() {
        for (int yop : new int[]{2028, 2029, 2030}) {
            for (String band : new String[]{"Standard", "Dream", "Super Dream", "Marquee"}) {
                assertFalse(service.criteria(yop, band).isEmpty(), yop + " / " + band + " should have planner rows");
            }
        }
    }

    @Test
    void doesNotInventBenchmarksForUnsupportedYop() {
        assertTrue(service.criteria(2031, "Dream").isEmpty());
        assertTrue(service.note().contains("not a guarantee"));
    }
}
