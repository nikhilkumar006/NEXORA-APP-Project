package com.nexora.academic;

public class ExtractedAcademicQuestion {
    private final String questionNumber;
    private final String questionText;
    private final String topic;
    private final String questionModel;
    private final int marks;
    private final double confidence;

    public ExtractedAcademicQuestion(String questionNumber, String questionText,
                                      String topic, String questionModel,
                                      int marks, double confidence) {
        this.questionNumber = questionNumber;
        this.questionText = questionText;
        this.topic = topic;
        this.questionModel = questionModel;
        this.marks = marks;
        this.confidence = confidence;
    }
    public String getQuestionNumber() { return questionNumber; }
    public String getQuestionText() { return questionText; }
    public String getTopic() { return topic; }
    public String getQuestionModel() { return questionModel; }
    public int getMarks() { return marks; }
    public double getConfidence() { return confidence; }
}
