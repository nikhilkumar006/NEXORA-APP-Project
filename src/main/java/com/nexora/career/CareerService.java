package com.nexora.career;

import com.nexora.evidence.EvidenceRecord;
import com.nexora.evidence.EvidenceRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;

/**
 * Deterministic placement-readiness analysis. Every result is traceable to the
 * configured institutional source version and the student's recorded profile/evidence.
 * It is not a job-offer predictor and never produces a probability.
 */
@Service
public class CareerService {
    private final CareerRepository career;
    private final EvidenceRepository evidence;
    private final PlacementProfileService placementProfile;
    private final JdbcTemplate jdbc;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    public CareerService(CareerRepository career, EvidenceRepository evidence, PlacementProfileService placementProfile, JdbcTemplate jdbc) {
        this.career=career; this.evidence=evidence; this.placementProfile=placementProfile; this.jdbc=jdbc;
    }
    public void saveProfile(CareerProfile p){career.save(p);}
    public Optional<CareerProfile> profile(String r){return career.find(r);}
    public PlacementProfile placementProfile(String r){return placementProfile.get(r);}
    public void savePlacementProfile(PlacementProfile p){placementProfile.save(p);}

    /**
     * Calculates on-campus progress only from verified student evidence. Student-entered
     * planner values are intentionally not sufficient to establish institutional eligibility.
     */
    public RequirementProgress requirementProgress(String registrationNo, int yop, String sourceVersion, String band) {
        Map<String, String> verifiedValues = verifiedValues(evidence.findByStudent(registrationNo));
        List<Criterion> criteria = findVerifiedCriteria(yop, sourceVersion, band);
        int met = 0, unmet = 0, unknown = 0;
        for (Criterion criterion : criteria) {
            Assessment assessment = assess(criterion, verifiedValues);
            switch (assessment.status()) {
                case MET -> met++;
                case NOT_MET -> unmet++;
                case UNKNOWN -> unknown++;
            }
        }
        return new RequirementProgress(met, unmet, unknown, criteria.size());
    }

    /** Per-criterion student view; unknown means no verified evidence, not automatic failure. */
    public List<RequirementLine> requirementDetails(String registrationNo, int yop, String sourceVersion, String band) {
        Map<String, String> verified = verifiedValues(evidence.findByStudent(registrationNo));
        List<Criterion> criteria = findVerifiedCriteria(yop, sourceVersion, band);
        List<RequirementLine> lines = new ArrayList<>();
        for (Criterion criterion : criteria) {
            Assessment assessment = assess(criterion, verified);
            String status = switch (assessment.status()) {
                case MET -> "Verified — meets requirement";
                case NOT_MET -> "Verified — below requirement";
                case UNKNOWN -> "Unverified / not recorded";
            };
            String target = criterion.text() == null || criterion.text().isBlank()
                    ? (criterion.value() == null ? "—" : String.valueOf(criterion.value())) : criterion.text();
            String actual = verified.getOrDefault(normalizeMetric(criterion.metric()), "—");
            lines.add(new RequirementLine(displayMetric(criterion.metric()), target, actual, status,
                    assessment.status().name(), criterion.mandatoryFlag()));
        }
        return List.copyOf(lines);
    }

    public record RequirementLine(String metric, String target, String evidenceValue, String status,
                                 String statusCode, boolean mandatory) {}

    public record RequirementProgress(int established, int notMet, int unverified, int total) {
        /** Ratio of confirmed-met institutional criteria to all verified criteria in the selected policy. */
        public int percent() { return total == 0 ? 0 : (int) Math.round(established * 100.0 / total); }
    }

    /** Exact source documents used for the currently selected, verified criterion set. */
    public List<CriteriaSource> verifiedSources(int yop, String sourceVersion, String band) {
        return jdbc.query("""
                SELECT DISTINCT source_title, source_url, observed_at
                FROM career_criteria
                WHERE yop=? AND source_version=? AND band=? AND verified_flag=TRUE
                  AND source_url IS NOT NULL AND source_title IS NOT NULL AND observed_at IS NOT NULL
                ORDER BY observed_at DESC, source_title
                """, (rs, row) -> new CriteriaSource(rs.getString("source_title"),
                rs.getString("source_url"), rs.getDate("observed_at").toLocalDate().toString()),
                yop, sourceVersion, band);
    }

