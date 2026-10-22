package com.nexora.academic;

/** Student-facing subject metadata for syllabus and semester-end resources. */
public class AcademicSubject {
    private final String subjectCode;
    private final String subjectName;
    private final boolean semesterFinalAvailable;
    private final boolean repositoryAvailable;
    private final boolean academicIntelligenceAvailable;
    private final int semesterNumber;
    private final int displayOrder;

    public AcademicSubject(String subjectCode, String subjectName,
                           boolean semesterFinalAvailable, boolean repositoryAvailable,
                           boolean academicIntelligenceAvailable, int semesterNumber, int displayOrder) {
        this.subjectCode = subjectCode;
        this.subjectName = subjectName;
        this.semesterFinalAvailable = semesterFinalAvailable;
        this.repositoryAvailable = repositoryAvailable;
        this.academicIntelligenceAvailable = academicIntelligenceAvailable;
        this.semesterNumber = semesterNumber;
        this.displayOrder = displayOrder;
    }

    public String getSubjectCode() { return subjectCode; }
    public String getSubjectName() { return subjectName; }
    public boolean isSemesterFinalAvailable() { return semesterFinalAvailable; }
    public boolean isRepositoryAvailable() { return repositoryAvailable; }
    public boolean isAcademicIntelligenceAvailable() { return academicIntelligenceAvailable; }
    public int getSemesterNumber() { return semesterNumber; }
    public int getDisplayOrder() { return displayOrder; }
    public String getCode() { return subjectCode; }
    public String getName() { return subjectName; }
}
