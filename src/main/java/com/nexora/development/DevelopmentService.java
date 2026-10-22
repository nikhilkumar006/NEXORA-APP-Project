package com.nexora.development;

import java.util.List;
import java.util.Map;
import com.nexora.career.CareerGpsSelection;

public interface DevelopmentService {
    List<Skill> getSkills(String registrationNo);
    List<StudentProject> getProjects(String registrationNo);
    List<ExperienceEntry> getExperiences(String registrationNo);
    CareerTarget getCareerTarget(String registrationNo);
    List<String> getRecommendedSkills(String registrationNo);
    List<String> getRecommendedSkillsFor(CareerTarget target, CareerGpsSelection selection, List<Skill> skills);
    Map<String,List<String>> getSkillGraph();
    List<DevelopmentInsight> getInsights(String registrationNo);
    List<DevelopmentInsight> getInsightsFor(CareerTarget target, CareerGpsSelection selection, List<Skill> skills, List<StudentProject> projects,
                                             List<ExperienceEntry> experiences, List<String> recommendedSkills);
    void addSkill(String registrationNo, String name, String level, String status, String notes);
    void deleteSkill(String registrationNo, long id);
    void addProject(String registrationNo, String title, String description, String technologies, String role, String status);
    void addExperience(String registrationNo, String type, String title, String organization, String description, String startDate, String endDate);
    void saveCareerTarget(String registrationNo, String targetRole, String notes);
}
