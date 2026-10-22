package com.nexora.academic;

public class AcademicCourse {

    private String code;
    private String name;

    private boolean cla1;
    private boolean cla2;
    private boolean semesterFinal;

    private String driveFolderId;

    public AcademicCourse(
            String code,
            String name,
            boolean cla1,
            boolean cla2,
            boolean semesterFinal
    ) {
        this.code = code;
        this.name = name;
        this.cla1 = cla1;
        this.cla2 = cla2;
        this.semesterFinal = semesterFinal;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public boolean isCla1() {
        return cla1;
    }

    public boolean isCla2() {
        return cla2;
    }

    public boolean isSemesterFinal() {
        return semesterFinal;
    }

    public String getDriveFolderId() {
        return driveFolderId;
    }

    public void setDriveFolderId(String driveFolderId) {
        this.driveFolderId = driveFolderId;
    }
}