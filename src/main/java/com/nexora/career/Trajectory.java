package com.nexora.career;
import java.util.List;
public record Trajectory(String currentBand,String targetBand,int readinessIndex,int codingProblems,int projects,int internships,int certifications,int hackathons,int verifiedEvidence,double codingProblemsPerWeek,double evidenceEventsPerWeek,List<String> gaps,List<String> actions) {}
