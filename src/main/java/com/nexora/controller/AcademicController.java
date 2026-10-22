package com.nexora.controller;

import com.nexora.academic.AcademicSubject;
import com.nexora.academic.AcademicSubjectService;
import com.nexora.academic.AcademicResource;
import com.nexora.academic.AcademicResourceService;
import com.nexora.academic.AcademicPageReadCache;
import com.nexora.academic.ChapterTestService;
import com.nexora.academic.AcademicDriveSyncService;
import com.nexora.academic.AcademicUnit;
import com.nexora.academic.GoogleDriveService;
import com.nexora.academic.PYQExamStats;
import com.nexora.academic.PYQModelStats;
import com.nexora.academic.PYQQuestion;
import com.nexora.academic.PYQService;
import com.nexora.academic.Topic;
import com.nexora.academic.TopicDependency;
import com.nexora.academic.TopicService;
import com.nexora.academic.AcademicPeriod;
import com.nexora.repository.StudentRepository;
import com.nexora.subscription.SubscriptionService;
import com.nexora.document.OcrService;

import org.springframework.stereotype.Controller;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.ImageType;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.File;
import org.springframework.core.io.InputStreamResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Comparator;

@Controller
public class AcademicController {
    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(AcademicController.class.getName());

    private final GoogleDriveService googleDriveService;
    private final TopicService topicService;
    private final PYQService pyqService;
    private final AcademicSubjectService academicSubjectService;
    private final AcademicResourceService academicResourceService;
    private final AcademicPageReadCache academicPageReadCache;
    private final AcademicDriveSyncService academicDriveSyncService;
    private final StudentRepository studentRepository;
    private final SubscriptionService subscriptions;
    private final JdbcTemplate jdbc;
    private final OcrService ocr;
    private final ChapterTestService chapterTests;

    public AcademicController(
            GoogleDriveService googleDriveService,
            TopicService topicService,
            PYQService pyqService,
            AcademicSubjectService academicSubjectService,
            AcademicResourceService academicResourceService,
            AcademicPageReadCache academicPageReadCache,
            AcademicDriveSyncService academicDriveSyncService,
            StudentRepository studentRepository,
            SubscriptionService subscriptions, JdbcTemplate jdbc, OcrService ocr, ChapterTestService chapterTests) {

        this.googleDriveService = googleDriveService;
        this.topicService = topicService;
        this.pyqService = pyqService;
        this.academicSubjectService = academicSubjectService;
        this.academicResourceService = academicResourceService;
        this.academicPageReadCache = academicPageReadCache;
        this.academicDriveSyncService = academicDriveSyncService;
        this.studentRepository = studentRepository;
        this.subscriptions = subscriptions;
        this.jdbc = jdbc;
        this.ocr = ocr;
        this.chapterTests = chapterTests;
    }


    // ============================================================
    // ACADEMIC HOME
    // ============================================================

