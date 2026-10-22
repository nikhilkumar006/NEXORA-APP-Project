# NEXORA Final Showdown — Academic Development and Empty-Data Audit

**Audit date:** 12 October 2026  
**Scope:** Academic Development, evidence-driven display rules, form/security markup, static asset/template integrity, and the previously repaired shared modules.  
**Authentication scope:** No new login, sign-up, or OTP pages were implemented in this pass. The existing local review-mode configuration and logout flow were retained.

## Changes made in this pass

### Academic Development

- Added a syllabus-topic and subtopic list to each chapter workspace. A chapter now remains useful when a class has not yet indexed PYQ questions or study PDFs.
- Confirmed that all six configured Semester 3 subjects resolve to five ordered, non-empty units. Each unit has a syllabus description and each topic has a non-empty title.
- Unsupported subject codes return no syllabus topics or units; the service does not fabricate curriculum for unknown codes.
- Fixed the subject archive entry rule: it appears when indexed papers/questions exist or a Google Drive read source is configured and can be synced. If the archive is empty and there is no Drive source, directly opening the archive returns the user to the subject workspace instead of rendering a blank archive.
- Added a first-run archive message only when Drive is configured, and the sync action can actually be used.
- Chapter and topic weightage panels are rendered only where validated, topic-mapped question evidence exists. Empty/zero evidence placeholders were removed.
- Empty chapter tests remain hidden until questions are configured. Study-material and upload panels remain conditional on existing material or the corresponding Drive configuration.
- PYQ archive question rows require question text and a valid four-digit academic year. Paper rows require a valid year and filename.

### Other empty/unavailable details

- Removed the contradictory “Awaiting OAuth setup” buttons from LinkedIn and GitHub cards. Those cards are already conditionally rendered only when an integration is enabled or a connection exists.
- Continued to suppress empty career-market values, unverified job rows, and invalid source URLs. A job is exposed as a listing only when it has identifiable organization/role fields and a direct valid HTTP(S) source URL.
- Removed older generated status/checklist reports from the project root so this report is the single current audit summary.
- The existing combined Settings & Profile page, review-mode unlocks, and logout flow were retained.

## Syllabus smoke verification

The `AcademicAnalysisService` was compiled independently using a minimal annotation stub because Spring dependencies could not be fetched in this environment. A standalone smoke program verified these six subject-code results:

| Subject code | Configured units | Syllabus topics |
|---|---:|---:|
| `21MAB201T` | 5 | 15 |
| `21DCS201P` | 5 | 14 |
| `21CSS201T` | 5 | 15 |
| `21CSC201J` | 5 | 32 |
| `21CSC202J` | 5 | 13 |
| `21CSC203P` | 5 | 15 |

All unit lists were ordered, all units had topic entries and non-empty summaries, and unknown subject codes returned empty lists.

## Structural verification results

- 36 Thymeleaf HTML templates parsed.
- 43 POST forms audited; each has an explicit CSRF field or a Thymeleaf `th:action` processed through Spring's request-data-value processor.
- No duplicate HTML IDs detected.
- No missing local CSS, JavaScript, or image references detected.
- 12 standalone CSS files parsed without parser errors.
- 23 inline style blocks parsed without parser errors.
- 5 external JavaScript files passed `node --check`.
- 8 inline script blocks were found; syntax-checkable plain JavaScript blocks passed `node --check`. Blocks containing Thymeleaf template expressions were excluded from Node syntax checking because they are not plain JavaScript until rendered.
- A source-wide `javac -proc:none` scan emitted missing-dependency diagnostics because Spring/JUnit/project dependencies are absent from the environment, but no Java syntax-pattern diagnostics were found.
- The final ZIP integrity check passed after packaging; the archive contains 286 files.

## External syllabus references

The syllabus-aligned course catalogues were cross-checked against these publicly accessible SRMIST documents:

- [SRMIST Regulations 2021 — CSE curriculum and syllabus](https://webstor.srmist.edu.in/web_assets/downloads/2023/csc-r2021-curriculum-and-syllabus.pdf) — includes the published DSA, Computer Organization and Architecture, Operating System, and Advanced Programming Practice syllabus material.
- [SRMIST Regulations 2021 — Mathematics higher-semester syllabi](https://webstor.srmist.edu.in/web_assets/downloads/2023/mathematics-syllabus-all-programmes-2021.pdf) — includes `21MAB201T`, Transforms and Boundary Value Problems.
- [SRMIST Regulations 2021 — Academic curricula](https://webstor.srmist.edu.in/web_assets/downloads/2023/academic-curricula-2021.pdf) — used to check course-code/course-title catalogue details.

The syllabus catalogue provides learning topics, not verified exam-mark distributions. Weightage is therefore still derived only from indexed, mapped PYQ data, not guessed from syllabus length.

## Important runtime/build limitation

The full Maven test command was attempted:

```bash
./mvnw -q test
```

It failed before compilation because the Maven Wrapper could not download Maven 3.9.16 from Maven Central (`wget: Failed to fetch https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.16/apache-maven-3.9.16-bin.zip`). The environment also had no installed Maven command or cached dependency set. As a result, this environment could not complete the full JUnit suite, start the full Spring Boot application, connect to MySQL, or run browser end-to-end tests.

**Accordingly, this report does not claim that all backend routes have passed live runtime tests.** The changes above passed the available standalone and static checks; Spring startup, database-backed routes, Drive/OCR integrations, and full end-to-end behavior still need to be exercised in an environment with Maven dependencies and the configured MySQL/integration services.
