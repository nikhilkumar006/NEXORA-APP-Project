package com.nexora.academic;

public class PYQModelStats {

    private final String questionModel;
    private final int occurrences;
    private final int totalMarks;

    public PYQModelStats(
            String questionModel,
            int occurrences,
            int totalMarks) {

        this.questionModel = questionModel;
        this.occurrences = occurrences;
        this.totalMarks = totalMarks;
    }

    public String getQuestionModel() {
        return questionModel;
    }

    public int getOccurrences() {
        return occurrences;
    }

    public int getTotalMarks() {
        return totalMarks;
    }
}