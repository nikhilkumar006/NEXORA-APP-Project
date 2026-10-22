package com.nexora.academic;

import com.nexora.xp.XpService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Month;
import java.time.Year;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AcademicResourceService {
    private static final Pattern MONTH = Pattern.compile("(?i)(January|February|March|April|May|June|July|August|September|October|November|December)");

    private static final Set<String> TYPES = Set.of("STUDY_MATERIALS", "PYQS", "SEMESTER_PYQS", "IMPORTANT_TOPICS");
    private static final Pattern YEAR = Pattern.compile("(?:19|20)\\d{2}|2100");
    private static final Pattern SUBJECT_CODE = Pattern.compile("(?i)\\b\\d{2}[A-Z]{2,4}\\d{3}[A-Z]?\\b");
    private static final Pattern MONTH_YEAR_PAIR = Pattern.compile("(?i)\\b(January|Jan|February|Feb|March|Mar|April|Apr|May|June|Jun|July|Jul|August|Aug|September|Sept|Sep|October|Oct|November|Nov|December|Dec)\\b[\\s,./-]{0,20}((?:19|20)\\d{2})|((?:19|20)\\d{2})[\\s,./-]{0,20}\\b(January|Jan|February|Feb|March|Mar|April|Apr|May|June|Jun|July|Jul|August|Aug|September|Sept|Sep|October|Oct|November|Nov|December|Dec)\\b");

    private final AcademicResourceRepository repository;
    private final Path root;
    private final AcademicResourceProcessingService processor;
    private final GoogleDriveUploadService driveUpload;
    private final XpService xp;
    private final AcademicQuestionRepository questionRepository;
    private final TopicService topicService;
    private final AcademicSubjectService subjects;
    private final PaperIntegrityService integrity;
    private final int uploadXp;

    public AcademicResourceService(AcademicResourceRepository repository,
                                   @Value("${nexora.academic.resources-dir:./data/academic-resources}") String root,
                                   AcademicResourceProcessingService processor,
                                   GoogleDriveUploadService driveUpload,
                                   XpService xp,
                                   AcademicQuestionRepository questionRepository,
                                   TopicService topicService, AcademicSubjectService subjects, PaperIntegrityService integrity,
                                   @Value("${nexora.academic.upload-xp:10}") int uploadXp) {
        this.repository = repository;
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.processor = processor;
        this.driveUpload = driveUpload;
        this.xp = xp;
        this.questionRepository = questionRepository;
        this.topicService = topicService;
        this.subjects = subjects;
        this.integrity = integrity;
        this.uploadXp = uploadXp;
    }

    public boolean isDriveUploadConfigured() { return driveUpload.isConfigured(); }

    public List<AcademicResource> find(String subjectCode, String type) { return repository.findBySubjectAndType(subjectCode, type); }
    public int countAll() { return repository.countAll(); }
    public AcademicResource find(long id) { return repository.findById(id); }
    public AcademicResource findByDriveFileId(String id) { return repository.findByDriveFileId(id); }
    public List<AcademicResource> findPendingApprovals() { return repository.findPendingApprovals(); }

    /**
     * Student upload: validate filename, file type, size and duplication. Original semester
     * papers are archived without OCR; other supported evidence types use their configured
     * extraction workflow. XP is awarded only after Drive archiving succeeds.
     */
    public long save(String subjectCode, String type, MultipartFile file,
                     String month, Integer year, String uploadedBy) throws IOException {
        validate(subjectCode, type, file);
        if ("SEMESTER_PYQS".equals(type) && integrity.isBanned(uploadedBy)) {
            throw new IllegalArgumentException("This account is permanently disabled for repeated paper-metadata tampering.");
        }
        byte[] bytes = file.getBytes();
        String original = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        if (original.isBlank()) throw new IllegalArgumentException("Please choose a valid PDF or image file.");
        if ("SEMESTER_PYQS".equals(type) && (!original.toLowerCase(Locale.ROOT).endsWith(".pdf")
                || original.contains("/") || original.contains("\\"))) {
            throw new IllegalArgumentException("Give the original question paper a descriptive PDF filename that includes its exam year, for example DSA_May_2025.pdf.");
        }

        Integer detectedYear = year != null ? year : detectYear(original);
        if (detectedYear == null || detectedYear < 2000 || detectedYear > 2100) {
            throw new IllegalArgumentException("Enter the academic year for this paper.");
        }
        String detectedMonth = "IMPORTANT_TOPICS".equals(type) ? "Topics-" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) : normalizeMonth(month == null || month.isBlank() ? detectMonth(original) : month);
        if ("SEMESTER_PYQS".equals(type) && (detectedMonth == null || detectedMonth.isBlank())) detectedMonth = "Exam";
        String extension = extension(original);
        // Keep the student's descriptive filename unchanged for the original-paper archive.
        String canonical = "SEMESTER_PYQS".equals(type) ? original : canonicalFilename(subjectCode, type, detectedYear, detectedMonth, extension);

        String hash = sha256(bytes);
        AcademicResource duplicate = repository.findByContentHash(hash);
        if (duplicate != null && !"IMPORTANT_TOPICS".equals(type)) {
            throw new IllegalArgumentException("This paper is already present in the NEXORA repository.");
        }
        AcademicResource slotDuplicate = repository.findByUploadSlot(subjectCode, type, detectedMonth, detectedYear, canonical);
        if (slotDuplicate != null) {
            throw new IllegalArgumentException("A paper for this subject, exam type and year is already present.");
        }

        String safeName = UUID.randomUUID() + "-" + canonical;
        Path dir = root.resolve(subjectCode).resolve(type).normalize();
        if (!dir.startsWith(root)) throw new IllegalArgumentException("Invalid resource path.");
        Files.createDirectories(dir);
        Path target = dir.resolve(safeName).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("Invalid resource path.");
        Files.write(target, bytes);

        AcademicResource r = new AcademicResource();
        r.setSubjectCode(subjectCode); r.setResourceType(type); r.setTitle(canonical); r.setOriginalFilename(canonical);
        r.setMimeType(file.getContentType() == null ? detectMime(original) : file.getContentType());
        r.setResourceMonth(detectedMonth); r.setResourceYear(detectedYear);
        r.setUploadedBy(uploadedBy == null || uploadedBy.isBlank() ? "STUDENT" : uploadedBy);
        r.setProcessingStatus("PROCESSING");
        r.setProcessingMessage("Checking the paper and extracting questions.");
        r.setExtractedQuestionCount(0);
        r.setApprovalStatus("VALIDATING");
        r.setContentHash(hash);

        long id = 0L;
        int extractedCount = 0;
        try {
            id = repository.save(r, target.toString());
            AcademicQuestionExtractionService.ExtractionResult result =
                    processor.process(id, subjectCode, type, canonical, target, detectedYear);
            extractedCount = result.questions() == null ? 0 : result.questions().size();

            if ("IMPORTANT_TOPICS".equals(type)) {
                List<ExtractedAcademicQuestion> mappedTopics = extractOrderedSyllabusTopics(subjectCode, result.text());
                if (mappedTopics.isEmpty()) throw new IllegalArgumentException("No syllabus topics could be read from this image/PDF. Upload a clearer photo with topic names matching the subject syllabus.");
                questionRepository.replaceForResource(id, subjectCode, type, detectedYear, result.method(), mappedTopics);
                extractedCount = mappedTopics.size();
                repository.updateProcessing(id, "PROCESSED", "Topic list OCR completed; " + mappedTopics.size() + " syllabus topics mapped in source order.", extractedCount);
            } else if (!"SEMESTER_PYQS".equals(type)) {
                validateExtractedPaper(subjectCode, type, result);
            }

            if ("IMPORTANT_TOPICS".equals(type)) {
                // Topic-list photos/PDFs are personal analysis inputs, not shared PYQ contributions.
                // Store and process locally so Premium topic mapping does not depend on Drive write access.
                repository.updateProcessing(id, "APPROVED", "Personal topic list analyzed locally; " + extractedCount + " topics mapped in source order.", extractedCount);
                repository.setApproval(id, "APPROVED", "AUTO_VALIDATOR");
                return id;
            }

            String driveId = driveUpload.upload(bytes, canonical, r.getMimeType(), subjectCode, type, detectedYear, detectedMonth, r.getUploadedBy());
            if (driveId == null || driveId.isBlank()) {
                repository.updateProcessing(id, "NEEDS_REVIEW", "The paper passed local checks, but Drive archive could not be completed.", extractedCount);
                throw new IllegalStateException("The paper was read successfully, but Google Drive upload could not be completed. Check the service-account Drive access.");
            }

            repository.updateDriveInfo(id, driveId, "https://drive.google.com/open?id=" + driveId);
            repository.updateProcessing(id, "APPROVED", "Paper validated, archived to Drive and indexed.", extractedCount);
            repository.setApproval(id, "APPROVED", "AUTO_VALIDATOR");
            xp.awardUploadApproval(r.getUploadedBy(), id, uploadXp, canonical);
            return id;
        } catch (RuntimeException ex) {
            if (id > 0) {
                try { questionRepository.deleteForResource(id);
                    repository.updateProcessing(id, "REJECTED", ex.getMessage() == null ? "Upload could not be completed." : ex.getMessage(), extractedCount); } catch (Exception ignored) { }
                try { repository.setApproval(id, "REJECTED", "AUTO_VALIDATOR"); } catch (Exception ignored) { }
            }
            try { Files.deleteIfExists(target); } catch (Exception ignored) { }
            throw ex;
        }
    }

    private void verifySemesterPaperMetadata(String subjectCode, String expectedMonth, Integer expectedYear, String rawText, String registrationNo) {
        String header = rawText == null ? "" : rawText.substring(0, Math.min(2500, rawText.length()));
        String upperHeader = header.toUpperCase(Locale.ROOT);
        String expectedCode = subjectCode.toUpperCase(Locale.ROOT);
        Matcher codes = SUBJECT_CODE.matcher(upperHeader);
        boolean codeMatched = false;
        while (codes.find()) {
            String found = codes.group().toUpperCase(Locale.ROOT);
            if (!found.equals(expectedCode)) {
                PaperIntegrityService.ViolationResult strike = integrity.recordMetadataViolation(registrationNo,
                        "The paper contains subject code " + found + " but was uploaded under " + expectedCode);
                throw new IllegalArgumentException(strike.message());
            }
            codeMatched = true;
        }

        AcademicSubject expectedSubject = subjects.getSubjectByCode(subjectCode);
        String expectedName = expectedSubject == null ? "" : expectedSubject.getSubjectName();
        String normalizedHeader = normalizeMetadata(header);
        boolean titleMatched = !expectedName.isBlank() && normalizedHeader.contains(normalizeMetadata(expectedName));
        for (AcademicSubject other : subjects.getAllSubjects()) {
            if (other == null || other.getCode() == null || other.getCode().equalsIgnoreCase(subjectCode)) continue;
            String otherCode = other.getCode().toUpperCase(Locale.ROOT);
            if (upperHeader.contains(otherCode)) {
                PaperIntegrityService.ViolationResult strike = integrity.recordMetadataViolation(registrationNo,
                        "The paper contains another subject code " + otherCode + " but was uploaded under " + expectedCode);
                throw new IllegalArgumentException(strike.message());
            }
            String otherName = other.getSubjectName();
            if (otherName != null && !otherName.isBlank() && normalizedHeader.contains(normalizeMetadata(otherName)) && !titleMatched) {
                PaperIntegrityService.ViolationResult strike = integrity.recordMetadataViolation(registrationNo,
                        "The paper title matches another subject (" + otherName + ") rather than " + (expectedName.isBlank() ? expectedCode : expectedName));
                throw new IllegalArgumentException(strike.message());
            }
        }
        if (!codeMatched && !titleMatched) {
            throw new IllegalArgumentException("The paper's subject code/name could not be verified from the document header. No XP was awarded. Upload a clearer complete paper.");
        }

        Matcher date = MONTH_YEAR_PAIR.matcher(header);
        String foundMonth = null; Integer foundYear = null;
        if (date.find()) {
            foundMonth = normalizeMonth(date.group(1) != null ? date.group(1) : date.group(4));
            String yearText = date.group(2) != null ? date.group(2) : date.group(3);
            try { foundYear = Integer.parseInt(yearText); } catch (Exception ignored) { }
        }
        if (foundMonth == null || foundYear == null) {
            throw new IllegalArgumentException("The exam month/year could not be verified from the paper header. No XP was awarded; upload a clear scan showing the original exam date.");
        }
        if (expectedYear == null || !expectedYear.equals(foundYear) || expectedMonth == null || !expectedMonth.equalsIgnoreCase(foundMonth)) {
            PaperIntegrityService.ViolationResult strike = integrity.recordMetadataViolation(registrationNo,
                    "Paper header shows " + foundMonth + " " + foundYear + " but the submitted archive metadata says " + expectedMonth + " " + expectedYear);
            throw new IllegalArgumentException(strike.message());
        }
    }

    private String normalizeMetadata(String value) {
        return value == null ? "" : java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFKC)
                .toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private void validateExtractedPaper(String subjectCode, String type, AcademicQuestionExtractionService.ExtractionResult result) {
        if (result == null) throw new IllegalArgumentException("The paper could not be analyzed.");
        String text = result.text() == null ? "" : result.text().replaceAll("\\s+", " ").trim();
        if (text.length() < 80) throw new IllegalArgumentException("Paper rejected: the document is not clearly readable. Upload a clear PDF/scan with visible question text.");
        if (result.method() != null && result.method().startsWith("OCR_FAILED")) throw new IllegalArgumentException("Paper rejected: OCR could not read the document clearly.");
        int questions = result.questions() == null ? 0 : result.questions().size();
        if (questions < 2) throw new IllegalArgumentException("Paper rejected: fewer than 2 usable questions were detected. Upload the complete, clearly visible question paper.");
        long mapped = result.questions().stream().filter(q -> q.getTopic() != null && !q.getTopic().isBlank() && !q.getTopic().equalsIgnoreCase("Unmapped / review") && q.getConfidence() >= 0.45).count();
        if (mapped < Math.max(1, Math.ceil(questions * 0.35))) throw new IllegalArgumentException("Paper rejected: the extracted questions do not contain enough evidence that this is a valid " + subjectCode + " paper. Check the subject and upload a clearer/correct paper.");
    }

    private List<ExtractedAcademicQuestion> extractOrderedSyllabusTopics(String subjectCode, String rawText) {
        if (rawText == null || rawText.replaceAll("\\s+", "").length() < 8) return List.of();
        List<String> syllabusTopics = new java.util.ArrayList<>();
        for (AcademicUnit unit : topicService.getUnitsForSubject(subjectCode)) {
            if (unit.getTopics() == null) continue;
            for (AcademicTopic topic : unit.getTopics()) if (topic.getTopic() != null && !topic.getTopic().isBlank()) syllabusTopics.add(topic.getTopic().trim());
        }
        if (syllabusTopics.isEmpty()) return List.of();
        String text = rawText.replace('\u00a0', ' ');
        List<String> ordered = new java.util.ArrayList<>();
        for (String line : text.split("[\\r\\n]+")) {
            String cleanLine = line.replaceFirst("(?i)^\\s*(?:[0-9]+[.)、:-]?|[-*•])\\s*", "").trim();
            if (cleanLine.isBlank()) continue;
            String normalizedLine = normalizeTopicText(cleanLine);
            if (normalizedLine.length() < 3) continue;
            List<java.util.Map.Entry<String,Integer>> lineMatches = new java.util.ArrayList<>();
            for (String topic : syllabusTopics) {
                String normalizedTopic = normalizeTopicText(topic);
                int position = normalizedTopic.length() >= 3 ? normalizedLine.indexOf(normalizedTopic) : -1;
                if (position >= 0) lineMatches.add(new java.util.AbstractMap.SimpleEntry<>(topic, position));
                else if (normalizedLine.length() >= 6 && normalizedTopic.length() >= 6 && normalizedTopic.contains(normalizedLine))
                    lineMatches.add(new java.util.AbstractMap.SimpleEntry<>(topic, 0));
            }
            lineMatches.sort(java.util.Map.Entry.comparingByValue());
            for (var match : lineMatches) if (!ordered.contains(match.getKey())) ordered.add(match.getKey());
            if (lineMatches.isEmpty()) {
                String best = null; double bestScore = 0;
                java.util.Set<String> lineWords = new java.util.HashSet<>(java.util.Arrays.asList(normalizedLine.split(" ")));
                for (String topic : syllabusTopics) {
                    String normalizedTopic = normalizeTopicText(topic);
                    java.util.Set<String> topicWords = new java.util.HashSet<>(java.util.Arrays.asList(normalizedTopic.split(" ")));
                    topicWords.removeIf(w -> w.length() < 3);
                    if (topicWords.isEmpty()) continue;
                    long overlap = topicWords.stream().filter(lineWords::contains).count();
                    double score = overlap / (double) topicWords.size();
                    if (score > bestScore) { bestScore = score; best = topic; }
                }
                if (best != null && bestScore >= 0.65 && !ordered.contains(best)) ordered.add(best);
            }
        }
        // OCR may flatten a PDF into one line. Fall back to occurrence order in the full text.
        if (ordered.isEmpty()) {
            String normalized = normalizeTopicText(text);
            java.util.Map<String,Integer> positions = new java.util.HashMap<>();
            for (String topic : syllabusTopics) {
                String normalizedTopic = normalizeTopicText(topic);
                int at = normalizedTopic.length() >= 3 ? normalized.indexOf(normalizedTopic) : -1;
                if (at >= 0) positions.put(topic, at);
            }
            positions.entrySet().stream().sorted(java.util.Map.Entry.comparingByValue()).forEach(e -> { if (!ordered.contains(e.getKey())) ordered.add(e.getKey()); });
        }
        List<ExtractedAcademicQuestion> result = new java.util.ArrayList<>(); int n = 1;
        for (String topic : ordered) result.add(new ExtractedAcademicQuestion(Integer.toString(n++), topic, topic, "TOPIC_LIST", 0, 0.9));
        return List.copyOf(result);
    }

    private String normalizeTopicText(String value) {
        return value == null ? "" : java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").replaceAll("\\s+", " ").trim();
    }

    /** Drive-originated resources are trusted institutional evidence. Existing records are reconciled, not skipped. */
    public long saveDriveResource(GoogleDriveService.DriveFile file, byte[] bytes, String month, Integer year) throws IOException {
        if (file == null || file.getSubjectCode() == null || file.getResourceType() == null) return 0;
        AcademicResource existing = repository.findByDriveFileId(file.getId());
        String original = StringUtils.cleanPath(file.getName());
        String hash = sha256(bytes);
        Integer normalizedYear = year != null ? year : detectYear(original);
        String normalizedMonth = month == null || month.isBlank() ? detectMonth(original) : normalizeMonth(month);

        // A previous version could have stored the same Drive paper under a generic type.
        // Reconcile that record in-place so the current archive immediately sees it.
        AcademicResource sameContent = repository.findByContentHash(hash);
        if (existing == null && sameContent != null) {
            Path existingPath = storagePath(sameContent.getId());
            if (existingPath == null || !Files.isRegularFile(existingPath)) {
                Path dir = root.resolve(file.getSubjectCode()).resolve(file.getResourceType()).normalize();
                Files.createDirectories(dir);
                existingPath = dir.resolve("drive-" + file.getId() + "-" + original.replaceAll("[^a-zA-Z0-9._() -]", "_")).normalize();
                if (!existingPath.startsWith(root)) throw new IllegalArgumentException("Invalid Drive resource path.");
                Files.write(existingPath, bytes);
                repository.updateStoragePath(sameContent.getId(), existingPath.toString());
            }
            repository.reconcileDriveResource(sameContent.getId(), file.getSubjectCode(), file.getResourceType(), original, file.getMimeType(), normalizedMonth, normalizedYear, file.getId(), file.getWebViewLink(), hash);
            processor.process(sameContent.getId(), file.getSubjectCode(), file.getResourceType(), original, existingPath, normalizedYear);
            return sameContent.getId();
        }

        String safeName = "drive-" + file.getId() + "-" + original.replaceAll("[^a-zA-Z0-9._() -]", "_");
        Path dir = root.resolve(file.getSubjectCode()).resolve(file.getResourceType()).normalize();
        if (!dir.startsWith(root)) throw new IllegalArgumentException("Invalid resource path.");
        Files.createDirectories(dir);
        Path target = dir.resolve(safeName).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("Invalid resource path.");

        if (existing != null) {
            Path existingPath = storagePath(existing.getId());
            if (existingPath == null || !Files.isRegularFile(existingPath)) {
                Files.write(target, bytes);
                repository.updateStoragePath(existing.getId(), target.toString());
                existingPath = target;
            }
            repository.reconcileDriveResource(existing.getId(), file.getSubjectCode(), file.getResourceType(), original, file.getMimeType(), normalizedMonth, normalizedYear, file.getId(), file.getWebViewLink(), hash);
            processor.process(existing.getId(), file.getSubjectCode(), file.getResourceType(), original, existingPath, normalizedYear);
            return existing.getId();
        }

        Files.write(target, bytes);
        AcademicResource r = new AcademicResource();
        r.setSubjectCode(file.getSubjectCode()); r.setResourceType(file.getResourceType()); r.setTitle(original);
        r.setOriginalFilename(original); r.setMimeType(file.getMimeType());
        r.setResourceMonth(normalizedMonth); r.setResourceYear(normalizedYear);
        r.setUploadedBy("GOOGLE_DRIVE"); r.setDriveFileId(file.getId()); r.setDriveWebViewLink(file.getWebViewLink());
        r.setProcessingStatus("PROCESSING"); r.setProcessingMessage("Drive paper received; indexing questions."); r.setExtractedQuestionCount(0);
        r.setApprovalStatus("APPROVED"); r.setContentHash(hash);
        long id = repository.save(r, target.toString());
        processor.process(id, r.getSubjectCode(), r.getResourceType(), original, target, r.getResourceYear());
        return id;
    }

    /** Admin approval: archive the exact local paper to Drive, then award XP exactly once. */
    public void approve(long id, String adminRegistration) {
        AcademicResource r = repository.findById(id);
        if (r == null) throw new IllegalArgumentException("Academic resource not found.");
        if (!"SEMESTER_PYQS".equalsIgnoreCase(r.getResourceType())) {
            throw new IllegalArgumentException("Only semester-end previous-year papers can be approved from the student archive.");
        }
        if (!"PENDING".equalsIgnoreCase(r.getApprovalStatus())) return;
        Path path = storagePath(id);
        if (path == null || !Files.isRegularFile(path)) throw new IllegalStateException("Uploaded paper file is missing.");
        try {
            String driveId = driveUpload.upload(Files.readAllBytes(path), r.getOriginalFilename(), r.getMimeType(), r.getSubjectCode(), r.getResourceType(), r.getResourceYear(), r.getResourceMonth(), r.getUploadedBy());
            if (driveId == null) throw new IllegalStateException("Drive write access is not configured. Configure the Drive service account before approving this paper.");
            repository.updateDriveInfo(id, driveId, "https://drive.google.com/open?id=" + driveId);
            repository.setApproval(id, "APPROVED", adminRegistration);
            xp.awardUploadApproval(r.getUploadedBy(), id, uploadXp, r.getOriginalFilename());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to archive the approved paper: " + e.getMessage(), e);
        }
    }

    public void reject(long id, String adminRegistration) {
        AcademicResource r = repository.findById(id);
        if (r == null) throw new IllegalArgumentException("Academic resource not found.");
        if (!"SEMESTER_PYQS".equalsIgnoreCase(r.getResourceType())) {
            throw new IllegalArgumentException("Only semester-end previous-year papers are handled by the student archive.");
        }
        if (!"PENDING".equalsIgnoreCase(r.getApprovalStatus())) return;
        repository.setApproval(id, "REJECTED", adminRegistration);
    }

    public Path extractedTextPath(long id) {
        Path original = storagePath(id); if (original == null) return null;
        Path text = original.resolveSibling(original.getFileName().toString() + ".txt").normalize();
        return text.startsWith(root) && Files.isRegularFile(text) ? text : null;
    }
    public void reprocess(long id) { AcademicResource r=repository.findById(id); if(r==null)return; Path path=storagePath(id); if(path==null||!Files.isRegularFile(path))return; processor.process(id,r.getSubjectCode(),r.getResourceType(),r.getOriginalFilename(),path,r.getResourceYear()); }
    public Path storagePath(long id) { String stored=repository.findStoragePathById(id); if(stored==null||stored.isBlank())return null; Path path=Path.of(stored).toAbsolutePath().normalize(); return path.startsWith(root)?path:null; }

    private void validate(String subjectCode, String type, MultipartFile file) {
        if (subjectCode == null || subjectCode.isBlank()) throw new IllegalArgumentException("Subject is required.");
        if (!TYPES.contains(type)) throw new IllegalArgumentException("Unsupported academic resource type.");
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Please choose a file.");
        if (file.getSize() > 20L * 1024 * 1024) throw new IllegalArgumentException("File must be 20 MB or smaller.");
        String name=file.getOriginalFilename()==null?"":file.getOriginalFilename();
        if(!name.toLowerCase(Locale.ROOT).matches(".*\\.(pdf|png|jpe?g|webp|bmp|tiff?)$")) throw new IllegalArgumentException("Academic archive accepts PDF or image files only.");
        if ("SEMESTER_PYQS".equals(type) && !name.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new IllegalArgumentException("Semester PYQ uploads must be original PDF files with a descriptive filename containing the exam year.");
        }
        if (!"SEMESTER_PYQS".equals(type) && !"IMPORTANT_TOPICS".equals(type)) {
            throw new IllegalArgumentException("This upload type is not available to students.");
        }
    }

    private String canonicalFilename(String subjectCode, String type, Integer year, String month, String extension) {
        if ("SEMESTER_PYQS".equals(type)) return (month == null || month.isBlank() ? "Unknown" : month) + "_" + year + ".pdf";
        if ("IMPORTANT_TOPICS".equals(type)) return subjectCode + "_IMPORTANT_TOPICS_" + year + "_" + (month == null ? "Upload" : month) + "." + extension;
        throw new IllegalArgumentException("Unsupported student paper type.");
    }

    private String normalizeMonth(String month){if(month==null||month.isBlank())return null;String m=month.trim();for(Month x:Month.values())if(x.name().equalsIgnoreCase(m)||x.name().substring(0,3).equalsIgnoreCase(m.substring(0,Math.min(3,m.length()))))return x.name().substring(0,1)+x.name().substring(1).toLowerCase(Locale.ROOT);return m;}

    private Integer detectYear(String name){
        Matcher m=YEAR.matcher(name==null?"":name);
        if(!m.find()) return null;
        try{return Integer.parseInt(m.group());}catch(Exception e){return null;}
    }

    private String detectMonth(String name){
        String n=name==null?"":name.toLowerCase(Locale.ROOT);
        for(Month m:Month.values()){
            String full=m.name().toLowerCase(Locale.ROOT);
            String shortName=full.substring(0,3);
            if(n.startsWith(full+"_") || n.startsWith(shortName+"_") || n.matches(".*\\b"+full+"\\b.*") || n.matches(".*\\b"+shortName+"\\b.*")){
                return full.substring(0,1).toUpperCase(Locale.ROOT)+full.substring(1);
            }
        }
        return null;
    }
    private String detectMime(String filename){try{String m=Files.probeContentType(Path.of(filename));if(m!=null)return m;}catch(Exception ignored){}return "application/octet-stream";}
    private String sha256(byte[] bytes){try{byte[] d=MessageDigest.getInstance("SHA-256").digest(bytes);StringBuilder b=new StringBuilder();for(byte x:d)b.append(String.format("%02x",x));return b.toString();}catch(Exception e){throw new IllegalStateException("Unable to fingerprint upload.",e);}}
    private String extension(String filename) {
        String n = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        int dot = n.lastIndexOf('.');
        if (dot < 0) return "pdf";
        String ext = n.substring(dot + 1);
        return Set.of("pdf","png","jpg","jpeg","webp","bmp","tif","tiff").contains(ext) ? ext : "pdf";
    }
}
