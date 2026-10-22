package com.nexora.development;

import com.nexora.career.CareerGpsService;
import com.nexora.career.CareerGpsSelection;
import com.nexora.career.market.CareerDirectoryService;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DevelopmentServiceImpl implements DevelopmentService {
    private final DevelopmentRepository repository;
    private final CareerDirectoryService directory;
    private final CareerGpsService careerGps;

    private static final List<String> FOUNDATION_SKILLS = List.of("Programming Fundamentals", "DSA", "SQL", "Git");

    public DevelopmentServiceImpl(DevelopmentRepository repository, CareerDirectoryService directory,
                                  CareerGpsService careerGps) {
        this.repository = repository;
        this.directory = directory;
        this.careerGps = careerGps;
    }

    public List<Skill> getSkills(String r) { return repository.findSkills(r); }
    public List<StudentProject> getProjects(String r) { return repository.findProjects(r); }
    public List<ExperienceEntry> getExperiences(String r) { return repository.findExperiences(r); }
    public CareerTarget getCareerTarget(String r) { return repository.findCareerTarget(r); }

    private static final Map<String, List<String>> PREREQUISITES = Map.of(
            "OOP", List.of("Java"),
            "Collections", List.of("Java", "OOP"),
            "DSA", List.of("Programming", "Java"),
            "Spring Boot", List.of("Java", "OOP", "SQL"),
            "REST API", List.of("HTTP", "Java"),
            "Data Visualization", List.of("Python", "Statistics"),
            "Advanced DSA", List.of("DSA", "Collections")
    );

    public List<String> getRecommendedSkills(String r) {
        CareerTarget target = repository.findCareerTarget(r);
        CareerGpsSelection selection = careerGps.find(r).orElse(null);
        List<Skill> skills = repository.findSkills(r);
        return getRecommendedSkillsFor(target, selection, skills);
    }

    @Override
    public List<String> getRecommendedSkillsFor(CareerTarget target, CareerGpsSelection selection, List<Skill> skills) {
        String roleName = target == null ? null : target.targetRole();
        if (roleName == null || roleName.isBlank() || "Not selected".equalsIgnoreCase(roleName.trim())) {
            roleName = selection == null ? null : selection.targetRole();
        }
        CareerDirectoryService.CareerRole role = directory.role(roleName);
        if (role == null && selection != null) role = directory.role(selection.targetRole());
        List<String> required = role == null || role.skills() == null || role.skills().isEmpty()
                ? FOUNDATION_SKILLS : role.skills();
        Set<String> existing = new HashSet<>();
        if (skills != null) skills.stream().filter(Objects::nonNull).map(Skill::name)
                .filter(Objects::nonNull).map(String::trim).filter(value -> !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT)).forEach(existing::add);
        List<String> result = new ArrayList<>();
        for (String skill : required) {
            if (skill == null || skill.isBlank() || existing.contains(skill.toLowerCase(Locale.ROOT))) continue;
            List<String> prerequisites = PREREQUISITES.getOrDefault(skill, List.of());
            boolean prerequisitesReady = prerequisites.stream().allMatch(p -> existing.contains(p.toLowerCase(Locale.ROOT)));
            if (prerequisitesReady || prerequisites.isEmpty()) result.add(skill);
        }
        if (result.isEmpty()) {
            for (String skill : required) {
                if (skill != null && !skill.isBlank() && !existing.contains(skill.toLowerCase(Locale.ROOT))) result.add(skill);
            }
        }
        return result.stream().distinct().limit(5).toList();
    }

    public Map<String,List<String>> getSkillGraph() { return PREREQUISITES; }

    public List<DevelopmentInsight> getInsights(String r) {
        List<Skill> skills = repository.findSkills(r);
        List<StudentProject> projects = repository.findProjects(r);
        List<ExperienceEntry> experience = repository.findExperiences(r);
        CareerTarget target = repository.findCareerTarget(r);
        CareerGpsSelection selection = careerGps.find(r).orElse(null);
        List<String> recommended = getRecommendedSkillsFor(target, selection, skills);
        return getInsightsFor(target, selection, skills, projects, experience, recommended);
    }

    @Override
    public List<DevelopmentInsight> getInsightsFor(CareerTarget target, CareerGpsSelection selection, List<Skill> skills,
            List<StudentProject> projects, List<ExperienceEntry> experience, List<String> recommendedSkills) {
        List<DevelopmentInsight> out = new ArrayList<>();
        List<Skill> safeSkills = skills == null ? List.of() : skills;
        List<StudentProject> safeProjects = projects == null ? List.of() : projects;
        List<ExperienceEntry> safeExperience = experience == null ? List.of() : experience;
        boolean targetConfigured = target != null && directory.role(target.targetRole()) != null;
        if (!targetConfigured && selection != null && directory.role(selection.targetRole()) != null) targetConfigured = true;
        if (!targetConfigured) out.add(new DevelopmentInsight("ACTION", "Set a career target", "Choose a target role so NEXORA can identify relevant skill gaps."));
        if (safeSkills.isEmpty()) out.add(new DevelopmentInsight("ACTION", "Add your current skills", "Your skill profile is empty, so recommendations are using the general foundation set."));
        if (safeProjects.isEmpty()) out.add(new DevelopmentInsight("ACTION", "Add a project", "Projects provide evidence that you can apply technical skills in practice."));
        if (safeExperience.isEmpty()) out.add(new DevelopmentInsight("ACTION", "Record your experience", "Add internships, hackathons, certifications or relevant activities."));
        if (targetConfigured && recommendedSkills != null && !recommendedSkills.isEmpty())
            out.add(new DevelopmentInsight("GAP", "Skill gap identified", "Some skills required for your selected role are not yet in your profile."));
        if (!safeSkills.isEmpty() && !safeProjects.isEmpty())
            out.add(new DevelopmentInsight("PROGRESS", "Profile has practical evidence", "Your skills are connected with at least one recorded project."));
        return List.copyOf(out);
    }

    public void addSkill(String r,String n,String l,String s,String notes){ if(n!=null&&!n.isBlank()) repository.addSkill(r,n.trim(),l,s,notes); }
    public void deleteSkill(String r,long id){ repository.deleteSkill(r,id); }
    public void addProject(String r,String t,String d,String tech,String role,String status){ if(t!=null&&!t.isBlank()) repository.addProject(r,t.trim(),d,tech,role,status); }
    public void addExperience(String r,String type,String title,String org,String d,String start,String end){ if(title!=null&&!title.isBlank()) repository.addExperience(r,type,title.trim(),org,d,start,end); }
    public void saveCareerTarget(String r,String role,String notes){ if(role!=null&&!role.isBlank()) repository.saveCareerTarget(r,role,notes); }
}
