package com.nexora.academic;

import java.time.LocalDateTime;

public class AcademicResource {
    private long id;
    private String subjectCode;
    private String resourceType;
    private String title;
    private String originalFilename;
    private String mimeType;
    private String resourceMonth;
    private Integer resourceYear;
    private String uploadedBy;
    private LocalDateTime createdAt;
    private String driveFileId;
    private String driveWebViewLink;
    private String processingStatus;
    private String processingMessage;
    private Integer extractedQuestionCount;
    private String approvalStatus;
    private String approvedBy;
    private LocalDateTime approvedAt;
    private String contentHash;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getSubjectCode() { return subjectCode; }
    public void setSubjectCode(String subjectCode) { this.subjectCode = subjectCode; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public String getResourceMonth() { return resourceMonth; }
    public void setResourceMonth(String resourceMonth) { this.resourceMonth = resourceMonth; }
    public Integer getResourceYear() { return resourceYear; }
    public void setResourceYear(Integer resourceYear) { this.resourceYear = resourceYear; }
    public String getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(String uploadedBy) { this.uploadedBy = uploadedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getDriveFileId() { return driveFileId; }
    public void setDriveFileId(String driveFileId) { this.driveFileId = driveFileId; }
    public String getDriveWebViewLink() { return driveWebViewLink; }
    public void setDriveWebViewLink(String driveWebViewLink) { this.driveWebViewLink = driveWebViewLink; }
    public String getProcessingStatus() { return processingStatus; }
    public void setProcessingStatus(String processingStatus) { this.processingStatus = processingStatus; }
    public String getProcessingMessage() { return processingMessage; }
    public void setProcessingMessage(String processingMessage) { this.processingMessage = processingMessage; }
    public Integer getExtractedQuestionCount() { return extractedQuestionCount; }
    public void setExtractedQuestionCount(Integer extractedQuestionCount) { this.extractedQuestionCount = extractedQuestionCount; }
    public String getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(String approvalStatus) { this.approvalStatus = approvalStatus; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }

    public String getDisplayPeriod() {
        if (resourceMonth != null && !resourceMonth.isBlank() && resourceYear != null) {
            return resourceMonth + " " + resourceYear;
        }
        if (resourceYear != null) return String.valueOf(resourceYear);
        return "Uploaded resource";
    }
}
