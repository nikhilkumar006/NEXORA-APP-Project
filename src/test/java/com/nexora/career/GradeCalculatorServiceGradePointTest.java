package com.nexora.career;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GradeCalculatorServiceGradePointTest {
    @Test
    void usesSrmGradePointTableIncludingAttendanceAndNonAppearanceFailures() {
        GradeCalculatorService service = new GradeCalculatorService(null);
        assertEquals(10, service.pointFor("O"));
        assertEquals(9, service.pointFor("A+"));
        assertEquals(8, service.pointFor("A"));
        assertEquals(7, service.pointFor("B+"));
        assertEquals(6, service.pointFor("B"));
        assertEquals(5, service.pointFor("C"));
        assertEquals(0, service.pointFor("F"));
        assertEquals(0, service.pointFor("Ab"));
        assertEquals(0, service.pointFor("I"));
    }
}
