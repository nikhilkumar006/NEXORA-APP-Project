package com.nexora.academic;

import java.time.LocalDate;
import java.time.Month;

/** Central academic calendar for NEXORA. Odd semester: Aug-Dec; even: Jan-May. */
public final class AcademicPeriod {
    private AcademicPeriod() {}

    public record Current(int academicYear, int semester, int yearOfStudy, String semesterType, String label, boolean breakPeriod) {}

    public static Current calculate(int batchStartYear, LocalDate date) {
        if (date == null) throw new IllegalArgumentException("A current date is required to calculate the academic period.");
        if (batchStartYear < 2000 || batchStartYear > date.getYear()) {
            throw new IllegalArgumentException("The student cohort start year is not configured correctly.");
        }
        int y = date.getYear();
        Month m = date.getMonth();
        int semester;
        boolean breakPeriod = false;

        if (m.getValue() >= 8) { // Aug-Dec
            semester = (y - batchStartYear) * 2 + 1;
        } else if (m.getValue() <= 5) { // Jan-May
            semester = (y - batchStartYear) * 2;
        } else { // Jun-Jul: semester break, retain the most recently completed semester
            semester = Math.max(1, (y - batchStartYear) * 2);
            breakPeriod = true;
        }

        semester = Math.max(1, Math.min(8, semester));
        int yearOfStudy = (semester + 1) / 2;
        int academicYearStart = batchStartYear + (yearOfStudy - 1);
        String academicYear = academicYearStart + "-" + String.valueOf(academicYearStart + 1).substring(2);
        String type = semester % 2 == 1 ? "Odd Semester" : "Even Semester";
        String label = "Semester " + semester + " • " + yearOfStudy + " Year • " + academicYear;
        return new Current(academicYearStart, semester, yearOfStudy, type, label, breakPeriod);
    }
}