    public record CriteriaSource(String title, String url, String observedAt) {}

    /** Known policy versions are drawn from source-backed records; the current candidate is shown even before criteria are entered. */
    public List<String> availableSourceVersions() {
        LinkedHashSet<String> versions = new LinkedHashSet<>();
        versions.add("SRM-PLACEMENT-2026");
        List<String> stored = jdbc.query("""
                SELECT DISTINCT source_version FROM career_criteria
                WHERE verified_flag=TRUE AND source_url IS NOT NULL AND source_url<>''
                  AND source_title IS NOT NULL AND source_title<>'' AND observed_at IS NOT NULL
                ORDER BY source_version
                """, (rs, row) -> rs.getString(1));
        stored.stream().filter(Objects::nonNull).map(String::trim)
                .filter(v -> v.matches("SRM-PLACEMENT-[0-9]{4}")).forEach(versions::add);
        return List.copyOf(versions);
    }

    public Trajectory trajectory(String reg){
        List<EvidenceRecord> es=evidence.findByStudent(reg);
        PlacementProfile p=placementProfile.get(reg);
        String source=career.find(reg).map(CareerProfile::sourceVersion).filter(Objects::nonNull).filter(s->!s.isBlank()).orElse("SRM-PLACEMENT-2026");
        int yop=jdbc.queryForObject("SELECT COALESCE(MAX(batch_end_year),2029) FROM students WHERE registration_no=?",Integer.class,reg);
        Map<String,Double> evidenceValues=new HashMap<>();
        for(EvidenceRecord e:es){try{evidenceValues.merge(e.metric(),Double.parseDouble(e.value()),Math::max);}catch(Exception ignored){}}

        Map<String,CompletableFuture<BandResult>> tasks=new LinkedHashMap<>();
        for(String band:List.of("Standard","Dream","Super Dream","Marquee")){
            tasks.put(band,CompletableFuture.supplyAsync(() -> evaluateBand(reg,p,evidenceValues,yop,source,band),executor));
        }
        Map<String,BandResult> results=new LinkedHashMap<>();
        tasks.forEach((band,f)->results.put(band,f.join()));
        String current="Not enough evidence";
        for(String band:List.of("Standard","Dream","Super Dream","Marquee")) if(results.get(band).met()) current=band;
        int readinessIndex=(int)Math.round(100.0*results.values().stream().filter(BandResult::met).count()/4.0);
        String target=career.find(reg).map(CareerProfile::targetBand).orElse("Dream");
        String next=nextBand(current);
        List<String> gaps=new ArrayList<>(), actions=new ArrayList<>();
        if(!next.equals("Complete")){
            BandResult r=results.get(next);
            if(r!=null) { gaps.addAll(r.gaps()); actions.addAll(r.actions()); }
        }
        int codingProblems=(int)Math.round(value("coding_problems",p,evidenceValues));
        int projects=(int)Math.round(value("projects",p,evidenceValues));
        int internships=(int)Math.round(value("internships",p,evidenceValues));
        int certifications=(int)Math.round(value("certifications",p,evidenceValues));
        int hackathons=(int)Math.round(value("hackathons",p,evidenceValues));
        int verified=(int)es.stream().filter(this::isVerifiedRecord).count();
        double weeks=1.0;
        if(!es.isEmpty()){
            LocalDateTime min=es.stream().map(EvidenceRecord::capturedAt).min(LocalDateTime::compareTo).orElse(LocalDateTime.now());
            long days=Math.max(7,ChronoUnit.DAYS.between(min,LocalDateTime.now())); weeks=Math.max(1.0,days/7.0);
        }
        double codingPerWeek=codingProblems/weeks, evidencePerWeek=es.size()/weeks;
        return new Trajectory(current,target,readinessIndex,codingProblems,projects,internships,certifications,hackathons,verified,codingPerWeek,evidencePerWeek,gaps,actions);
    }

    private List<Criterion> findVerifiedCriteria(int yop, String source, String band) {
        return jdbc.query("SELECT metric,target_value,target_text,mandatory_flag FROM career_criteria WHERE yop=? AND source_version=? AND band=? AND verified_flag=TRUE AND source_url IS NOT NULL AND source_title IS NOT NULL AND observed_at IS NOT NULL ORDER BY metric",
                (rs, n) -> new Criterion(rs.getString(1), rs.getObject(2, Double.class), rs.getString(3), rs.getBoolean(4)), yop, source, band);
    }

