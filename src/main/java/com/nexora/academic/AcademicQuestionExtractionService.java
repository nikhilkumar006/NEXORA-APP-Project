package com.nexora.academic;

import com.nexora.document.OcrService;
import com.nexora.document.PdfTextService;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic academic document extraction. No generative model is used. */
@Service
public class AcademicQuestionExtractionService {
    private static final Pattern QUESTION_START = Pattern.compile(
            "(?im)^\\s*(?:question\\s*)?(\\d{1,2})(?:\\s*[.)]|\\s*[-:])\\s+(.+?)\\s*$");
    private static final Pattern MARKS = Pattern.compile("(?i)(?:\\(|\\[|\\b)(\\d{1,2})(?:\\s*marks?|\\s*M)(?:\\)|\\])?");

    private final PdfTextService pdfTextService;
    private final OcrService ocrService;
    private final TopicService topicService;
    private final AcademicQuestionRepository repository;

    public AcademicQuestionExtractionService(PdfTextService pdfTextService,
                                              OcrService ocrService,
                                              TopicService topicService,
                                              AcademicQuestionRepository repository) {
        this.pdfTextService = pdfTextService;
        this.ocrService = ocrService;
        this.topicService = topicService;
        this.repository = repository;
    }

    public ExtractionResult extract(byte[] bytes, String filename, String subjectCode,
                                    String resourceType, Integer year, long resourceId) {
        String text = "";
        String extractionMethod = "PDF_TEXT";
        try {
            if (isPdf(filename)) text = pdfTextService.extract(bytes);
            else if (isImage(filename)) text = ocrService.extractImage(bytes);
        } catch (Exception ignored) { }

        if (text == null) text = "";
        if (text.isBlank() && (isPdf(filename) || isImage(filename))) {
            try {
                text = isPdf(filename) ? ocrService.extractPdf(bytes) : ocrService.extractImage(bytes);
                extractionMethod = "OCR";
            } catch (Exception e) {
                extractionMethod = "OCR_FAILED: " + safe(e.getMessage());
            }
        }
        if (!text.isBlank() && extractionMethod.equals("PDF_TEXT") && isImage(filename)) extractionMethod = "OCR";

        List<ExtractedAcademicQuestion> questions = ("IMPORTANT_TOPICS".equals(resourceType) || "TEACHER_QUESTIONS".equals(resourceType))
                ? parseTeacherTopicEvidence(text, subjectCode)
                : parseQuestions(text, subjectCode);
        if (resourceId > 0) repository.replaceForResource(resourceId, subjectCode, resourceType, year, extractionMethod, questions);
        return new ExtractionResult(text, extractionMethod, questions);
    }

    private List<ExtractedAcademicQuestion> parseTeacherTopicEvidence(String text, String subjectCode) {
        if (text == null || text.isBlank()) return List.of();
        String normalized = text.replace('\r','\n');
        List<ExtractedAcademicQuestion> out = new ArrayList<>();
        int number=1;
        for (Topic topic : topicService.getTopicsForSubject(subjectCode)) {
            String tn=norm(topic.getName());
            if (tn.isBlank()) continue;
            String n=norm(normalized);
            int hits=0;
            int pos=0;
            while((pos=n.indexOf(tn,pos))>=0){hits++;pos+=Math.max(1,tn.length());}
            if(hits==0){
                String[] words=tn.split(" ");
                for(String w:words) if(w.length()>=4 && n.contains(w)) hits++;
            }
            if(hits>0) out.add(new ExtractedAcademicQuestion(String.valueOf(number++), topic.getName()+" — staff important-topic evidence ("+hits+" mention(s))", topic.getName(), "Teacher important topic", 0, Math.min(0.99,0.55+hits*0.08)));
        }
        return out;
    }

