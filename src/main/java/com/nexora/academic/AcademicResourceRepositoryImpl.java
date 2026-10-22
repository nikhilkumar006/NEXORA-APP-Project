package com.nexora.academic;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

@Repository
public class AcademicResourceRepositoryImpl implements AcademicResourceRepository {
    private final JdbcTemplate jdbc;

    public AcademicResourceRepositoryImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long save(AcademicResource resource, String storagePath) {
        jdbc.update("""
            INSERT INTO academic_resources
            (subject_code, resource_type, title, original_filename, mime_type,
             storage_path, resource_month, resource_year, uploaded_by, processing_status, processing_message, extracted_question_count, approval_status, content_hash)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            resource.getSubjectCode(), resource.getResourceType(), resource.getTitle(),
            resource.getOriginalFilename(), resource.getMimeType(), storagePath,
            resource.getResourceMonth(), resource.getResourceYear(), resource.getUploadedBy(),
            resource.getProcessingStatus(), resource.getProcessingMessage(), resource.getExtractedQuestionCount(), resource.getApprovalStatus(), resource.getContentHash());
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return id == null ? 0L : id;
    }

    @Override
    public int countAll() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM academic_resources WHERE resource_type = 'SEMESTER_PYQS' AND COALESCE(approval_status,'APPROVED')='APPROVED'", Integer.class);
        return count == null ? 0 : count;
    }

    @Override
    public List<AcademicResource> findBySubjectAndType(String subjectCode, String resourceType) {
        return jdbc.query("""
            SELECT id, subject_code, resource_type, title, original_filename, mime_type,
                   resource_month, resource_year, uploaded_by, created_at, drive_file_id, drive_web_view_link,
                   processing_status, processing_message, extracted_question_count, approval_status, approved_by, approved_at, content_hash
            FROM academic_resources
            WHERE subject_code = ? AND resource_type = ?
              AND COALESCE(approval_status,'APPROVED')='APPROVED'
            ORDER BY resource_year DESC, id DESC
            """, (rs, rowNum) -> map(rs), subjectCode, resourceType);
    }

    @Override
    public AcademicResource findById(long id) {
        List<AcademicResource> rows = jdbc.query("""
            SELECT id, subject_code, resource_type, title, original_filename, mime_type,
                   resource_month, resource_year, uploaded_by, created_at, drive_file_id, drive_web_view_link,
                   processing_status, processing_message, extracted_question_count, approval_status, approved_by, approved_at, content_hash
            FROM academic_resources WHERE id = ?
            """, (rs, rowNum) -> map(rs), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public String findStoragePathById(long id) {
        List<String> rows = jdbc.query("SELECT storage_path FROM academic_resources WHERE id = ?",
                (rs, rowNum) -> rs.getString("storage_path"), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public AcademicResource findByDriveFileId(String driveFileId) {
        List<AcademicResource> rows = jdbc.query("""
            SELECT id, subject_code, resource_type, title, original_filename, mime_type,
                   resource_month, resource_year, uploaded_by, created_at, drive_file_id, drive_web_view_link,
                   processing_status, processing_message, extracted_question_count, approval_status, approved_by, approved_at, content_hash
            FROM academic_resources WHERE drive_file_id = ?
            """, (rs, rowNum) -> map(rs), driveFileId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public void updateDriveInfo(long id, String driveFileId, String webViewLink) {
        jdbc.update("UPDATE academic_resources SET drive_file_id=?, drive_web_view_link=? WHERE id=?", driveFileId, webViewLink, id);
    }

    @Override
    public void reconcileDriveResource(long id, String subjectCode, String resourceType, String title, String mimeType, String month, Integer year, String driveFileId, String webViewLink, String contentHash) {
        jdbc.update("""
            UPDATE academic_resources
               SET subject_code=?, resource_type=?, title=?, original_filename=?, mime_type=?,
                   resource_month=?, resource_year=?, uploaded_by='GOOGLE_DRIVE',
                   drive_file_id=?, drive_web_view_link=?, content_hash=?,
                   approval_status='APPROVED', approved_by='GOOGLE_DRIVE', approved_at=COALESCE(approved_at, CURRENT_TIMESTAMP)
             WHERE id=?
            """, subjectCode, resourceType, title, title, mimeType, month, year, driveFileId, webViewLink, contentHash, id);
    }

    @Override
    public void updateStoragePath(long id, String storagePath) {
        jdbc.update("UPDATE academic_resources SET storage_path=? WHERE id=?", storagePath, id);
    }

    @Override
    public void updateProcessing(long id, String status, String message, int questionCount) {
        jdbc.update("UPDATE academic_resources SET processing_status=?, processing_message=?, extracted_question_count=? WHERE id=?", status, message, questionCount, id);
    }

    @Override
    public AcademicResource findByContentHash(String hash) {
        if (hash == null || hash.isBlank()) return null;
        List<AcademicResource> rows = jdbc.query("""
            SELECT id, subject_code, resource_type, title, original_filename, mime_type,
                   resource_month, resource_year, uploaded_by, created_at, drive_file_id, drive_web_view_link,
                   processing_status, processing_message, extracted_question_count, approval_status, approved_by, approved_at, content_hash
            FROM academic_resources WHERE content_hash=? LIMIT 1
            """, (rs,n) -> map(rs), hash);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public AcademicResource findByUploadSlot(String subjectCode, String resourceType, String month, Integer year, String uploadedFilename) {
        List<AcademicResource> rows = jdbc.query("""
            SELECT id, subject_code, resource_type, title, original_filename, mime_type,
                   resource_month, resource_year, uploaded_by, created_at, drive_file_id, drive_web_view_link,
                   processing_status, processing_message, extracted_question_count, approval_status, approved_by, approved_at, content_hash
            FROM academic_resources
            WHERE subject_code=? AND resource_type=? AND COALESCE(resource_month,'')=COALESCE(?, '')
              AND COALESCE(resource_year,0)=COALESCE(?,0)
              AND LOWER(original_filename)=LOWER(?)
            LIMIT 1
            """, (rs,n) -> map(rs), subjectCode, resourceType, month, year, uploadedFilename);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public void setApproval(long id, String status, String approvedBy) {
        jdbc.update("UPDATE academic_resources SET approval_status=?, approved_by=?, approved_at=CURRENT_TIMESTAMP WHERE id=?", status, approvedBy, id);
    }

    @Override
    public List<AcademicResource> findPendingApprovals() {
        return jdbc.query("""
            SELECT id, subject_code, resource_type, title, original_filename, mime_type,
                   resource_month, resource_year, uploaded_by, created_at, drive_file_id, drive_web_view_link,
                   processing_status, processing_message, extracted_question_count, approval_status, approved_by, approved_at, content_hash
            FROM academic_resources WHERE approval_status='PENDING' AND resource_type='SEMESTER_PYQS' ORDER BY id DESC
            """, (rs,n) -> map(rs));
    }

    private AcademicResource map(java.sql.ResultSet rs) throws java.sql.SQLException {
        AcademicResource r = new AcademicResource();
        r.setId(rs.getLong("id"));
        r.setSubjectCode(rs.getString("subject_code"));
        r.setResourceType(rs.getString("resource_type"));
        r.setTitle(rs.getString("title"));
        r.setOriginalFilename(rs.getString("original_filename"));
        r.setMimeType(rs.getString("mime_type"));
        r.setResourceMonth(rs.getString("resource_month"));
        int year = rs.getInt("resource_year");
        r.setResourceYear(rs.wasNull() ? null : year);
        r.setUploadedBy(rs.getString("uploaded_by"));
        r.setDriveFileId(rs.getString("drive_file_id"));
        r.setDriveWebViewLink(rs.getString("drive_web_view_link"));
        r.setProcessingStatus(rs.getString("processing_status"));
        r.setProcessingMessage(rs.getString("processing_message"));
        int qc = rs.getInt("extracted_question_count");
        r.setExtractedQuestionCount(rs.wasNull() ? null : qc);
        r.setApprovalStatus(rs.getString("approval_status"));
        r.setApprovedBy(rs.getString("approved_by"));
        Timestamp approved = rs.getTimestamp("approved_at");
        if (approved != null) r.setApprovedAt(approved.toLocalDateTime());
        r.setContentHash(rs.getString("content_hash"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) r.setCreatedAt(ts.toLocalDateTime());
        return r;
    }
}
