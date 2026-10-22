package com.nexora.development;

import java.util.List;

public interface DevelopmentRepository {
    List<Skill> findSkills(String registrationNo);
    void addSkill(String registrationNo, String name, String level, String status, String notes);
    void deleteSkill(String registrationNo, long id);
    List<StudentProject> findProjects(String registrationNo);
    void addProject(String registrationNo, String title, String description, String technologies, String role, String status);
    List<ExperienceEntry> findExperiences(String registrationNo);
    void addExperience(String registrationNo, String type, String title, String organization, String description, String startDate, String endDate);
    CareerTarget findCareerTarget(String registrationNo);
    void saveCareerTarget(String registrationNo, String targetRole, String notes);
}
