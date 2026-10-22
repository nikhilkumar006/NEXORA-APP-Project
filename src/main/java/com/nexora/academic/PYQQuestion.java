package com.nexora.academic;

public class PYQQuestion {

    private final String id;
    private final String examType;
    private final String year;
    private final String topic;
    private final String questionModel;
    private final String questionText;
    private final int marks;
    private final String source;
    private final String sourceName;

    public PYQQuestion(String id,
                       String examType,
                       String year,
                       String topic,
                       String questionModel,
                       String questionText,
                       int marks,
                       String source,
                       String sourceName) {

        this.id = id;
        this.examType = examType;
        this.year = year;
        this.topic = topic;
        this.questionModel = questionModel;
        this.questionText = questionText;
        this.marks = marks;
        this.source = source;
        this.sourceName = sourceName;
    }

    public String getId() {
        return id;
    }

    public String getExamType() {
        return examType;
    }

    public String getYear() {
        return year;
    }

    public String getTopic() {
        return topic;
    }

    public String getQuestionModel() {
        return questionModel;
    }

    public String getQuestionText() {
        return questionText;
    }

    public int getMarks() {
        return marks;
    }

    public String getSource() {
        return source;
    }

    public String getSourceName() {
        return sourceName;
    }
}