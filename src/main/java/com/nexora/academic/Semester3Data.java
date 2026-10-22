package com.nexora.academic;

import java.util.ArrayList;
import java.util.List;

public class Semester3Data {

    private Semester3Data() {
        // Utility class
    }

    public static List<AcademicCourse> getCourses() {

        List<AcademicCourse> courses = new ArrayList<>();

        courses.add(
                new AcademicCourse(
                        "21MAB201T",
                        "Transforms and Boundary Value Problems",
                        true,
                        true,
                        true
                )
        );

        courses.add(
                new AcademicCourse(
                        "21DCS201P",
                        "Design Thinking Methodology",
                        false,
                        false,
                        false
                )
        );

        courses.add(
                new AcademicCourse(
                        "21CSS201T",
                        "Computer Organization and Architecture",
                        true,
                        true,
                        true
                )
        );

        courses.add(
                new AcademicCourse(
                        "21CSC201J",
                        "Data Structures and Algorithms",
                        true,
                        true,
                        true
                )
        );

        courses.add(
                new AcademicCourse(
                        "21CSC202J",
                        "Operating System",
                        true,
                        true,
                        true
                )
        );

        courses.add(
                new AcademicCourse(
                        "21CSC203P",
                        "Advanced Programming Practice",
                        true,
                        true,
                        false
                )
        );

        return courses;
    }
}