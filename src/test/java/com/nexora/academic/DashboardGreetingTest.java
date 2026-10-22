package com.nexora.academic;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DashboardGreetingTest {
    @Test void midnightThroughElevenFiftyNineAmIsMorning() {
        assertEquals("Good Morning", DashboardGreeting.forHour(0));
        assertEquals("Good Morning", DashboardGreeting.forHour(11));
    }

    @Test void noonThroughElevenFiftyNinePmIsEveningAsRequested() {
        assertEquals("Good Evening", DashboardGreeting.forHour(12));
        assertEquals("Good Evening", DashboardGreeting.forHour(23));
    }

    @Test void invalidHoursAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> DashboardGreeting.forHour(-1));
        assertThrows(IllegalArgumentException.class, () -> DashboardGreeting.forHour(24));
    }
}
