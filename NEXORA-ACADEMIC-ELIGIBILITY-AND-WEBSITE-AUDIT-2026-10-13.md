# NEXORA academic eligibility and website audit — 13 October 2026

## Changes in this revision

- Development GPS now reads Class 10 / Class 12 marks from `student_academic_marks`, falling back to values already saved in `placement_profiles`.
- Development GPS now displays the CGPA derived from saved grade records even when the student has entered only some semesters; it no longer hides a valid partial cumulative result because future semesters are not entered yet.
- The placement planner always shows Class 10, Class 12, current CGPA, active backlog, degree/branch and graduation-year rows.
- Where exact source-verified criteria exist for the chosen employer/programme/year (or chosen on-campus planner band/year), those criteria take precedence for the matching academic metric.
- Where a matching source-verified criterion is unavailable, NEXORA uses a clearly labelled planning estimate: 60% in Class 10, 60% in Class 12, 6.0/10 CGPA, and aiming for zero active backlogs. These are not represented as company policy.
- Career Guidance role and company detail pages now always show an academic-readiness checklist, even when no employer-specific source criteria have been indexed. Verified source criteria remain separately labelled and linked.
- Unknown student scores display `Not entered yet`; the system does not invent a student's marks or CGPA.

## Audit performed

- Parsed `pom.xml` successfully.
- Parsed all 36 Thymeleaf HTML templates with Python's HTML parser.
- Parsed all JSON resources successfully.
- Ran `node --check` against JavaScript files under `src/main/resources/static`; no syntax errors were reported.
- Attempted the Maven wrapper compile, but the wrapper could not download Maven 3.9.16 from Maven Central in this environment. A full Java compile, test suite, application startup and live database / Google Drive integration test therefore remain unverified in this environment.

## Important data policy

Planning benchmarks fill the UI without fabricating a student's personal academic data or falsely claiming that an employer uses a particular cutoff. Exact employer requirements can vary by role, hiring programme, graduation year and current recruitment notice. Students should verify official criteria before applying.
