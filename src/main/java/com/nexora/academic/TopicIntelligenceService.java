package com.nexora.academic;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Evidence-first Important Topics intelligence analyzer.
 * It does not invent teacher content. It reads approved IMPORTANT_TOPICS or legacy TEACHER_QUESTIONS
 * resources, extracts text, maps that text to the known subject topics,
 * then connects those topics to recorded PYQ models/dependencies.
 */
@Service
public class TopicIntelligenceService {
    private final JdbcTemplate jdbc;
    private final TopicService topicService;
    private final AcademicResourceService resources;
    private final PYQService pyqs;

    public TopicIntelligenceService(JdbcTemplate jdbc, TopicService topicService,
                                    AcademicResourceService resources, PYQService pyqs) {
        this.jdbc = jdbc;
        this.topicService = topicService;
        this.resources = resources;
        this.pyqs = pyqs;
    }

    public List<Insight> forSubject(String subjectCode) {
        Map<String, InsightBuilder> map = new LinkedHashMap<>();
        List<Topic> topics = topicService.getTopicsForSubject(subjectCode);
        List<AcademicResource> importantTopicFiles = jdbc.query("""
            SELECT id, subject_code, resource_type, title, original_filename, resource_month, resource_year,
                   uploaded_by, drive_web_view_link, processing_status, approval_status
            FROM academic_resources
            WHERE subject_code=? AND resource_type IN ('IMPORTANT_TOPICS','TEACHER_QUESTIONS')
              AND COALESCE(approval_status,'APPROVED')='APPROVED'
            ORDER BY resource_year DESC, id DESC
            """, (rs,n) -> {
                AcademicResource r=new AcademicResource(); r.setId(rs.getLong("id")); r.setSubjectCode(rs.getString("subject_code"));
                r.setResourceType(rs.getString("resource_type")); r.setTitle(rs.getString("title")); r.setOriginalFilename(rs.getString("original_filename"));
                r.setResourceMonth(rs.getString("resource_month")); int y=rs.getInt("resource_year"); r.setResourceYear(rs.wasNull()?null:y);
                r.setUploadedBy(rs.getString("uploaded_by")); r.setDriveWebViewLink(rs.getString("drive_web_view_link"));
                r.setProcessingStatus(rs.getString("processing_status")); r.setApprovalStatus(rs.getString("approval_status")); return r;
            }, subjectCode);

        for (AcademicResource file : importantTopicFiles) {
            List<ExtractedAcademicQuestion> extracted = jdbc.query("""
                SELECT topic, question_text, confidence FROM academic_extracted_questions
                WHERE resource_id=? AND topic IS NOT NULL AND TRIM(topic)<>''
                ORDER BY id
                """, (rs,n) -> new ExtractedAcademicQuestion(null, rs.getString("question_text"), rs.getString("topic"), "Teacher important topic", 0, rs.getDouble("confidence")), file.getId());
            if (!extracted.isEmpty()) {
                for (ExtractedAcademicQuestion q : extracted) {
                    if (q.getTopic() == null || q.getTopic().isBlank() || q.getTopic().equalsIgnoreCase("Unmapped / review")) continue;
                    Topic matched = topics.stream().filter(t -> t.getName().equalsIgnoreCase(q.getTopic())).findFirst().orElse(null);
                    if (matched == null) continue;
                    InsightBuilder b=map.computeIfAbsent(matched.getName(), k -> new InsightBuilder(matched));
                    b.count += 1; b.sources.add(file.getOriginalFilename()); b.sourceIds.add(file.getId()); b.monthYear.add(file.getDisplayPeriod());
                }
            } else {
                Path txt = resources.extractedTextPath(file.getId());
                String text = "";
                try { if (txt != null) text = Files.readString(txt); } catch (Exception ignored) {}
                String normalized = normalize(text);
                if (normalized.isBlank()) continue;
                for (Topic t : topics) {
                    String topicNorm=normalize(t.getName());
                    if (topicNorm.isBlank()) continue;
                    int count=occurrences(normalized, topicNorm);
                    if (count==0) {
                        String[] words=topicNorm.split(" "); int hits=0; for(String w:words) if(w.length()>=4 && normalized.contains(w)) hits++;
                        if(hits==0) continue; count=hits;
                    }
                    InsightBuilder b=map.computeIfAbsent(t.getName(), k -> new InsightBuilder(t));
                    b.count += count; b.sources.add(file.getOriginalFilename()); b.sourceIds.add(file.getId()); b.monthYear.add(file.getDisplayPeriod());
                }
            }
        }

        List<Insight> result=new ArrayList<>();
        for(InsightBuilder b:map.values()) {
            List<String> models=pyqs.getQuestionModelsForTopic(subjectCode,b.topic.getName());
            List<PYQModelStats> stats=pyqs.getModelStatisticsForTopic(subjectCode,b.topic.getName());
            result.add(new Insight(b.topic.getName(), b.topic.getUnit(), b.count, confidence(b.count),
                    new ArrayList<>(b.sources), new ArrayList<>(b.sourceIds), new ArrayList<>(b.monthYear),
                    topicService.getPrerequisites(subjectCode,b.topic.getName()), models, stats));
        }
        result.sort(Comparator.comparingInt(Insight::mentionCount).reversed().thenComparing(Insight::topic));
        return result;
    }

    public List<Insight> forTopic(String subjectCode, String topic) {
        return forSubject(subjectCode).stream().filter(i -> i.topic().equalsIgnoreCase(topic)).toList();
    }

    public List<String> recommendedPrerequisites(String subjectCode, String topic) {
        return topicService.getFullPrerequisiteChain(subjectCode, topic);
    }

    private double confidence(int count){ return Math.min(0.99, 0.55 + count*0.08); }
    private int occurrences(String text,String term){int c=0,from=0;while((from=text.indexOf(term,from))>=0){c++;from+=Math.max(1,term.length());}return c;}
    private String normalize(String s){return s==null?"":s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]"," ").replaceAll("\\s+"," ").trim();}

    private static class InsightBuilder { final Topic topic; int count; final Set<String> sources=new LinkedHashSet<>(); final Set<Long> sourceIds=new LinkedHashSet<>(); final Set<String> monthYear=new LinkedHashSet<>(); InsightBuilder(Topic t){topic=t;} }

    public record Insight(String topic, String unit, int mentionCount, double confidence,
                          List<String> sourceFiles, List<Long> sourceIds, List<String> periods,
                          List<String> prerequisites, List<String> pyqModels, List<PYQModelStats> modelStatistics) {
        public String importance(){ if(mentionCount>=5)return "High importance"; if(mentionCount>=3)return "Medium importance"; return "Mentioned by staff"; }
        public String evidenceSummary(){return mentionCount+" mapped mention(s) across "+sourceFiles.size()+" approved important-topic file(s)";}
    }
}
