package com.nexora.academic;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class AcademicPeriodTest {
    @Test void autumnTermUsesCohortYearAndCurrentDate() {
        AcademicPeriod.Current current = AcademicPeriod.calculate(2025, LocalDate.of(2026, 10, 11));
        assertEquals(3, current.semester());
        assertEquals(2, current.yearOfStudy());
        assertEquals(2026, current.academicYear());
        assertEquals("Odd Semester", current.semesterType());
        assertFalse(current.breakPeriod());
    }

    @Test void springTermAdvancesSemesterAutomatically() {
        AcademicPeriod.Current current = AcademicPeriod.calculate(2025, LocalDate.of(2027, 2, 3));
        assertEquals(4, current.semester());
        assertEquals(2, current.yearOfStudy());
        assertEquals("Even Semester", current.semesterType());
        assertFalse(current.breakPeriod());
    }

    @Test void summerBreakRetainsLastCompletedSemester() {
        AcademicPeriod.Current current = AcademicPeriod.calculate(2025, LocalDate.of(2026, 7, 10));
        assertEquals(2, current.semester());
        assertEquals(1, current.yearOfStudy());
        assertTrue(current.breakPeriod());
    }

    @Test void academicPeriodChangesAtSemesterBoundariesUsingTheCohort() {
        AcademicPeriod.Current spring = AcademicPeriod.calculate(2025, LocalDate.of(2026, 5, 31));
        assertEquals(2, spring.semester());
        assertEquals(1, spring.yearOfStudy());
        assertFalse(spring.breakPeriod());

        AcademicPeriod.Current summer = AcademicPeriod.calculate(2025, LocalDate.of(2026, 6, 1));
        assertEquals(2, summer.semester());
        assertTrue(summer.breakPeriod());

        AcademicPeriod.Current autumn = AcademicPeriod.calculate(2025, LocalDate.of(2026, 8, 1));
        assertEquals(3, autumn.semester());
        assertEquals(2, autumn.yearOfStudy());
        assertEquals(2026, autumn.academicYear());
    }

    @Test void semesterNeverExceedsEightForGraduatingCohorts() {
        AcademicPeriod.Current current = AcademicPeriod.calculate(2022, LocalDate.of(2030, 10, 1));
        assertEquals(8, current.semester());
        assertEquals(4, current.yearOfStudy());
    }

    @Test void invalidCohortDoesNotProduceAFakeSemester() {
        assertThrows(IllegalArgumentException.class, () -> AcademicPeriod.calculate(0, LocalDate.of(2026, 10, 11)));
    }
}
