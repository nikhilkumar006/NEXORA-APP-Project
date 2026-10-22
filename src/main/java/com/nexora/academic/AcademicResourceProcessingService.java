package com.nexora.academic;

import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class AcademicResourceProcessingService {
    private final AcademicQuestionExtractionService extractor;
    private final AcademicResourceRepository repository;

    public AcademicResourceProcessingService(AcademicQuestionExtractionService extractor, AcademicResourceRepository repository) {
        this.extractor = extractor;
        this.repository = repository;
    }

    public AcademicQuestionExtractionService.ExtractionResult process(long resourceId, String subjectCode, String resourceType, String filename,
                       Path path, Integer year) {
        // The free PYQ archive is a repository of original papers only. Do not run PDF text
        // extraction/OCR or create extracted-question records for semester papers.
        if ("SEMESTER_PYQS".equalsIgnoreCase(resourceType)) {
            repository.updateProcessing(resourceId, "STORED", "Original semester paper archived. OCR and question extraction are intentionally disabled.", 0);
            return new AcademicQuestionExtractionService.ExtractionResult("", "ORIGINAL_PAPER_NO_OCR", java.util.List.of());
        }
        if ("STUDY_MATERIALS".equalsIgnoreCase(resourceType)) {
            repository.updateProcessing(resourceId, "STORED", "Study material indexed as a source file. Question extraction is not run on learning materials.", 0);
            return new AcademicQuestionExtractionService.ExtractionResult("", "STUDY_MATERIAL_NO_EXTRACTION", java.util.List.of());
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            AcademicQuestionExtractionService.ExtractionResult result =
                    extractor.extract(bytes, filename, subjectCode, resourceType, year, resourceId);
            int count = result.questions().size();
            try { Files.writeString(path.resolveSibling(path.getFileName().toString() + ".txt"), result.text() == null ? "" : result.text()); } catch (Exception ignored) { }
            String message;
            if (result.method().startsWith("OCR_FAILED")) {
                message = "Text extraction found no usable text and OCR could not complete: " + result.method();
                repository.updateProcessing(resourceId, "NEEDS_REVIEW", message, count);
            } else if (count == 0 && ("PYQS".equals(resourceType) || "SEMESTER_PYQS".equals(resourceType)
                    || "IMPORTANT_TOPICS".equals(resourceType) || "TEACHER_QUESTIONS".equals(resourceType))) {
                message = "Document stored, but no numbered questions were confidently detected. Review the source PDF/image.";
                repository.updateProcessing(resourceId, "NEEDS_REVIEW", message, 0);
            } else {
                message = result.method() + " extraction completed; " + count + " question(s) indexed.";
                repository.updateProcessing(resourceId, "PROCESSED", message, count);
            }
            return result;
        } catch (Exception e) {
            repository.updateProcessing(resourceId, "NEEDS_REVIEW", "Processing failed: " + safe(e.getMessage()), 0);
            throw new IllegalStateException("Academic paper processing failed: " + safe(e.getMessage()), e);
        }
    }

    public int count(long resourceId) {
        // The exact count is maintained in the resource row; this fallback is used only after processing.
        AcademicResource r = repository.findById(resourceId);
        return r != null && r.getExtractedQuestionCount() != null ? r.getExtractedQuestionCount() : 0;
    }
    private String safe(String s) { return s == null ? "unknown error" : s.replaceAll("[\\r\\n]+", " "); }
}
