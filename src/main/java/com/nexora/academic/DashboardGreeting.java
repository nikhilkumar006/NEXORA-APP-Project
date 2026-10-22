package com.nexora.academic;

/** Dashboard greeting boundary in India Standard Time: morning is midnight through 11:59 AM. */
public final class DashboardGreeting {
    private DashboardGreeting() {}

    public static String forHour(int hour) {
        if (hour < 0 || hour > 23) {
            throw new IllegalArgumentException("Hour must be between 0 and 23.");
        }
        return hour < 12 ? "Good Morning" : "Good Evening";
    }
}
