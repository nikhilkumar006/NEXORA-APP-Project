package com.nexora.academic;

public class PYQExamStats {

    private final String examType;
    private final String examYear;
    private final int occurrences;
    private final int totalMarks;

    public PYQExamStats(
            String examType,
            String examYear,
            int occurrences,
            int totalMarks) {

        this.examType = examType;
        this.examYear = examYear;
        this.occurrences = occurrences;
        this.totalMarks = totalMarks;
    }

    public String getExamType() {
        return examType;
    }

    public String getExamYear() {
        return examYear;
    }

    public int getOccurrences() {
        return occurrences;
    }

    public int getTotalMarks() {
        return totalMarks;
    }
}