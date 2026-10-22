package com.nexora.academic;

import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Month;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Synchronizes institutional Drive resources into the local searchable academic index. */
@Service
public class AcademicDriveSyncService {
    private static final Pattern YEAR = Pattern.compile("(?:19|20)\\d{2}");
    private final GoogleDriveService drive;
    private final AcademicResourceService resources;
    private final AcademicSubjectService subjects;

    public AcademicDriveSyncService(GoogleDriveService drive, AcademicResourceService resources, AcademicSubjectService subjects) {
        this.drive = drive; this.resources = resources; this.subjects = subjects;
    }

    /** Automatically refresh indexed academic papers nightly without slowing down student page requests. */
    @Scheduled(cron = "0 20 2 * * *", zone = "Asia/Kolkata")
    public void scheduledDriveIndexRefresh() {
        if (!drive.isConfigured()) return;
        java.util.Set<String> codes = new java.util.LinkedHashSet<>();
        try {
            for (AcademicSubject subject : subjects.getAllSubjects()) {
                if (subject != null && subject.getCode() != null && !subject.getCode().isBlank()) codes.add(subject.getCode());
            }
            for (String code : codes) {
                try { syncSubject(code); } catch (Exception ignored) { /* One subject must not stop the remaining nightly refresh. */ }
            }
        } catch (Exception ignored) { /* Missing curriculum/Drive configuration is reported by the manual sync UI. */ }
    }

    public SyncResult syncSubject(String subjectCode) {
        int discovered = 0, processed = 0, skipped = 0, errors = 0;
        if (!drive.isConfigured()) return new SyncResult(0,0,0,0,"Google Drive is not configured.");
        try {
            List<GoogleDriveService.DriveFile> files = drive.getSemester3Files();
            int matchingFiles = 0;
            for (GoogleDriveService.DriveFile f : files) {
                if (f == null || f.getSubjectCode() == null || !subjectCode.equalsIgnoreCase(f.getSubjectCode())) continue;
                if ("application/vnd.google-apps.folder".equals(f.getMimeType())) continue;
                if (!isProcessable(f.getName())) continue;
                matchingFiles++;
                if (f.getResourceType() == null || "IMPORTANT_TOPICS".equals(f.getResourceType())) { skipped++; continue; }
                discovered++;
                try {
                    // A manual sync should not download and re-process every unchanged PDF/PPT
                    // on every click. If the Drive metadata size matches the existing local copy,
                    // the indexed copy is already usable and can be reused immediately.
                    AcademicResource existing = resources.findByDriveFileId(f.getId());
                    if (existing != null && f.getSize() > 0
                            && subjectCode.equalsIgnoreCase(existing.getSubjectCode())
                            && f.getResourceType().equalsIgnoreCase(existing.getResourceType())
                            && f.getName().equals(existing.getOriginalFilename())) {
                        Path localCopy = resources.storagePath(existing.getId());
                        if (localCopy != null && Files.isRegularFile(localCopy)
                                && Files.size(localCopy) == f.getSize()) {
                            skipped++;
                            continue;
                        }
                    }
                    byte[] bytes = drive.downloadFile(f.getId());
                    long id = resources.saveDriveResource(f, bytes, detectMonth(f.getName()), detectYear(f.getName()));
                    if (id > 0) processed++; else skipped++;
                } catch (Exception e) { errors++; }
            }
            String message;
            if (discovered == 0) {
                message = matchingFiles == 0
                        ? "No PDF, PPT, PPTX, DOC or DOCX files were found for this subject. Check the configured Drive root, subject-folder name/code, and folder sharing permissions."
                        : "Supported files were found, but none could be classified as study materials or semester PYQs. Check the Drive folder names (Study Materials / PYQs) and subject mapping.";
            } else {
                message = "Drive sync finished: " + discovered + " files discovered, " + processed
                        + " new/repaired files indexed, " + skipped + " already-indexed or ineligible files skipped, " + errors
                        + " failed. Unchanged local files are reused to keep subsequent syncs fast.";
            }
            return new SyncResult(discovered, processed, skipped, errors, message);
        } catch (Exception e) {
            return new SyncResult(discovered, processed, skipped, errors, "Drive scan unavailable: " + safe(e.getMessage()));
        }
    }

    private boolean isProcessable(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        // Sync the requested study-resource formats only; folders, images and unrelated
        // file types must never appear as learning materials.
        return lower.endsWith(".pdf") || lower.endsWith(".ppt") || lower.endsWith(".pptx")
                || lower.endsWith(".doc") || lower.endsWith(".docx");
    }
    private Integer detectYear(String name) {
        Matcher m = YEAR.matcher(name == null ? "" : name);
        if (!m.find()) return null;
        try { return Integer.parseInt(m.group()); } catch (Exception e) { return null; }
    }
    private String detectMonth(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        for (Month m : Month.values()) {
            String full = m.name().toLowerCase(Locale.ROOT);
            String shortName = full.substring(0,3);
            if (n.contains(full) || n.matches(".*\\b" + shortName + "\\b.*")) return full.substring(0,1).toUpperCase(Locale.ROOT)+full.substring(1);
        }
        return null;
    }
    private String safe(String s) { return s == null ? "unknown error" : s.replaceAll("[\\r\\n]+", " "); }
    public record SyncResult(int discovered, int processed, int skipped, int errors, String message) {}
}