    private BandResult evaluateBand(String registrationNo, PlacementProfile ignoredProfile, Map<String,Double> ignoredValues, int yop, String source, String band) {
        List<Criterion> criteria = findVerifiedCriteria(yop, source, band);
        List<String> gaps = new ArrayList<>(), actions = new ArrayList<>();
        if (criteria.isEmpty()) {
            gaps.add("No verified institutional criteria are configured for this passing year, source and package band.");
            actions.add("Add the official placement-policy document and verify the criteria before assessing eligibility.");
            return new BandResult(false, gaps, actions);
        }
        Map<String, String> verified = verifiedValues(evidence.findByStudent(registrationNo));
        for (Criterion criterion : criteria) {
            Assessment assessment = assess(criterion, verified);
            if (assessment.status() != CriterionStatus.MET) {
                String target = criterion.text() == null ? String.valueOf(criterion.value()) : criterion.text();
                if (assessment.status() == CriterionStatus.UNKNOWN) {
                    gaps.add(label(criterion.metric()) + ": no verified student evidence is recorded yet");
                    actions.add("Add institutionally/externally verified evidence for " + label(criterion.metric()) + " from a source accepted by NEXORA.");
                } else {
                    gaps.add(label(criterion.metric()) + ": verified evidence is below the published requirement (" + target + ")");
                    actions.add(action(criterion.metric(), target));
                }
            }
        }
        return new BandResult(gaps.isEmpty(), gaps, actions);
    }