    @GetMapping("/academic")
    public String academicHome(Principal principal, Model model) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            return "redirect:/login";
        }
        var student = studentRepository.findByLoginId(principal.getName()).orElse(null);
        if (student == null) {
            return "redirect:/login?error=account";
        }

        // The cohort and current IST date are the only source of the current semester.
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));
        if (student.getBatchStartYear() < 2000 || student.getBatchStartYear() > today.getYear()) {
            return "redirect:/profile?error=academic-cohort";
        }
        AcademicPeriod.Current period = AcademicPeriod.calculate(student.getBatchStartYear(), today);
        // Cache the lightweight subject catalogue independently from subject detail data.
        // This avoids repeated SQL round-trips when students navigate back to Academic Home.
        List<AcademicSubject> subjects = safeSubjectsForSemester(period.semester());
        if (subjects == null) subjects = List.of();
        // Official Semester 3 course catalogue supplied for this implementation. This fallback
        // adds subject names only; units, questions, resources and counts remain database-backed.
        if (subjects.isEmpty() && period.semester() == 3) {
            subjects = List.of(
                    new AcademicSubject("21MAB201T", "Transforms and Boundary Value Problems", true, true, true, 3, 1),
                    new AcademicSubject("21DCS201P", "Design Thinking Methodology", false, true, true, 3, 2),
                    new AcademicSubject("21CSS201T", "Computer Organization and Architecture", true, true, true, 3, 3),
                    new AcademicSubject("21CSC201J", "Data Structures and Algorithms", true, true, true, 3, 4),
                    new AcademicSubject("21CSC202J", "Operating System", true, true, true, 3, 5),
                    new AcademicSubject("21CSC203P", "Advanced Programming Practice", false, true, true, 3, 6)
            );
        }

        // Keep the landing page deliberately lightweight. It is a navigation page, not the
        // subject analytics page: loading every unit, resource and PYQ for every subject here
        // caused an N+1 query burst and made Academic Home wait for data it did not display.
        // Full syllabus, material and question data are loaded by the selected subject routes.

        model.addAttribute("student", student);
        model.addAttribute("studentInitial", student.getFullName() == null || student.getFullName().isBlank()
                ? "N" : student.getFullName().trim().substring(0, 1).toUpperCase(java.util.Locale.ROOT));
        int yearNumber = period.yearOfStudy();
        String yearOrdinal = yearNumber + (yearNumber % 100 >= 11 && yearNumber % 100 <= 13 ? "th" : switch (yearNumber % 10) {
            case 1 -> "st"; case 2 -> "nd"; case 3 -> "rd"; default -> "th";
        });
        model.addAttribute("yearOfStudyLabel", yearOrdinal + " Year");
        model.addAttribute("semester", period.semester());
        model.addAttribute("academicPeriod", period);
        model.addAttribute("academicPeriodLabel", period.label());
        model.addAttribute("academicBreakPeriod", period.breakPeriod());
        model.addAttribute("academicYearRange", period.academicYear() + "–" + String.valueOf(period.academicYear() + 1).substring(2));
        model.addAttribute("semesterType", period.semesterType());
        model.addAttribute("yearOfStudy", period.yearOfStudy());
        model.addAttribute("subjects", subjects);
        model.addAttribute("currentYear", today.getYear());
        model.addAttribute("academicDate", today);
        return "academic";
    }

    // ============================================================
    // SUBJECT PAGE
    // ============================================================

    @GetMapping("/academic/{subjectCode}")
    public String subjectPage(
            @PathVariable("subjectCode") String subjectCode,
            @RequestParam(required = false) String error,
            Principal principal,
            Model model) {

        AcademicSubject subject = findSubject(subjectCode);

        if (subject == null) {
            return "redirect:/academic";
        }

        // IMPORTANT: Do not synchronize/download/process Drive files while rendering the
        // subject page. A GET request must remain fast and must not trigger OCR or
        // question extraction for every unindexed Drive document. Drive synchronization
        // is intentionally triggered only by the explicit "Sync & Analyze Drive" POST
        // action below.
        // Keep subject navigation fast and network-independent. Drive discovery,
        // downloads and OCR are never performed during GET page rendering.
        // Explicit Drive sync persists discovered resources in academic_resources,
        // which are rendered below through the local repository.
        model.addAttribute("subject", subject);
        model.addAttribute("premiumFeatures", hasPremium(principal));
        model.addAttribute("currentYear", java.time.Year.now().getValue());
        model.addAttribute("units", safeUnits(subjectCode));
        List<AcademicResource> subjectMaterials;
        try { subjectMaterials = safeResources(subjectCode, "STUDY_MATERIALS"); }
        catch (RuntimeException ex) { LOGGER.log(java.util.logging.Level.WARNING, "Could not load indexed study materials for " + subjectCode, ex); subjectMaterials = List.of(); }
        if (subjectMaterials == null) subjectMaterials = List.of();
        subjectMaterials = subjectMaterials.stream().filter(java.util.Objects::nonNull)
                .filter(resource -> {
                    String filename = resource.getOriginalFilename();
                    if (filename == null) return false;
                    String lower = filename.toLowerCase(java.util.Locale.ROOT);
                    return lower.endsWith(".pdf") || lower.endsWith(".ppt") || lower.endsWith(".pptx")
                            || lower.endsWith(".doc") || lower.endsWith(".docx");
                })
                .sorted(Comparator.comparing(AcademicResource::getOriginalFilename, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))).toList();
        model.addAttribute("subjectMaterials", subjectMaterials);
        // Group subject-wide learning resources into syllabus units using explicit chapter/unit
        // markers in filenames (for example "Unit 1 - ...pptx" or "Chapter 2.pdf").
        // Every supported file is retained; files without a reliable marker stay in an
        // "Other / not chapter-labelled" group instead of being silently omitted.
        List<Map<String,Object>> materialGroups = new ArrayList<>();
        List<AcademicUnit> materialUnits = safeUnits(subjectCode);
        Map<Integer,List<AcademicResource>> byUnit = new LinkedHashMap<>();
        for (int i = 1; i <= materialUnits.size(); i++) byUnit.put(i, new ArrayList<>());
        List<AcademicResource> unclassifiedMaterials = new ArrayList<>();
        for (AcademicResource material : subjectMaterials) {
            int unitNumber = inferMaterialUnit(material.getOriginalFilename());
            if (unitNumber > 0 && byUnit.containsKey(unitNumber)) byUnit.get(unitNumber).add(material);
            else unclassifiedMaterials.add(material);
        }
        for (int i = 0; i < materialUnits.size(); i++) {
            int unitNumber = i + 1;
            Map<String,Object> group = new LinkedHashMap<>();
            group.put("label", materialUnits.get(i).getUnitName());
            group.put("unitNumber", unitNumber);
            group.put("materials", byUnit.getOrDefault(unitNumber, List.of()));
            if (!byUnit.getOrDefault(unitNumber, List.of()).isEmpty()) materialGroups.add(group);
        }
        if (!unclassifiedMaterials.isEmpty()) {
            Map<String,Object> group = new LinkedHashMap<>();
            group.put("label", "Other / not chapter-labelled");
            group.put("unitNumber", 0);
            group.put("materials", unclassifiedMaterials);
            materialGroups.add(group);
        }
        model.addAttribute("materialGroups", materialGroups);
        List<AcademicResource> localSemesterPyqs = safeResources(subjectCode, "SEMESTER_PYQS");
        model.addAttribute("localSemesterPyqs", localSemesterPyqs);
        model.addAttribute("resourceError", error);
        model.addAttribute("driveConfigured", googleDriveConfigured());
        boolean subjectDriveWriteConfigured;
        try { subjectDriveWriteConfigured = academicResourceService.isDriveUploadConfigured(); }
        catch (RuntimeException ex) { subjectDriveWriteConfigured = false; }
        model.addAttribute("driveUploadConfigured", subjectDriveWriteConfigured);
        model.addAttribute("indexedTotal", localSemesterPyqs.size());
        // Keep the first subject render bounded: the overview template does not consume
        // question rows or unitSummaries. Loading and deduplicating every extracted question
        // here delayed the entire subject workspace on cold cache. The dedicated archive,
        // topic, unit and intelligence routes load PYQ data only when the student opens them.
        return "academic-subject";
    }


    @GetMapping({"/academic/{subjectCode}/chapter/{chapterIndex}", "/academic/{subjectCode}/unit/{chapterIndex}"})
    public String chapterPage(@PathVariable("subjectCode") String subjectCode, @PathVariable("chapterIndex") int chapterIndex,
                              @RequestParam(required = false) String error, Principal principal, Model model) {
        AcademicSubject subject = findSubject(subjectCode);
        if (subject == null) return "redirect:/academic";
        List<AcademicUnit> units;
        units = safeUnits(subjectCode);
        if (chapterIndex < 1 || chapterIndex > units.size()) return "redirect:/academic/" + subjectCode;
        AcademicUnit unit = units.get(chapterIndex - 1);
        List<PYQQuestion> all = safeQuestions(subjectCode);
        List<String> topicNames = unit.getTopics() == null ? List.of() : unit.getTopics().stream()
                .map(com.nexora.academic.AcademicTopic::getTopic).filter(t -> t != null && !t.isBlank()).toList();
        List<PYQQuestion> chapterQuestions = all.stream().filter(q -> q.getTopic() != null
                && topicNames.stream().anyMatch(t -> topicKey(t).equals(topicKey(q.getTopic())))).toList();
        int chapterMarks = chapterQuestions.stream().mapToInt(PYQQuestion::getMarks).sum();
        List<AcademicUnit> orderedUnits = units.stream().filter(java.util.Objects::nonNull).toList();
        java.util.Set<String> allSyllabusTopicNames = orderedUnits.stream()
                .flatMap(u -> u.getTopics() == null ? java.util.stream.Stream.empty() : u.getTopics().stream())
                .map(com.nexora.academic.AcademicTopic::getTopic).filter(name -> name != null && !name.isBlank())
                .map(AcademicController::topicKey).collect(java.util.stream.Collectors.toSet());
        List<PYQQuestion> mappedQuestions = all.stream().filter(q -> q.getTopic() != null
                && allSyllabusTopicNames.contains(topicKey(q.getTopic()))).toList();
        int allMarks = mappedQuestions.stream().mapToInt(PYQQuestion::getMarks).sum();
        // Calculate raw evidence for each chapter, then normalize against the sum of chapter
        // evidence. This guarantees the displayed integer percentages total exactly 100%.
        boolean useMarks = allMarks > 0;
        List<Double> chapterAmounts = new ArrayList<>();
        double denominator = 0.0;
        for (AcademicUnit candidate : orderedUnits) {
            List<String> candidateTopics = candidate.getTopics() == null ? List.of() : candidate.getTopics().stream().filter(java.util.Objects::nonNull)
                    .map(com.nexora.academic.AcademicTopic::getTopic).filter(t -> t != null && !t.isBlank()).toList();
            List<PYQQuestion> candidateQuestions = mappedQuestions.stream().filter(q -> q.getTopic() != null
                    && candidateTopics.stream().anyMatch(t -> topicKey(t).equals(topicKey(q.getTopic())))).toList();
            double amount = useMarks ? candidateQuestions.stream().mapToInt(PYQQuestion::getMarks).sum() : candidateQuestions.size();
            chapterAmounts.add(amount);
            denominator += amount;
        }
        List<Double> chapterWeights = new ArrayList<>();
        for (double amount : chapterAmounts) chapterWeights.add(denominator > 0 ? amount * 100.0 / denominator : 0.0);
        int[] normalizedWeights = new int[chapterWeights.size()];
        int allocated = 0;
        List<Integer> remainderOrder = new ArrayList<>();
        for (int i = 0; i < chapterWeights.size(); i++) {
            normalizedWeights[i] = (int) Math.floor(chapterWeights.get(i));
            allocated += normalizedWeights[i];
            remainderOrder.add(i);
        }
        remainderOrder.sort((a,b) -> Double.compare(chapterWeights.get(b) - Math.floor(chapterWeights.get(b)), chapterWeights.get(a) - Math.floor(chapterWeights.get(a))));
        if (denominator > 0) {
            for (int i = 0; i < 100 - allocated && i < remainderOrder.size(); i++) normalizedWeights[remainderOrder.get(i)]++;
        }
        int chapterWeightage = chapterIndex <= normalizedWeights.length ? normalizedWeights[chapterIndex - 1] : 0;

        List<Map<String,Object>> topicStats = new ArrayList<>();
        List<Map<String,Object>> prerequisiteDetails = new ArrayList<>();
        List<Map<String,Object>> prerequisites = new ArrayList<>();
        List<TopicDependency> dependencySnapshot;
        try { dependencySnapshot = topicService.getDependenciesForSubject(subjectCode); }
        catch (Exception ignored) { dependencySnapshot = List.of(); }
        if (dependencySnapshot == null) dependencySnapshot = List.of();
        // Merge prerequisites declared directly in the syllabus with database-managed mappings.
        // This makes the prerequisite panel useful even before an administrator imports a topic map.
        List<TopicDependency> mergedDependencies = new ArrayList<>(dependencySnapshot);
        for (AcademicUnit syllabusUnit : orderedUnits) {
            if (syllabusUnit.getTopics() == null) continue;
            for (com.nexora.academic.AcademicTopic syllabusTopic : syllabusUnit.getTopics() == null ? List.<com.nexora.academic.AcademicTopic>of() : syllabusUnit.getTopics()) {
                if (syllabusTopic == null || syllabusTopic.getTopic() == null || syllabusTopic.getTopic().isBlank()) continue;
                if (syllabusTopic == null || syllabusTopic.getTopic() == null || syllabusTopic.getTopic().isBlank()
                        || syllabusTopic.getPrerequisites() == null) continue;
                for (String prerequisite : syllabusTopic.getPrerequisites()) {
                    if (prerequisite == null || prerequisite.isBlank()) continue;
                    boolean exists = mergedDependencies.stream().anyMatch(d -> d.getDependentTopic() != null
                            && d.getPrerequisiteTopic() != null
                            && d.getDependentTopic().equalsIgnoreCase(syllabusTopic.getTopic())
                            && d.getPrerequisiteTopic().equalsIgnoreCase(prerequisite));
                    if (!exists) mergedDependencies.add(new TopicDependency(prerequisite, syllabusTopic.getTopic(), "SYLLABUS"));
                }
            }
        }
        dependencySnapshot = mergedDependencies;
        List<Map<String,Object>> pyqTopicStats = new ArrayList<>();
        for (int topicIndex = 0; topicIndex < topicNames.size(); topicIndex++) {
            String topicName = topicNames.get(topicIndex);
            List<PYQQuestion> topicQuestions = chapterQuestions.stream().filter(q -> q.getTopic().equalsIgnoreCase(topicName)).toList();
            Map<String,Object> stat = new LinkedHashMap<>();
            stat.put("name", topicName); stat.put("count", topicQuestions.size());
            stat.put("marks", topicQuestions.stream().mapToInt(PYQQuestion::getMarks).sum());
            stat.put("percent", !chapterQuestions.isEmpty()
                    ? Math.round(topicQuestions.size() * 100.0 / chapterQuestions.size()) : 0);
            topicStats.add(stat);
            Map<String,Object> intelligence = new LinkedHashMap<>();
            intelligence.put("name", topicName);
            intelligence.put("count", topicQuestions.size());
            intelligence.put("marks", topicQuestions.stream().mapToInt(PYQQuestion::getMarks).sum());
            intelligence.put("years", topicQuestions.stream().map(PYQQuestion::getYear)
                    .filter(java.util.Objects::nonNull).distinct().sorted(Comparator.reverseOrder()).toList());
            intelligence.put("models", topicQuestions.stream().map(PYQQuestion::getQuestionModel)
                    .filter(java.util.Objects::nonNull).filter(v -> !v.isBlank()).distinct().toList());
            pyqTopicStats.add(intelligence);
            List<String> direct = dependencySnapshot.stream()
                    .filter(d -> d.getDependentTopic() != null && topicKey(d.getDependentTopic()).equals(topicKey(topicName)))
                    .map(TopicDependency::getPrerequisiteTopic).filter(java.util.Objects::nonNull).distinct().toList();
            List<String> dependents = dependencySnapshot.stream()
                    .filter(d -> d.getPrerequisiteTopic() != null && topicKey(d.getPrerequisiteTopic()).equals(topicKey(topicName)))
                    .map(TopicDependency::getDependentTopic).filter(java.util.Objects::nonNull).distinct().toList();
            List<String> chain = prerequisiteChain(dependencySnapshot, topicName);
            Map<String,Object> detail = new LinkedHashMap<>();
            detail.put("id", "topic-" + topicIndex); detail.put("topic", topicName);
            detail.put("unit", unit.getUnitName());
            detail.put("prerequisites", (direct == null ? List.<String>of() : direct).stream()
                    .map(name -> name + " — " + topicUnitLabel(orderedUnits, name)).toList());
            detail.put("dependents", (dependents == null ? List.<String>of() : dependents).stream()
                    .map(name -> name + " — " + topicUnitLabel(orderedUnits, name)).toList());
            detail.put("chain", (chain == null ? List.<String>of() : chain).stream()
                    .map(name -> name + " — " + topicUnitLabel(orderedUnits, name)).toList());
            detail.put("previousTopic", topicIndex > 0 ? topicNames.get(topicIndex - 1) : "");
            detail.put("nextTopic", topicIndex + 1 < topicNames.size() ? topicNames.get(topicIndex + 1) : "");
            prerequisiteDetails.add(detail);
            if (direct != null) for (String pre : direct) {
                Map<String,Object> row = new LinkedHashMap<>(); row.put("topic", topicName); row.put("prerequisite", pre); prerequisites.add(row);
            }
        }
        // The unit workspace intentionally contains only chapter weightage, topic weightage,
        // prerequisites, and PYQ topic intelligence. Do not query Drive/resource/test systems
        // while rendering it; those resources belong to the subject-level pages.
        model.addAttribute("subject", subject); model.addAttribute("unit", unit); model.addAttribute("units", units);
        model.addAttribute("chapterIndex", chapterIndex); model.addAttribute("topicStats", topicStats);
        model.addAttribute("prerequisites", prerequisites); model.addAttribute("prerequisiteDetails", prerequisiteDetails);
        model.addAttribute("pyqTopicStats", pyqTopicStats);
        model.addAttribute("hasPyqTopicEvidence", pyqTopicStats.stream().anyMatch(row -> ((Number) row.get("count")).intValue() > 0));
        model.addAttribute("chapterQuestionCount", chapterQuestions.size()); model.addAttribute("chapterMarks", chapterMarks);
        model.addAttribute("chapterWeightage", chapterWeightage);
        model.addAttribute("hasChapterWeightageEvidence", denominator > 0 && !chapterQuestions.isEmpty());
        model.addAttribute("hasTopicWeightageEvidence", topicStats.stream()
                .anyMatch(row -> ((Number) row.get("count")).intValue() > 0));
        model.addAttribute("hasPrerequisites", !prerequisiteDetails.isEmpty());
        return "academic-chapter";
    }

    @PostMapping("/academic/{subjectCode}/chapter/{chapterIndex}/test/submit")
    public String submitChapterTest(@PathVariable("subjectCode") String subjectCode, @PathVariable("chapterIndex") int chapterIndex,
                                    @RequestParam Map<String, String> form, Principal principal,
                                    RedirectAttributes redirect) {
        AcademicSubject subject = findSubject(subjectCode);
        List<AcademicUnit> units = safeUnits(subjectCode);
        if (subject == null || chapterIndex < 1 || chapterIndex > units.size()) return "redirect:/academic";
        String registrationNo = principal == null ? null : studentRepository.findByLoginId(principal.getName())
                .map(com.nexora.model.Student::getRegistrationNo).orElse(null);
        if (registrationNo == null && principal != null) registrationNo = principal.getName();
        Map<Long, String> answers = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : form.entrySet()) {
            if (!entry.getKey().startsWith("answer_")) continue;
            try { answers.put(Long.parseLong(entry.getKey().substring("answer_".length())), entry.getValue()); }
            catch (NumberFormatException ignored) { }
        }
        try {
            ChapterTestService.TestResult result = chapterTests.grade(registrationNo, subjectCode, chapterIndex,
                    units.get(chapterIndex - 1).getUnitName(), answers);
            redirect.addFlashAttribute("testResult", result);
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("testError", ex.getMessage());
        }
        return "redirect:/academic/" + subjectCode + "/chapter/" + chapterIndex + "#subject-test";
    }

    @GetMapping("/academic/{subjectCode}/archive")
    public String pyqArchive(@PathVariable("subjectCode") String subjectCode, Model model) {
        AcademicSubject subject = findSubject(subjectCode);
        if (subject == null) return "redirect:/academic";
        List<AcademicResource> papers = safeResources(subjectCode, "SEMESTER_PYQS").stream()
                .filter(p -> p.getResourceYear() != null && p.getResourceYear() >= 2000 && p.getResourceYear() <= 2100)
                .filter(p -> p.getOriginalFilename() != null && !p.getOriginalFilename().isBlank())
                .sorted(Comparator.comparing(AcademicResource::getResourceYear, Comparator.nullsLast(Comparator.<Integer>reverseOrder()))
                        .thenComparing(AcademicResource::getOriginalFilename, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))).toList();
        boolean driveConfigured = googleDriveConfigured();
        if (papers.isEmpty() && !driveConfigured) {
            // Keep the original-paper archive reachable even before the Drive source is configured.
        }
        java.util.Map<String,List<AcademicResource>> papersByYear = new java.util.TreeMap<>(Comparator.reverseOrder());
        for (AcademicResource paper : papers) papersByYear.computeIfAbsent(String.valueOf(paper.getResourceYear()), k -> new ArrayList<>()).add(paper);
        model.addAttribute("subject", subject); model.addAttribute("papersByYear", papersByYear);
        model.addAttribute("paperCount", papers.size());
        model.addAttribute("driveConfigured", driveConfigured);
        model.addAttribute("hasArchiveContent", !papers.isEmpty());
        return "academic-pyq-archive";
    }

    @PostMapping("/academic/{subjectCode}/chapter/{chapterIndex}/materials.pdf")
    public ResponseEntity<byte[]> generateChapterMaterials(@PathVariable("subjectCode") String subjectCode, @PathVariable("chapterIndex") int chapterIndex) {
        AcademicSubject subject = findSubject(subjectCode);
        if (subject == null) return ResponseEntity.notFound().build();
        List<AcademicUnit> units = safeUnits(subjectCode);
        if (chapterIndex < 1 || chapterIndex > units.size()) return ResponseEntity.badRequest().build();
        AcademicUnit unit = units.get(chapterIndex - 1);
        List<String> topics = unit.getTopics() == null ? List.of() : unit.getTopics().stream().filter(java.util.Objects::nonNull).map(com.nexora.academic.AcademicTopic::getTopic).filter(t -> t != null && !t.isBlank()).toList();
        if (topics.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        try (PDDocument output = new PDDocument(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            java.util.Set<String> copiedPages = new java.util.HashSet<>(); int copied = 0;
            for (String topic : topics) {
                for (AcademicResource resource : academicResourceService.find(subjectCode, "STUDY_MATERIALS")) {
                    Path path = academicResourceService.storagePath(resource.getId());
                    if (path == null || !Files.isRegularFile(path) || !path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) continue;
                    try (PDDocument source = Loader.loadPDF(path.toFile())) {
                        PDFTextStripper stripper = new PDFTextStripper(); PDFRenderer renderer = new PDFRenderer(source);
                        for (int page = 1; page <= source.getNumberOfPages(); page++) {
                            String pageKey = resource.getId() + ":" + page; if (copiedPages.contains(pageKey)) continue;
                            stripper.setStartPage(page); stripper.setEndPage(page); String pageText = stripper.getText(source);
                            if (pageText == null || pageText.isBlank()) {
                                try (ByteArrayOutputStream imageBytes = new ByteArrayOutputStream()) {
                                    ImageIO.write(renderer.renderImageWithDPI(page - 1, 180, ImageType.GRAY), "png", imageBytes);
                                    pageText = ocr.extractImage(imageBytes.toByteArray());
                                } catch (Exception ignoredOcr) { pageText = ""; }
                            }
                            if (containsTopicText(pageText, topic)) {
                                output.importPage(source.getPage(page - 1)); copiedPages.add(pageKey); copied++;
                            }
                        }
                    } catch (Exception ignored) { }
                }
            }
            if (copied == 0) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            output.save(bytes); HttpHeaders headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDisposition(ContentDisposition.attachment().filename(subjectCode + "_Unit_" + chapterIndex + "_Study_Material.pdf").build());
            headers.add("X-NEXORA-Source-Pages", Integer.toString(copied)); return ResponseEntity.ok().headers(headers).body(bytes.toByteArray());
        } catch (Exception ex) { return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build(); }
    }

    // ============================================================
    // TOPICS PAGE
    // ============================================================

    @GetMapping("/academic/{subjectCode}/topics")
    public String topics(
            @PathVariable("subjectCode") String subjectCode,
            Principal principal,
            Model model) {

        AcademicSubject subject = findSubject(subjectCode);

        if (subject == null) {
            return "redirect:/academic";
        }


        List<AcademicUnit> units = safeUnits(subjectCode);
        if (units.isEmpty()) return "redirect:/academic/" + subjectCode;

        List<Topic> topics = safeTopics(subjectCode);
        boolean premiumFeatures = hasPremium(principal);
        List<TopicDependency> allDependencies = safeDependencies(subjectCode);
        List<TopicDependency> dependencies = premiumFeatures ? allDependencies : List.of();

        model.addAttribute("premiumFeatures", premiumFeatures);
        model.addAttribute("subject", subject);
        model.addAttribute("units", units);
        model.addAttribute("topics", topics);
        model.addAttribute("dependencies", dependencies);
        model.addAttribute("hasDependencyData", !allDependencies.isEmpty());

        return "academic-topics";
    }


    // ============================================================
    // TOPIC DETAILS
    // ============================================================

    @GetMapping({"/academic/{subjectCode}/topic", "/academic/{subjectCode}/topic/{topicName}"})
    public String topicDetails(
            @PathVariable("subjectCode") String subjectCode,
            @PathVariable(value = "topicName", required = false) String topicName,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String questionModel,
            Principal principal,
            Model model) {

        // The path form is the canonical route. The query form is retained for
        // backwards compatibility with previously generated/bookmarked links.
        String resolvedTopicName = (topicName != null && !topicName.isBlank())
                ? topicName
                : name;

        if (resolvedTopicName == null || resolvedTopicName.isBlank()) {
            return "redirect:/academic/" + subjectCode + "/topics";
        }

        AcademicSubject subject = findSubject(subjectCode);

        if (subject == null) {
            return "redirect:/academic";
        }


        Topic topic = safeTopicByName(subjectCode, resolvedTopicName);
        if (topic == null) return "redirect:/academic/" + subjectCode + "/topics";

        boolean premiumFeatures = hasPremium(principal);
        List<String> prerequisites = premiumFeatures ? safeTopicList(subjectCode, resolvedTopicName, 0) : List.of();
        List<String> dependentTopics = premiumFeatures ? safeTopicList(subjectCode, resolvedTopicName, 1) : List.of();
        List<String> fullPrerequisiteChain = premiumFeatures ? safeTopicList(subjectCode, resolvedTopicName, 2) : List.of();


        List<PYQQuestion> pyqQuestions;
        String pyqWarning = null;
        pyqQuestions = safeTopicQuestions(subjectCode, resolvedTopicName);
        pyqQuestions = (pyqQuestions == null ? List.<PYQQuestion>of() : pyqQuestions).stream()
                .filter(java.util.Objects::nonNull)
                .filter(q -> q.getQuestionText() != null && !q.getQuestionText().isBlank())
                .filter(q -> q.getYear() != null && q.getYear().trim().matches("(?:19|20)\\d{2}"))
                .toList();

        if (premiumFeatures && questionModel != null
                && !questionModel.isBlank()) {

            pyqQuestions = pyqQuestions.stream()
                    .filter(question ->
                            questionModel.equalsIgnoreCase(
                                    question.getQuestionModel()
                            )
                    )
                    .toList();
        }


        List<String> questionModels = new ArrayList<>();
        List<PYQModelStats> modelStatistics = new ArrayList<>();
        List<PYQExamStats> examStatistics = new ArrayList<>();
        if (premiumFeatures) {
            try {
                questionModels = pyqService.getQuestionModelsForTopic(subjectCode, resolvedTopicName);
                modelStatistics = pyqService.getModelStatisticsForTopic(subjectCode, resolvedTopicName);
                examStatistics = pyqService.getExamStatisticsForTopic(subjectCode, resolvedTopicName);
                if (questionModels == null) questionModels = List.of();
                if (modelStatistics == null) modelStatistics = List.of();
                if (examStatistics == null) examStatistics = List.of();
            } catch (RuntimeException ex) {
                LOGGER.log(java.util.logging.Level.WARNING, "Could not load optional PYQ analytics for subject " + subjectCode, ex);
                questionModels = List.of();
                modelStatistics = List.of();
                examStatistics = List.of();
            }
        }

        // Student-facing archive: group questions by exam year (newest first)
        // instead of rendering one long raw OCR list.
        Map<String, List<PYQQuestion>> questionsByYear = new LinkedHashMap<>();
        pyqQuestions.stream()
                .sorted(Comparator.comparingInt(AcademicController::yearSortKey).reversed())
                .forEach(q -> questionsByYear.computeIfAbsent(q.getYear().trim(), k -> new ArrayList<>()).add(q));


        model.addAttribute("premiumFeatures", premiumFeatures);
        model.addAttribute("subject", subject);
        model.addAttribute("topic", topic);
        model.addAttribute("prerequisites", prerequisites);
        model.addAttribute("dependentTopics", dependentTopics);

        model.addAttribute(
                "fullPrerequisiteChain",
                fullPrerequisiteChain
        );

        model.addAttribute("pyqQuestions", pyqQuestions);
        model.addAttribute("pyqMarksTotal", pyqQuestions.stream().mapToInt(PYQQuestion::getMarks).sum());
        model.addAttribute("questionModels", questionModels);
        model.addAttribute("modelStatistics", modelStatistics);
        model.addAttribute("examStatistics", examStatistics);
        model.addAttribute("questionsByYear", questionsByYear);
        model.addAttribute("selectedQuestionModel", questionModel);
        model.addAttribute("pyqWarning", pyqWarning);

        return "academic-topic-details";
    }

    private boolean hasPremium(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) return false;
        var student = studentRepository.findByLoginId(principal.getName()).orElse(null);
        return student != null && subscriptions.hasPlanAtLeast(student.getRegistrationNo(), "PREMIUM");
    }

    private boolean containsTopicText(String pageText, String topic) {
        if (pageText == null || topic == null || topic.isBlank()) return false;
        String normalizedPage = java.text.Normalizer.normalize(pageText, java.text.Normalizer.Form.NFKC)
                .toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", " ").replaceAll("\\s+", " ").trim();
        String normalizedTopic = java.text.Normalizer.normalize(topic, java.text.Normalizer.Form.NFKC)
                .toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", " ").replaceAll("\\s+", " ").trim();
        if (normalizedTopic.isBlank()) return false;
        if (normalizedPage.contains(normalizedTopic)) return true;
        String[] tokens = normalizedTopic.split(" ");
        if (tokens.length == 1 && tokens[0].length() <= 2) {
            return java.util.regex.Pattern.compile("(?i)\\b" + java.util.regex.Pattern.quote(tokens[0]) + "\\b").matcher(pageText).find();
        }
        java.util.Set<String> pageWords = new java.util.HashSet<>(java.util.Arrays.asList(normalizedPage.split(" ")));
        int meaningful = 0, matched = 0;
        for (String token : tokens) {
            if (token.length() < 3) continue;
            meaningful++;
            String stem = token.length() > 4 && token.endsWith("s") ? token.substring(0, token.length() - 1) : token;
            if (pageWords.contains(token) || pageWords.contains(stem)) matched++;
        }
        return meaningful > 0 && matched >= Math.ceil(meaningful * 0.8);
    }

    private static String topicKey(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }

    private static boolean isMcq(PYQQuestion question) {
        String kind = question == null || question.getQuestionModel() == null ? "" : question.getQuestionModel().toLowerCase(java.util.Locale.ROOT);
        return kind.contains("mcq") || kind.contains("objective") || kind.contains("multiple choice");
    }

    private static List<String> prerequisiteChain(List<TopicDependency> dependencies, String topicName) {
        java.util.LinkedHashMap<String, String> visited = new java.util.LinkedHashMap<>();
        collectPrerequisiteChain(dependencies, topicName, visited);
        return List.copyOf(visited.values());
    }

    private static void collectPrerequisiteChain(List<TopicDependency> dependencies, String topicName,
                                                  java.util.Map<String, String> visited) {
        for (TopicDependency dependency : dependencies) {
            if (dependency.getDependentTopic() == null || dependency.getPrerequisiteTopic() == null
                    || !dependency.getDependentTopic().equalsIgnoreCase(topicName)) continue;
            String prerequisite = dependency.getPrerequisiteTopic();
            String key = prerequisite.toLowerCase(java.util.Locale.ROOT);
            if (visited.containsKey(key)) continue;
            visited.put(key, prerequisite);
            collectPrerequisiteChain(dependencies, prerequisite, visited);
        }
    }

    private static String topicUnitLabel(List<AcademicUnit> units, String topicName) {
        if (topicName == null || units == null) return "unit not mapped";
        for (int i = 0; i < units.size(); i++) {
            AcademicUnit candidate = units.get(i);
            if (candidate == null || candidate.getTopics() == null) continue;
            boolean found = candidate.getTopics().stream().filter(java.util.Objects::nonNull)
                    .map(com.nexora.academic.AcademicTopic::getTopic).filter(java.util.Objects::nonNull)
                    .anyMatch(name -> name.equalsIgnoreCase(topicName));
            if (found) return candidate.getUnitName() == null ? "Unit " + (i + 1) : candidate.getUnitName();
        }
        return "unit not mapped";
    }

    private static int yearSortKey(PYQQuestion question) {
        if (question == null || question.getYear() == null) return Integer.MIN_VALUE;
        try {
            return Integer.parseInt(question.getYear().trim());
        } catch (NumberFormatException ignored) {
            return Integer.MIN_VALUE;
        }
    }


    // ============================================================
    // DRIVE SYNC / REPROCESS
    // ============================================================

    @PostMapping("/academic/{subjectCode}/resources/sync")
    @org.springframework.web.bind.annotation.ResponseBody
    public java.util.Map<String,Object> syncAcademicResources(@PathVariable("subjectCode") String subjectCode) {
        AcademicSubject subject = findSubject(subjectCode);
        if (subject == null) return java.util.Map.of("ok", false, "message", "Subject not found.");
        AcademicDriveSyncService.SyncResult result = academicDriveSyncService.syncSubject(subjectCode);
        academicPageReadCache.clear();
        boolean scanFailed = result.message() != null && (result.message().startsWith("Drive scan unavailable:")
                || result.message().contains("is not configured"));
        boolean ok = !scanFailed && result.errors() == 0 && result.discovered() > 0;
        return java.util.Map.of("ok", ok,
                "message", result.message(), "discovered", result.discovered(), "processed", result.processed(),
                "skipped", result.skipped(), "errors", result.errors());
    }

    @PostMapping("/academic/{subjectCode}/resources/reprocess/{id}")
    public String reprocessAcademicResource(@PathVariable("subjectCode") String subjectCode, @PathVariable("id") long id) {
        AcademicResource resource = academicResourceService.find(id);
        if (resource != null && subjectCode.equalsIgnoreCase(resource.getSubjectCode())) {
            Path path = academicResourceService.storagePath(id);
            if (path != null && Files.isRegularFile(path)) {
                // Reprocessing is triggered by the same deterministic extractor used for new uploads.
                // The service is invoked through a lightweight resource re-save path below.
                academicResourceService.reprocess(id);
            }
        }
        return "redirect:/academic/" + subjectCode;
    }

    // ============================================================
    // LOCAL ACADEMIC RESOURCE UPLOAD
    // ============================================================

    @PostMapping("/academic/{subjectCode}/resources/upload")
    public String uploadAcademicResource(
            @PathVariable("subjectCode") String subjectCode,
            @RequestParam("resourceType") String resourceType,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String resourceMonth,
            @RequestParam(required = false) Integer resourceYear,
            @RequestParam(required = false) Integer chapterIndex,
            Principal principal) {

        AcademicSubject subject = findSubject(subjectCode);
        if (subject == null) return "redirect:/academic";

        try {
            // Semester PYQ metadata is derived exclusively from the uploaded filename.
            // The descriptive original filename carries the year; no duplicate date form fields.
            if ("SEMESTER_PYQS".equalsIgnoreCase(resourceType)) {
                String originalName = file == null ? "" : file.getOriginalFilename();
                String safeOriginalName = originalName == null ? "" : originalName.trim();
                if (safeOriginalName.isBlank() || !safeOriginalName.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")
                        || safeOriginalName.contains("/") || safeOriginalName.contains("\\")) {
                    return "redirect:/academic/" + subjectCode + "?error=" + java.net.URLEncoder.encode(
                            "Give the question paper a descriptive PDF filename, for example DSA_May_2025.pdf.", java.nio.charset.StandardCharsets.UTF_8);
                }
                java.util.regex.Matcher yearMatcher = java.util.regex.Pattern.compile("(?<!\\d)(20\\d{2}|2100)(?!\\d)").matcher(safeOriginalName);
                if (!yearMatcher.find()) {
                    return "redirect:/academic/" + subjectCode + "?error=" + java.net.URLEncoder.encode(
                            "Include the exam year in the PDF filename so the archive can group papers by year (for example DSA_May_2025.pdf).", java.nio.charset.StandardCharsets.UTF_8);
                }
                resourceYear = Integer.valueOf(yearMatcher.group(1));
                java.util.regex.Matcher monthMatcher = java.util.regex.Pattern.compile("(?i)(January|February|March|April|May|June|July|August|September|October|November|December)").matcher(safeOriginalName);
                if (monthMatcher.find()) {
                    String month = monthMatcher.group(1);
                    resourceMonth = month.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + month.substring(1).toLowerCase(java.util.Locale.ROOT);
                }
                if (file.isEmpty() || file.getSize() > 20L * 1024 * 1024) {
                    return "redirect:/academic/" + subjectCode + "?error=" + java.net.URLEncoder.encode(
                            "Choose a non-empty PDF no larger than 20 MB.", java.nio.charset.StandardCharsets.UTF_8);
                }
                String contentType = file.getContentType();
                if (contentType != null && !contentType.isBlank()
                        && !contentType.equalsIgnoreCase("application/pdf")
                        && !contentType.equalsIgnoreCase("application/octet-stream")) {
                    return "redirect:/academic/" + subjectCode + "?error=" + java.net.URLEncoder.encode(
                            "Only PDF question papers are accepted.", java.nio.charset.StandardCharsets.UTF_8);
                }
            }
            academicResourceService.save(
                    subjectCode,
                    resourceType,
                    file,
                    resourceMonth,
                    resourceYear,
                    principal == null ? "STUDENT" : studentRepository.findByLoginId(principal.getName())
                            .map(com.nexora.model.Student::getRegistrationNo).orElse("STUDENT"));
            academicPageReadCache.clear();
            return chapterIndex != null && chapterIndex > 0
                    ? "redirect:/academic/" + subjectCode + "/chapter/" + chapterIndex + "?uploaded=1"
                    : "redirect:/academic/" + subjectCode + "?uploaded=1";
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? "Upload failed" : ex.getMessage();
            String base = chapterIndex != null && chapterIndex > 0
                    ? "/academic/" + subjectCode + "/chapter/" + chapterIndex
                    : "/academic/" + subjectCode;
            return "redirect:" + base + "?error=" + java.net.URLEncoder.encode(message, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    @GetMapping("/academic/resource/{id}/text")
    public ResponseEntity<?> extractedAcademicText(@PathVariable("id") long id) {
        Path path = academicResourceService.extractedTextPath(id);
        if (path == null) return ResponseEntity.notFound().build();
        try {
            return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(Files.readString(path));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Unable to read extracted text.");
        }
    }

    @GetMapping("/academic/resource/{id}")
    public ResponseEntity<?> openAcademicResource(@PathVariable("id") long id) {
        AcademicResource resource = academicResourceService.find(id);
        if (resource == null) return ResponseEntity.notFound().build();

        // A Drive-indexed row may have metadata but no local storage path. Prefer a local
        // upload when available, then stream from Drive using configured credentials, and
        // finally redirect to a validated Google Drive/Docs viewer URL.
        String filename = resource.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            filename = resource.getTitle() == null || resource.getTitle().isBlank()
                    ? "academic-resource" : resource.getTitle();
        }
        try {
            Path path = academicResourceService.storagePath(id);
            if (path != null && Files.isRegularFile(path)) {
                InputStreamResource body = new InputStreamResource(Files.newInputStream(path));
                return ResponseEntity.ok()
                        .contentType(detectResourceMediaType(resource.getMimeType(), filename))
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                ContentDisposition.inline().filename(filename).build().toString())
                        .body(body);
            }

            String driveId = resource.getDriveFileId();
            if (driveId != null && !driveId.isBlank()) {
                try {
                    byte[] bytes = googleDriveService.downloadFile(driveId.trim());
                    if (bytes != null && bytes.length > 0) {
                        return ResponseEntity.ok()
                                .contentType(detectResourceMediaType(resource.getMimeType(), filename))
                                .header(HttpHeaders.CONTENT_DISPOSITION,
                                        ContentDisposition.inline().filename(filename).build().toString())
                                .body(bytes);
                    }
                } catch (Exception driveException) {
                    LOGGER.warning("Drive streaming failed for academic resource " + id + ": " + driveException.getMessage());
                }
            }

            // Repair legacy rows where the Drive file ID was not saved: match the exact
            // filename within the same subject against the recursively indexed Drive list.
            // This list is cached by GoogleDriveService, so repeated clicks do not re-scan Drive.
            if (driveId == null || driveId.isBlank()) {
                try {
                    String expectedName = normalizeResourceName(filename);
                    for (GoogleDriveService.DriveFile driveFile : googleDriveService.getSemester3Files()) {
                        if (driveFile == null || driveFile.getId() == null || driveFile.getId().isBlank()) continue;
                        String resourceSubjectCode = resource.getSubjectCode() == null ? "" : resource.getSubjectCode();
                        if (!resourceSubjectCode.equalsIgnoreCase(
                                driveFile.getSubjectCode() == null ? "" : driveFile.getSubjectCode())) continue;
                        if (!expectedName.equals(normalizeResourceName(driveFile.getName()))) continue;
                        try {
                            byte[] bytes = googleDriveService.downloadFile(driveFile.getId());
                            if (bytes != null && bytes.length > 0) {
                                return ResponseEntity.ok()
                                        .contentType(detectResourceMediaType(resource.getMimeType(), filename))
                                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                                ContentDisposition.inline().filename(filename).build().toString())
                                        .body(bytes);
                            }
                        } catch (Exception driveException) {
                            LOGGER.warning("Drive filename fallback failed for academic resource " + id + ": " + driveException.getMessage());
                        }
                        if (isTrustedGoogleDriveLink(driveFile.getWebViewLink())) {
                            return ResponseEntity.status(HttpStatus.FOUND)
                                    .header(HttpHeaders.LOCATION, driveFile.getWebViewLink().trim()).build();
                        }
                    }
                } catch (Exception lookupException) {
                    LOGGER.warning("Drive lookup fallback failed for academic resource " + id + ": " + lookupException.getMessage());
                }
            }

            String viewLink = resource.getDriveWebViewLink();
            if (isTrustedGoogleDriveLink(viewLink)) {
                return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, viewLink.trim()).build();
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.TEXT_PLAIN)
                    .body("This resource has no local copy or valid Google Drive link. Please sync the subject's Drive materials again.");
        } catch (Exception ex) {
            LOGGER.warning("Unable to open academic resource " + id + ": " + ex.getMessage());
            return ResponseEntity.internalServerError().contentType(MediaType.TEXT_PLAIN)
                    .body("Unable to open this academic resource. Please sync the subject's Drive materials again.");
        }
    }

    private int inferMaterialUnit(String filename) {
        if (filename == null || filename.isBlank()) return 0;
        String normalized = java.text.Normalizer.normalize(filename, java.text.Normalizer.Form.NFKC)
                .toLowerCase(java.util.Locale.ROOT).replace('_', ' ').replace('-', ' ');
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\\b(?:unit|chapter|chap|ch)\\s*(?:no\\.?\\s*)?([1-9][0-9]?)\\b")
                .matcher(normalized);
        if (matcher.find()) {
            try { return Integer.parseInt(matcher.group(1)); }
            catch (NumberFormatException ignored) { return 0; }
        }
        return 0;
    }

    private MediaType detectResourceMediaType(String configuredMime, String filename) {
        if (configuredMime != null && !configuredMime.isBlank()) {
            try { return MediaType.parseMediaType(configuredMime); } catch (Exception ignored) { }
        }
        String name = filename == null ? "" : filename.toLowerCase(java.util.Locale.ROOT);
        if (name.endsWith(".pdf")) return MediaType.APPLICATION_PDF;
        if (name.endsWith(".ppt")) return MediaType.parseMediaType("application/vnd.ms-powerpoint");
        if (name.endsWith(".pptx")) return MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.presentationml.presentation");
        if (name.endsWith(".doc")) return MediaType.parseMediaType("application/msword");
        if (name.endsWith(".docx")) return MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private String normalizeResourceName(String value) {
        if (value == null) return "";
        return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFKC)
                .trim().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
    }

    private boolean isTrustedGoogleDriveLink(String link) {
        if (link == null || link.isBlank()) return false;
        try {
            java.net.URI uri = java.net.URI.create(link.trim());
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme()) && host != null
                    && (host.equalsIgnoreCase("drive.google.com") || host.equalsIgnoreCase("docs.google.com"));
        } catch (Exception ignored) {
            return false;
        }
    }


    @GetMapping("/academic/{subjectCode}/pyq-intelligence")
    public String pyqIntelligence(@PathVariable("subjectCode") String subjectCode,
                                  @RequestParam(required = false) String unit,
                                  @RequestParam(required = false) String topic,
                                  @RequestParam(required = false) String kind,
                                  Principal principal, Model model) {
        AcademicSubject subject = findSubject(subjectCode);
        if (subject == null) return "redirect:/academic";
        if (!hasPremium(principal)) return "redirect:/profile?upgrade=premium#subscription";
        List<AcademicUnit> units = safeUnits(subjectCode);
        List<String> topics = units.stream()
                .filter(u -> unit == null || unit.isBlank() || u.getUnitName().equals(unit))
                .flatMap(u -> u.getTopics() == null ? java.util.stream.Stream.empty() : u.getTopics().stream())
                .map(com.nexora.academic.AcademicTopic::getTopic).filter(t -> t != null && !t.isBlank()).distinct().toList();
        String selectedTopic = topics.contains(topic) ? topic : "";
        String selectedKind = "MCQ".equalsIgnoreCase(kind) ? "MCQ" : ("SUBJECTIVE".equalsIgnoreCase(kind) ? "SUBJECTIVE" : "");
        List<PYQQuestion> selectedTopicQuestions = List.of();
        if (!selectedTopic.isBlank() && !selectedKind.isBlank()) {
            try { selectedTopicQuestions = pyqService.getQuestionsForTopic(subjectCode, selectedTopic); }
            catch (RuntimeException ex) { LOGGER.log(java.util.logging.Level.WARNING, "Could not load PYQ intelligence for " + subjectCode, ex); }
        }
        List<PYQQuestion> questions = (selectedTopicQuestions == null ? List.<PYQQuestion>of() : selectedTopicQuestions).stream()
                    .filter(java.util.Objects::nonNull)
                    .filter(q -> q.getQuestionText() != null && !q.getQuestionText().isBlank())
                    .filter(q -> q.getYear() != null && q.getYear().trim().matches("(?:19|20)\\d{2}"))
                    .filter(q -> selectedKind.equals("MCQ")
                            ? q.getQuestionModel() != null && (q.getQuestionModel().toLowerCase(java.util.Locale.ROOT).contains("mcq") || q.getQuestionModel().toLowerCase(java.util.Locale.ROOT).contains("objective"))
                            : q.getQuestionModel() == null || !(q.getQuestionModel().toLowerCase(java.util.Locale.ROOT).contains("mcq") || q.getQuestionModel().toLowerCase(java.util.Locale.ROOT).contains("objective")))
                    .sorted(java.util.Comparator.comparingInt(AcademicController::yearSortKey).reversed()).toList();
        model.addAttribute("subject", subject);
        model.addAttribute("units", units);
        model.addAttribute("topics", topics);
        model.addAttribute("selectedUnit", unit == null ? "" : unit);
        model.addAttribute("selectedTopic", selectedTopic);
        model.addAttribute("selectedKind", selectedKind);
        model.addAttribute("questions", questions);
        model.addAttribute("indexedQuestionCount", safeQuestions(subjectCode).stream().filter(q -> q.getQuestionText() != null && !q.getQuestionText().isBlank()).count());
        return "academic-pyq-intelligence";
    }

    @PostMapping("/academic/{subjectCode}/important-topics/upload")
    public String uploadImportantTopics(@PathVariable("subjectCode") String subjectCode,
                                        @RequestParam("file") MultipartFile file,
                                        @RequestParam(required = false) Integer resourceYear,
                                        Principal principal) {
        AcademicSubject subject = findSubject(subjectCode);
        if (subject == null) return "redirect:/academic";
        if (!hasPremium(principal)) return "redirect:/profile?upgrade=premium#subscription";
        try {
            String reg = principal == null ? "STUDENT" : studentRepository.findByLoginId(principal.getName())
                    .map(com.nexora.model.Student::getRegistrationNo).orElse("STUDENT");
            academicResourceService.save(subjectCode, "IMPORTANT_TOPICS", file, "ImportantTopics",
                    resourceYear == null ? java.time.Year.now().getValue() : resourceYear, reg);
            academicPageReadCache.clear();
            return "redirect:/academic/" + subjectCode + "/important-topics?uploaded=1";
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? "Important-topics analysis failed." : ex.getMessage();
            return "redirect:/academic/" + subjectCode + "/important-topics?error=" + java.net.URLEncoder.encode(message, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    @GetMapping("/academic/{subjectCode}/important-topics")
    public String importantTopics(@PathVariable("subjectCode") String subjectCode, Principal principal, Model model,
                                  @RequestParam(required = false) String uploaded,
                                  @RequestParam(required = false) String error) {
        AcademicSubject subject = findSubject(subjectCode);
        if (subject == null) return "redirect:/academic";
        if (!hasPremium(principal)) return "redirect:/profile?upgrade=premium#subscription";
        model.addAttribute("subject", subject);
        model.addAttribute("units", safeUnits(subjectCode));
        List<AcademicResource> teacherFiles = safeResources(subjectCode, "IMPORTANT_TOPICS");
        model.addAttribute("teacherTopicFiles", teacherFiles);
        List<Map<String,Object>> detectedTopics = List.of();
        if (!teacherFiles.isEmpty()) {
            AcademicResource latest = teacherFiles.get(0);
            try {
                detectedTopics = jdbc.queryForList("SELECT topic,MAX(question_text) AS analysis_summary,MAX(confidence) AS confidence FROM academic_extracted_questions WHERE resource_id=? AND topic IS NOT NULL GROUP BY topic ORDER BY MIN(id)", latest.getId());
                model.addAttribute("latestTeacherTopicFile", latest);
            } catch (RuntimeException ex) {
                LOGGER.log(java.util.logging.Level.WARNING, "Could not load optional extracted topic analysis for " + subjectCode, ex);
                detectedTopics = List.of();
            }
        }
        detectedTopics = detectedTopics == null ? List.of() : detectedTopics.stream()
                .filter(java.util.Objects::nonNull)
                .filter(row -> row.get("topic") != null && !String.valueOf(row.get("topic")).isBlank())
                .toList();
        model.addAttribute("detectedTopics", detectedTopics);
        java.util.Map<String,List<PYQQuestion>> subjectiveByTopic = new LinkedHashMap<>();
        java.util.Map<String,List<PYQQuestion>> mcqByTopic = new LinkedHashMap<>();
        for (Map<String,Object> detected : detectedTopics) {
            String detectedName = String.valueOf(detected.get("topic"));
            List<PYQQuestion> matched = safeTopicQuestions(subjectCode, detectedName).stream()
                    .filter(q -> q.getQuestionText() != null && !q.getQuestionText().isBlank())
                    .filter(q -> q.getYear() != null && q.getYear().trim().matches("(?:19|20)\\d{2}"))
                    .sorted(Comparator.comparingInt(AcademicController::yearSortKey).reversed()).toList();
            subjectiveByTopic.put(detectedName, matched.stream().filter(q -> !isMcq(q)).toList());
            mcqByTopic.put(detectedName, matched.stream().filter(AcademicController::isMcq).toList());
        }
        model.addAttribute("subjectiveQuestionsByTopic", subjectiveByTopic);
        model.addAttribute("mcqQuestionsByTopic", mcqByTopic);
        model.addAttribute("hasSubjectiveQuestionMatches", subjectiveByTopic.values().stream().anyMatch(list -> list != null && !list.isEmpty()));
        model.addAttribute("hasMcqQuestionMatches", mcqByTopic.values().stream().anyMatch(list -> list != null && !list.isEmpty()));
        model.addAttribute("uploaded", uploaded != null);
        model.addAttribute("error", error);
        model.addAttribute("currentYear", java.time.Year.now().getValue());
        model.addAttribute("materials", safeResources(subjectCode, "STUDY_MATERIALS"));
        return "academic-important-topics";
    }

    @PostMapping("/academic/{subjectCode}/important-topics/materials.pdf")
    public ResponseEntity<byte[]> generateTopicMaterials(@PathVariable("subjectCode") String subjectCode,
                                                         @RequestParam("topics") List<String> requestedTopics,
                                                         Principal principal) {
        AcademicSubject subject = findSubject(subjectCode);
        if (subject == null) return ResponseEntity.notFound().build();
        if (!hasPremium(principal)) return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .header("X-NEXORA-Upgrade", "/profile?upgrade=premium#subscription").build();
        if (requestedTopics == null || requestedTopics.isEmpty()) return ResponseEntity.badRequest().build();
        java.util.Set<String> syllabusTopics = safeUnits(subjectCode).stream()
                .flatMap(u -> u.getTopics() == null ? java.util.stream.Stream.empty() : u.getTopics().stream())
                .map(com.nexora.academic.AcademicTopic::getTopic).filter(t -> t != null && !t.isBlank())
                .collect(java.util.stream.Collectors.toSet());
        List<String> selected = requestedTopics.stream().filter(syllabusTopics::contains).distinct().toList();
        if (selected.isEmpty()) return ResponseEntity.badRequest().build();
        try (PDDocument output = new PDDocument(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            int copied = 0;
            java.util.Set<String> copiedPages = new java.util.HashSet<>();
            // Outer loop follows the topic order supplied by the uploaded photo/form. A page
            // is copied only once even when it mentions multiple selected topics.
            for (String selectedTopic : selected) {
                for (AcademicResource resource : academicResourceService.find(subjectCode, "STUDY_MATERIALS")) {
                    Path path = academicResourceService.storagePath(resource.getId());
                    if (path == null || !Files.isRegularFile(path) || !path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) continue;
                    try (PDDocument source = Loader.loadPDF(path.toFile())) {
                        PDFTextStripper stripper = new PDFTextStripper(); PDFRenderer renderer = new PDFRenderer(source);
                        for (int page = 1; page <= source.getNumberOfPages(); page++) {
                            String pageKey = resource.getId() + ":" + page;
                            if (copiedPages.contains(pageKey)) continue;
                            stripper.setStartPage(page); stripper.setEndPage(page); String pageText = stripper.getText(source);
                            if (pageText == null || pageText.isBlank()) {
                                try (ByteArrayOutputStream imageBytes = new ByteArrayOutputStream()) {
                                    BufferedImage image = renderer.renderImageWithDPI(page - 1, 180, ImageType.GRAY);
                                    ImageIO.write(image, "png", imageBytes); pageText = ocr.extractImage(imageBytes.toByteArray());
                                } catch (Exception ignoredOcr) { pageText = ""; }
                            }
                            String text = (pageText == null ? "" : pageText).toLowerCase(java.util.Locale.ROOT);
                            if (containsTopicText(text, selectedTopic)) {
                                output.importPage(source.getPage(page - 1)); copiedPages.add(pageKey); copied++;
                            }
                        }
                    } catch (Exception ignored) { /* Keep processing other source materials. */ }
                }
            }
            if (copied == 0) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            output.save(bytes);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDisposition(ContentDisposition.attachment().filename(subjectCode + "_Important_Topics.pdf").build());
            headers.add("X-NEXORA-Source-Pages", Integer.toString(copied));
            return ResponseEntity.ok().headers(headers).body(bytes.toByteArray());
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ============================================================
    // SUBJECT LOOKUP
    // ============================================================

    private List<Topic> safeTopics(String subjectCode) {
        try {
            List<Topic> result = topicService.getTopicsForSubject(subjectCode);
            if (result != null && !result.isEmpty()) return result.stream().filter(java.util.Objects::nonNull).toList();
        } catch (RuntimeException ex) {
            LOGGER.log(java.util.logging.Level.WARNING, "Could not load topic index for " + subjectCode, ex);
        }
        List<Topic> fromSyllabus = new ArrayList<>();
        for (AcademicUnit unit : safeUnits(subjectCode)) {
            if (unit.getTopics() == null) continue;
            for (var topic : unit.getTopics()) {
                if (topic != null && topic.getTopic() != null && !topic.getTopic().isBlank())
                    fromSyllabus.add(new Topic(topic.getTopic(), "Syllabus topic", "", "", unit.getUnitName()));
            }
        }
        return fromSyllabus;
    }

    private List<TopicDependency> safeDependencies(String subjectCode) {
        try {
            List<TopicDependency> result = topicService.getDependenciesForSubject(subjectCode);
            return result == null ? List.of() : result.stream().filter(java.util.Objects::nonNull).toList();
        } catch (RuntimeException ex) {
            LOGGER.log(java.util.logging.Level.WARNING, "Could not load prerequisite map for " + subjectCode, ex);
            return List.of();
        }
    }

    private Topic safeTopicByName(String subjectCode, String topicName) {
        try {
            Topic result = topicService.getTopicByName(subjectCode, topicName);
            if (result != null) return result;
        } catch (RuntimeException ex) {
            LOGGER.log(java.util.logging.Level.WARNING, "Could not load topic " + topicName + " for " + subjectCode, ex);
        }
        return safeTopics(subjectCode).stream().filter(t -> t.getName() != null && t.getName().equalsIgnoreCase(topicName)).findFirst().orElse(null);
    }

    private List<String> safeTopicList(String subjectCode, String topicName, int kind) {
        try {
            List<String> result = switch (kind) {
                case 0 -> topicService.getPrerequisites(subjectCode, topicName);
                case 1 -> topicService.getDependentTopics(subjectCode, topicName);
                default -> topicService.getFullPrerequisiteChain(subjectCode, topicName);
            };
            return result == null ? List.of() : result.stream().filter(java.util.Objects::nonNull).filter(t -> !t.isBlank()).distinct().toList();
        } catch (RuntimeException ex) {
            LOGGER.log(java.util.logging.Level.WARNING, "Could not load topic relationships for " + subjectCode, ex);
            return List.of();
        }
    }

    private List<PYQQuestion> safeTopicQuestions(String subjectCode, String topicName) {
        String key = "topic-questions:" + normalizeCacheKey(subjectCode) + ":" + normalizeCacheKey(topicName);
        return academicPageReadCache.get(key, () -> {
            try {
                List<PYQQuestion> result = pyqService.getQuestionsForTopic(subjectCode, topicName);
                return result == null ? List.of() : result.stream().filter(java.util.Objects::nonNull).toList();
            } catch (RuntimeException ex) {
                LOGGER.log(java.util.logging.Level.WARNING, "Could not load topic questions for " + subjectCode, ex);
                return List.of();
            }
        });
    }

    private List<AcademicSubject> safeSubjectsForSemester(int semester) {
        String key = "subjects:semester:" + semester;
        return academicPageReadCache.get(key, () -> {
            try {
                List<AcademicSubject> result = academicSubjectService.getSubjectsForSemester(semester);
                return result == null ? List.of() : result.stream().filter(java.util.Objects::nonNull).toList();
            } catch (RuntimeException ex) {
                LOGGER.log(java.util.logging.Level.WARNING, "Could not load the academic subject catalogue for semester " + semester, ex);
                return List.of();
            }
        });
    }

    private List<AcademicUnit> safeUnits(String subjectCode) {
        String key = "units:" + normalizeCacheKey(subjectCode);
        return academicPageReadCache.get(key, () -> {
            try {
                List<AcademicUnit> result = topicService.getUnitsForSubject(subjectCode);
                return result == null ? List.of() : result.stream().filter(java.util.Objects::nonNull).toList();
            } catch (RuntimeException ex) {
                LOGGER.log(java.util.logging.Level.WARNING, "Could not build syllabus units for " + subjectCode, ex);
                return List.of();
            }
        });
    }

    private List<AcademicResource> safeResources(String subjectCode, String type) {
        String key = "resources:" + normalizeCacheKey(subjectCode) + ":" + normalizeCacheKey(type);
        return academicPageReadCache.get(key, () -> {
            try {
                List<AcademicResource> result = academicResourceService.find(subjectCode, type);
                return result == null ? List.of() : result.stream().filter(java.util.Objects::nonNull).toList();
            } catch (RuntimeException ex) {
                LOGGER.log(java.util.logging.Level.WARNING, "Could not load " + type + " for " + subjectCode, ex);
                return List.of();
            }
        });
    }

    private List<PYQQuestion> safeQuestions(String subjectCode) {
        String key = "questions:" + normalizeCacheKey(subjectCode);
        return academicPageReadCache.get(key, () -> {
            try {
                List<PYQQuestion> result = pyqService.getQuestionsForSubject(subjectCode);
                return (result == null ? List.<PYQQuestion>of() : result).stream()
                        .filter(java.util.Objects::nonNull)
                        .filter(q -> q.getQuestionText() != null && !q.getQuestionText().isBlank())
                        .filter(q -> q.getYear() != null && q.getYear().trim().matches("(?:19|20)\\d{2}"))
                        .toList();
            } catch (RuntimeException ex) {
                LOGGER.log(java.util.logging.Level.WARNING, "Could not load PYQ questions for " + subjectCode, ex);
                return List.of();
            }
        });
    }

    private String normalizeCacheKey(String value) {
        return value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private boolean googleDriveConfigured() {
        try { return googleDriveService.isConfigured(); }
        catch (RuntimeException ex) { return false; }
    }

    private AcademicSubject findSubject(String subjectCode) {
        if (subjectCode == null || subjectCode.isBlank()) return null;
        String cacheKey = "subject:" + normalizeCacheKey(subjectCode);
        java.util.Optional<AcademicSubject> result = academicPageReadCache.get(cacheKey, () -> {
            try {
                AcademicSubject saved = academicSubjectService.getSubjectByCode(subjectCode);
                if (saved != null) return java.util.Optional.of(saved);
            } catch (RuntimeException ex) {
                LOGGER.log(java.util.logging.Level.WARNING, "Could not load academic subject " + subjectCode, ex);
            }
            // Syllabus fallbacks are limited to the six verified Semester 3 course codes.
            AcademicSubject fallback = switch (subjectCode.trim().toUpperCase(java.util.Locale.ROOT)) {
                case "21MAB201T" -> new AcademicSubject("21MAB201T", "Transforms and Boundary Value Problems", true, true, true, 3, 1);
                case "21DCS201P" -> new AcademicSubject("21DCS201P", "Design Thinking Methodology", false, true, true, 3, 2);
                case "21CSS201T" -> new AcademicSubject("21CSS201T", "Computer Organization and Architecture", true, true, true, 3, 3);
                case "21CSC201J" -> new AcademicSubject("21CSC201J", "Data Structures and Algorithms", true, true, true, 3, 4);
                case "21CSC202J" -> new AcademicSubject("21CSC202J", "Operating System", true, true, true, 3, 5);
                case "21CSC203P" -> new AcademicSubject("21CSC203P", "Advanced Programming Practice", false, true, true, 3, 6);
                default -> null;
            };
            return java.util.Optional.ofNullable(fallback);
        });
        return result.orElse(null);
    }
}