    private List<ExtractedAcademicQuestion> parseQuestions(String text, String subjectCode) {
        if (text == null || text.isBlank()) return List.of();
        String normalized = text.replace('\r', '\n').replace('\u0000', ' ')
                .replaceAll("[ \t]+\n", "\n").replaceAll("\n[ \t]+", "\n");
        String[] lines = normalized.split("\n");
        List<Block> blocks = new ArrayList<>();
        Block current = null;
        Pattern lineStart = Pattern.compile("(?i)^\\s*(?:q(?:uestion)?\\s*\\.?\\s*)?(\\d{1,2})\\s*[\\.)\\-:]\\s+(.+)$");
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isBlank()) { if (current != null) current.text.append(' '); continue; }
            Matcher m = lineStart.matcher(line);
            if (m.matches()) {
                if (current != null) blocks.add(current);
                current = new Block(m.group(1));
                current.text.append(m.group(2).trim());
            } else if (current != null) {
                String lower = line.toLowerCase(Locale.ROOT);
                if (lower.matches("page\\s+\\d+(\\s+of\\s+\\d+)?") || lower.matches("\\d+\\s+of\\s+\\d+")) continue;
                current.text.append(' ').append(line);
            }
        }
        if (current != null) blocks.add(current);

        // OCR can flatten the paper into one line. Split on subsequent question markers.
        if (blocks.size() <= 1) {
            String compact = normalized.replaceAll("\\s+", " ").trim();
            Matcher flat = Pattern.compile("(?i)(?:^|\\s)(?:q(?:uestion)?\\s*\\.?\\s*)?(\\d{1,2})\\s*[\\.)\\-:]\\s+").matcher(compact);
            List<int[]> markers = new ArrayList<>();
            while (flat.find()) markers.add(new int[]{flat.start(), flat.end(), Integer.parseInt(flat.group(1))});
            if (markers.size() >= 2) {
                blocks.clear();
                for (int i=0;i<markers.size();i++) {
                    int from=markers.get(i)[1], to=i+1<markers.size()?markers.get(i+1)[0]:compact.length();
                    if (to>from) { Block b=new Block(String.valueOf(markers.get(i)[2])); b.text.append(compact.substring(from,to).trim()); blocks.add(b); }
                }
            }
        }
        if (blocks.isEmpty()) {
            String compact = normalized.replaceAll("\\s+", " ").trim();
            if (compact.length()>30) { Block b=new Block("1"); b.text.append(compact); blocks.add(b); }
        }
        List<ExtractedAcademicQuestion> out=new ArrayList<>();
        Set<String> seen=new LinkedHashSet<>();
        for (Block b:blocks) {
            String q=cleanQuestion(b.text.toString()).replaceAll("(?i)\\bpage\\s+\\d+(\\s+of\\s+\\d+)?\\b", "").trim();
            if(q.length()<8 || !seen.add(norm(q))) continue;
            TopicMatch match=mapTopic(q,subjectCode);
            out.add(new ExtractedAcademicQuestion(b.number,q,match.topic,detectModel(q),detectMarks(q),match.confidence));
        }
        return out;
    }

    private TopicMatch mapTopic(String question, String subjectCode) {
        String normalized = norm(question);
        List<Topic> topics = topicService.getTopicsForSubject(subjectCode);
        String best = "Unmapped / review";
        double bestScore = 0;
        for (Topic t : topics) {
            String tn = norm(t.getName());
            if (tn.isBlank()) continue;
            if (normalized.contains(tn)) return new TopicMatch(t.getName(), 1.0);
            String[] words = tn.split(" ");
            int hit = 0;
            for (String w : words) if (w.length() >= 4 && normalized.contains(w)) hit++;
            if (hit > 0) {
                double score = Math.min(0.95, 0.45 + ((double) hit / Math.max(1, words.length)) * 0.5);
                if (score > bestScore) { bestScore = score; best = t.getName(); }
            }
        }
        Map<String,String> aliases = Map.ofEntries(
                Map.entry("linked list", "Linked List"), Map.entry("singly linked", "Singly Linked List"),
                Map.entry("doubly linked", "Doubly Linked List"), Map.entry("circular linked", "Circular Linked List"),
                Map.entry("matrix multiplication", "Matrix Multiplication"), Map.entry("time complexity", "Time Complexity"),
                Map.entry("space complexity", "Space Complexity"), Map.entry("binary search tree", "Binary Search Tree"),
                Map.entry("bst", "Binary Search Tree"), Map.entry("avl", "AVL Tree"), Map.entry("hashing", "Hashing"),
                Map.entry("breadth first", "Graph Traversal"), Map.entry("bfs", "Graph Traversal"),
                Map.entry("depth first", "Graph Traversal"), Map.entry("dfs", "Graph Traversal"),
                Map.entry("stack", "Stack"), Map.entry("queue", "Queue"), Map.entry("pointer", "Pointers"),
                Map.entry("structure", "Structures"), Map.entry("sorting", "Sorting"), Map.entry("search", "Searching")
        );
        for (Map.Entry<String,String> a : aliases.entrySet()) {
            if (normalized.contains(a.getKey())) {
                String candidate = a.getValue();
                for (Topic t : topics) if (norm(t.getName()).equals(norm(candidate))) return new TopicMatch(t.getName(), 0.90);
            }
        }
        return new TopicMatch(best, bestScore);
    }

    private String detectModel(String q) {
        String n = norm(q);
        // Preserve OCR-detected answer options as an objective/MCQ question type.
        if (Pattern.compile("(?is).*\\bA\\s*[).:]\\s+.+\\bB\\s*[).:]\\s+.+\\bC\\s*[).:]\\s+.+\\bD\\s*[).:]\\s+.+").matcher(q).matches()) return "MCQ / objective";
        if (n.matches(".*\\b(program|write a c|write code|implement|implementation)\\b.*")) return "Program / implementation";
        if (n.matches(".*\\b(algorithm|steps|procedure|method)\\b.*")) return "Algorithm / procedure";
        if (n.matches(".*\\b(compare|differentiate|distinguish|difference)\\b.*")) return "Compare / differentiate";
        if (n.matches(".*\\b(calculate|find|compute|evaluate|solve)\\b.*")) return "Problem solving / calculation";
        if (n.matches(".*\\b(draw|construct|trace|diagram)\\b.*")) return "Construction / tracing";
        if (n.matches(".*\\b(explain|describe|discuss)\\b.*")) return "Explain / discuss";
        if (n.matches(".*\\b(define|what is|state|list|short note)\\b.*")) return "Definition / short answer";
        return "Question";
    }

    private int detectMarks(String q) {
        Matcher m = MARKS.matcher(q);
        int best = 0;
        while (m.find()) { try { best = Math.max(best, Integer.parseInt(m.group(1))); } catch (Exception ignored) {} }
        return best;
    }

    private String cleanQuestion(String q) {
        return q.replaceAll("\\s+", " ").replaceAll("\\s+([,.;:])", "$1").trim();
    }
    private String norm(String s) { return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim(); }
    private boolean isPdf(String f) { return f != null && f.toLowerCase(Locale.ROOT).endsWith(".pdf"); }
    private boolean isImage(String f) { return f != null && f.toLowerCase(Locale.ROOT).matches(".*\\.(png|jpe?g|webp|bmp|tiff?)$"); }
    private String safe(String s) { return s == null ? "unknown" : s.replaceAll("[\\r\\n]+", " "); }

    private static class Block { String number; StringBuilder text = new StringBuilder(); Block(String n){number=n;} }
    private record TopicMatch(String topic, double confidence) {}
    public record ExtractionResult(String text, String method, List<ExtractedAcademicQuestion> questions) {}
}