    private Map<String, String> verifiedValues(List<EvidenceRecord> records) {
        Map<String, EvidenceRecord> latest = new HashMap<>();
        records.stream().filter(this::isVerifiedRecord)
                .filter(e -> e.metric() != null && !e.metric().isBlank())
                .sorted(Comparator.comparing(EvidenceRecord::capturedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .forEach(e -> latest.put(normalizeMetric(e.metric()), e));
        Map<String, String> values = new HashMap<>();
        latest.forEach((metric, record) -> values.put(metric, record.value()));
        return values;
    }

    private boolean isVerifiedRecord(EvidenceRecord record) {
        if (record == null || record.verificationStatus() == null) return false;
        String status = record.verificationStatus().trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        // Allow only statuses assigned by a supported integration or institutional verifier.
        // A label such as SELF_VERIFIED or READY_FOR_OFFICIAL_VERIFICATION is not authoritative.
        return Set.of("VERIFIED", "API_VERIFIED", "INSTITUTION_VERIFIED", "OAUTH_VERIFIED").contains(status);
    }

    private Assessment assess(Criterion criterion, Map<String, String> verifiedValues) {
        String metric = normalizeMetric(criterion.metric());
        String raw = verifiedValues.get(metric);
        if (raw == null || raw.isBlank()) return new Assessment(CriterionStatus.UNKNOWN);
        String value = raw.trim();
        if (Set.of("linkedin_updated", "portfolio", "eportfolio", "resume_verified", "profile_verified").contains(metric)) {
            Boolean actual = parseBoolean(value);
            if (actual == null) return new Assessment(CriterionStatus.UNKNOWN);
            return new Assessment(actual ? CriterionStatus.MET : CriterionStatus.NOT_MET);
        }
        if ("resume_level".equals(metric)) {
            return new Assessment(resumeRank(value) >= resumeRank(requiredResume(criterion.text())) ? CriterionStatus.MET : CriterionStatus.NOT_MET);
        }
        if ("professional_behaviour".equals(metric)) {
            return new Assessment(behaviourRank(value) >= behaviourRank(requiredBehaviour(criterion.text())) ? CriterionStatus.MET : CriterionStatus.NOT_MET);
        }
        try {
            double actual = Double.parseDouble(value);
            Double target = criterion.value();
            if (target == null) return new Assessment(CriterionStatus.UNKNOWN);
            boolean passed = "active_arrears".equals(metric) ? actual <= target : actual >= target;
            return new Assessment(passed ? CriterionStatus.MET : CriterionStatus.NOT_MET);
        } catch (NumberFormatException ex) {
            return new Assessment(CriterionStatus.UNKNOWN);
        }
    }

    private String normalizeMetric(String metric) {
        return metric == null ? "" : metric.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private String displayMetric(String metric) {
        return switch (normalizeMetric(metric)) {
            case "cgpa" -> "CGPA";
            case "tenth_percentile" -> "Class 10 marks (%)";
            case "twelfth_percentile" -> "Class 12 marks (%)";
            case "active_arrears" -> "Active arrears";
            default -> label(normalizeMetric(metric));
        };
    }

    private Boolean parseBoolean(String value) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "1", "true", "yes", "verified", "complete", "completed" -> Boolean.TRUE;
            case "0", "false", "no", "incomplete", "not completed" -> Boolean.FALSE;
            default -> null;
        };
    }

    private Double profileValue(String metric,PlacementProfile p){
        return switch(metric){
            case "CGPA" -> p.cgpa(); case "aptitude" -> p.aptitude(); case "technical_skills" -> p.technicalSkills();
            case "coding_problems" -> p.codingProblems()!=null?p.codingProblems().doubleValue():null; case "leetcode" -> p.leetcode()!=null?p.leetcode().doubleValue():null;
            case "hackerrank" -> p.hackerRank(); case "coding_assessment" -> p.codingAssessment(); case "internships" -> intv(p.internships());
            case "projects" -> intv(p.projects()); case "certifications" -> intv(p.certifications()); case "hackathons" -> intv(p.hackathons());
            case "competitive_programming" -> intv(p.competitiveProgramming()); case "github_repositories" -> intv(p.githubRepositories());
            case "implemented_projects" -> intv(p.implementedProjects()); case "communication" -> p.communication(); case "group_discussion" -> p.groupDiscussion();
            case "presentation" -> p.presentation(); case "technical_mock" -> p.technicalMock(); case "hr_mock" -> p.hrMock();
            case "placement_training_attendance" -> p.placementTrainingAttendance(); case "placement_assessment_attendance" -> p.placementAssessmentAttendance();
            case "industry_exposure" -> intv(p.industryExposure()); case "tenth_percentile" -> p.tenthPercentile(); case "twelfth_percentile" -> p.twelfthPercentile();
            case "active_arrears" -> intv(p.activeArrears()); default -> null;
        };
    }
    private Double intv(Integer i){return i==null?null:i.doubleValue();}
    private Boolean profileBoolean(String metric,PlacementProfile p){return switch(metric){case "linkedin_updated"->p.linkedInUpdated();case "portfolio"->p.portfolio();case "eportfolio"->p.ePortfolio();case "resume_verified"->p.resumeVerified();case "profile_verified"->p.profileVerified();default->false;};}
    private double value(String metric,PlacementProfile p,Map<String,Double> evidence){Double x=profileValue(metric,p);return x!=null?x:evidence.getOrDefault(metric,0d);}
    private String label(String s){return s.replace('_',' ');}
    private String action(String metric,String target){return "Update verified evidence/profile for "+label(metric)+" until it reaches: "+target;}
    private String requiredResume(String text){if(text==null)return "Basic Readiness";if(text.toLowerCase().contains("industry-standard"))return "Industry-standard ATS optimized";if(text.toLowerCase().contains("professional"))return "Professional ATS optimized";if(text.toLowerCase().contains("placement-ready"))return "Placement-ready";return "Basic Readiness";}
    private int resumeRank(String s){if(s==null)return 0;String x=s.toLowerCase();if(x.contains("industry"))return 4;if(x.contains("professional"))return 3;if(x.contains("placement"))return 2;return 1;}
    private String requiredBehaviour(String text){if(text==null)return "Satisfactory";if(text.toLowerCase().contains("excellent"))return "Excellent";if(text.toLowerCase().contains("good"))return "Good";return "Satisfactory";}
    private int behaviourRank(String s){if(s==null)return 0;return switch(s.toLowerCase()){case "excellent"->3;case "good"->2;default->1;};}
    private String nextBand(String current){return switch(current){case "Not enough evidence"->"Standard";case "Standard"->"Dream";case "Dream"->"Super Dream";case "Super Dream"->"Marquee";default->"Complete";};}
    private enum CriterionStatus { MET, NOT_MET, UNKNOWN }
    private record Assessment(CriterionStatus status) {}
    private record Criterion(String metric,Double value,String text,boolean mandatoryFlag){}
    private record BandResult(boolean met,List<String> gaps,List<String> actions){}
    @jakarta.annotation.PreDestroy public void shutdown(){executor.shutdown();}
}
