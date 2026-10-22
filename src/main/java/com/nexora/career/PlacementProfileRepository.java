package com.nexora.career;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class PlacementProfileRepository {
    private final JdbcTemplate jdbc;
    public PlacementProfileRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void save(PlacementProfile p) {
        jdbc.update("""
            INSERT INTO placement_profiles(
                registration_no,cgpa,active_arrears,aptitude,technical_skills,coding_problems,leetcode,hackerrank,
                coding_assessment,internships,projects,certifications,hackathons,competitive_programming,github_repositories,
                implemented_projects,linkedin_updated,portfolio,eportfolio,communication,group_discussion,presentation,
                technical_mock,hr_mock,resume_level,professional_behaviour,placement_training_attendance,
                placement_assessment_attendance,resume_verified,profile_verified,industry_exposure,tenth_percentile,twelfth_percentile
            ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON DUPLICATE KEY UPDATE
                cgpa=VALUES(cgpa),active_arrears=VALUES(active_arrears),aptitude=VALUES(aptitude),technical_skills=VALUES(technical_skills),
                coding_problems=VALUES(coding_problems),leetcode=VALUES(leetcode),hackerrank=VALUES(hackerrank),coding_assessment=VALUES(coding_assessment),
                internships=VALUES(internships),projects=VALUES(projects),certifications=VALUES(certifications),hackathons=VALUES(hackathons),
                competitive_programming=VALUES(competitive_programming),github_repositories=VALUES(github_repositories),implemented_projects=VALUES(implemented_projects),
                linkedin_updated=VALUES(linkedin_updated),portfolio=VALUES(portfolio),eportfolio=VALUES(eportfolio),communication=VALUES(communication),
                group_discussion=VALUES(group_discussion),presentation=VALUES(presentation),technical_mock=VALUES(technical_mock),hr_mock=VALUES(hr_mock),
                resume_level=VALUES(resume_level),professional_behaviour=VALUES(professional_behaviour),placement_training_attendance=VALUES(placement_training_attendance),
                placement_assessment_attendance=VALUES(placement_assessment_attendance),resume_verified=VALUES(resume_verified),profile_verified=VALUES(profile_verified),
                industry_exposure=VALUES(industry_exposure),tenth_percentile=VALUES(tenth_percentile),twelfth_percentile=VALUES(twelfth_percentile)
            """,
            p.registrationNo(),p.cgpa(),p.activeArrears(),p.aptitude(),p.technicalSkills(),p.codingProblems(),p.leetcode(),p.hackerRank(),
            p.codingAssessment(),p.internships(),p.projects(),p.certifications(),p.hackathons(),p.competitiveProgramming(),p.githubRepositories(),
            p.implementedProjects(),p.linkedInUpdated(),p.portfolio(),p.ePortfolio(),p.communication(),p.groupDiscussion(),p.presentation(),
            p.technicalMock(),p.hrMock(),p.resumeLevel(),p.professionalBehaviour(),p.placementTrainingAttendance(),p.placementAssessmentAttendance(),
            p.resumeVerified(),p.profileVerified(),p.industryExposure(),p.tenthPercentile(),p.twelfthPercentile());
    }

    public Optional<PlacementProfile> find(String reg) {
        return jdbc.query("SELECT * FROM placement_profiles WHERE registration_no=?", (r,n) -> new PlacementProfile(
                r.getString("registration_no"), r.getObject("cgpa", Double.class), r.getObject("active_arrears", Integer.class),
                r.getObject("aptitude", Double.class), r.getObject("technical_skills", Double.class), r.getObject("coding_problems", Integer.class),
                r.getObject("leetcode", Integer.class), r.getObject("hackerrank", Double.class), r.getObject("coding_assessment", Double.class),
                r.getObject("internships", Integer.class), r.getObject("projects", Integer.class), r.getObject("certifications", Integer.class),
                r.getObject("hackathons", Integer.class), r.getObject("competitive_programming", Integer.class), r.getObject("github_repositories", Integer.class),
                r.getObject("implemented_projects", Integer.class), r.getObject("linkedin_updated", Boolean.class), r.getObject("portfolio", Boolean.class),
                r.getObject("eportfolio", Boolean.class), r.getObject("communication", Double.class), r.getObject("group_discussion", Double.class),
                r.getObject("presentation", Double.class), r.getObject("technical_mock", Double.class), r.getObject("hr_mock", Double.class),
                r.getString("resume_level"), r.getString("professional_behaviour"), r.getObject("placement_training_attendance", Double.class),
                r.getObject("placement_assessment_attendance", Double.class), r.getObject("resume_verified", Boolean.class), r.getObject("profile_verified", Boolean.class),
                r.getObject("industry_exposure", Integer.class), r.getObject("tenth_percentile", Double.class), r.getObject("twelfth_percentile", Double.class)
        ), reg).stream().findFirst();
    }
}
