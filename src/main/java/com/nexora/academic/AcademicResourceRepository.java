package com.nexora.academic;

import java.util.List;

public interface AcademicResourceRepository {
    long save(AcademicResource resource, String storagePath);
    List<AcademicResource> findBySubjectAndType(String subjectCode, String resourceType);
    int countAll();
    AcademicResource findById(long id);
    String findStoragePathById(long id);
    AcademicResource findByDriveFileId(String driveFileId);
    void updateDriveInfo(long id, String driveFileId, String webViewLink);
    void reconcileDriveResource(long id, String subjectCode, String resourceType, String title, String mimeType, String month, Integer year, String driveFileId, String webViewLink, String contentHash);
    void updateStoragePath(long id, String storagePath);
    void updateProcessing(long id, String status, String message, int questionCount);
    AcademicResource findByContentHash(String hash);
    AcademicResource findByUploadSlot(String subjectCode, String resourceType, String month, Integer year, String uploadedFilename);
    void setApproval(long id, String status, String approvedBy);
    List<AcademicResource> findPendingApprovals();
}
