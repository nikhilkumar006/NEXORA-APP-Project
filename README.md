# NEXORA — SRM CSE Student Platform

**Stack:** Java 21 · Spring Boot · Thymeleaf · MySQL · Spring Security

NEXORA is a server-rendered full-stack application. The project is intentionally kept in its existing Spring Boot structure; the supplied UI screenshots are design references, not the source of student metrics or eligibility decisions.

## Modules present in this source tree

- Dashboard and student profile/settings
- Career Selection and Development GPS: saved primary career/company, milestones, Semester-6 secondary-path gating, and source-backed readiness benchmarks from the two supplied SRMIST placement planner workbooks for 2028–2030 YOP. Off-campus eligibility remains exact-company/role/source gated.
- Career Guidance: alphabetical CSE career-family and role catalogue, role detail pages, city filters, dated market snapshots and exact-role evidence where available. No invented vacancy counts or eligibility thresholds.
- Development: student-recorded skills, projects, experience and career targets
- Academic Development: subjects, topics, academic resources, semester-end PYQ archive, PDF text/OCR pipeline, topic/question analysis and grade calculator
- Achievements (read-only synced evidence), Connections (LinkedIn profile OAuth, GitHub public-repository sync, Codeforces accepted-submission sync, and direct destinations for LeetCode, HackerRank, CodeChef and Kaggle), and Dashboard-only XP/reward request workflow
- Recommended Courses: target-role skill matching, official free learning resources and live paid-course search pages from course providers. Provider pricing and enrollment availability are checked at source.
- Opportunities Hub: links directly to the official Unstop website and lists its opportunity categories. No iframe or copied/fabricated local listings are used.
- Free and Premium subscription options only (Premium ₹49/semester or ₹89/year) with manual payment verification; Premium access is enforced server-side.
- Optional Swing database summary console

## Important source-integrity rules

- On-campus readiness uses the CORE + TRY values extracted from the two user-provided SRMIST placement planner workbooks, selected by year of passing and offer band. They are labelled as internal planning benchmarks, not independently verified employer cutoffs. Existing database criteria without a source title, URL and date remain unverified and excluded from verified eligibility calculations.
- An administrator can manage sourced placement criteria at `/admin/career-criteria`. This route requires an account with `ROLE_ADMIN` (configure `NEXORA_ADMIN_REGISTRATION` or `NEXORA_ADMIN_EMAIL` for the designated administrator); an administrator must check the exact institutional policy and confirm the source when saving each criterion. The application does not supply missing SRM placement rules itself.
- Career Guidance market snapshots cite their dated sources. They are not live vacancies, employer offers or guaranteed eligibility rules. Company pages explicitly state when no exact role-specific source is indexed.
- Student-entered skills, projects and evidence are not automatically employer-verified. GitHub public repository metadata and Codeforces accepted submissions are API-sourced; LinkedIn OIDC connection does not imply access to certificates/internships.
- Paid subscriptions activate only after an administrator approves the submitted payment request. Approval is transaction-protected; no automatic payment or payout is claimed.

## Subscription pricing

- **Free:** ₹0
- **Premium:** ₹49 per semester or ₹89 per year
- SRMIST HOD placement planner: the 2028, 2029 and 2030 YOP benchmarks are selected using the student's registered batch end year (with a four-year batch-start fallback when the end year is invalid). The same cohort planner is available in Career Selection and Development GPS.

Free is the default. Payment requests store the server-calculated fixed amount for the selected plan and period and are manually verified before activation. The currently supplied static QR cannot itself force the UPI app amount; an amount-bound dynamic QR requires the payee VPA or payment-provider API.

## Local setup

For a temporary local module review without login/signup/OTP screens, activate the `review` Spring profile in the IntelliJ Run Configuration (`Active profiles: review`). This enables the existing local student preview filter and redirects auth-screen routes to the dashboard. **Never activate this profile on a public or production deployment**; the normal profile keeps authentication enabled.

Requirements: JDK 21, MySQL, and network access to Maven Central on the first Maven-wrapper run.

1. Create a MySQL database named `nexora`.
2. Configure `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` in the environment.
3. Configure SMTP with `NEXORA_MAIL_HOST`, `NEXORA_MAIL_USERNAME`, `NEXORA_MAIL_PASSWORD`, and `NEXORA_MAIL_FROM` to use email OTP login/registration.
4. Start from IntelliJ using `NexoraApplication`, or run:

   ```bash
   ./mvnw test
   ./mvnw package
   ./mvnw spring-boot:run
   ```

5. With the `review` profile enabled and at least one existing active student record in the `nexora` database, open `http://localhost:8080/dashboard`. The review profile intentionally bypasses login/signup/OTP screens; it does not create a fake student if the database is empty.

The application initializes missing tables and applies compatible column additions at startup. Back up existing data before running an updated version against a non-test database. Review mode requires an existing active student row; the app intentionally does not generate fake student records.

## Security configuration

`NEXORA_DEV_ACCESS` defaults to **false**. For an isolated local preview only, a developer may explicitly set `NEXORA_DEV_ACCESS=true`; this temporarily selects the first active student and must never be enabled on a public deployment. Normal login uses password verification followed by an email OTP. Registration verifies an `@srmist.edu.in` address by OTP. OTP sends are throttled to one per minute and five per hour per identifier/action; codes expire after five minutes and allow at most five failed verification attempts. Authentication forms use CSRF tokens, and student-specific controllers no longer substitute another active student's record when a principal cannot be resolved.

Optional integrations remain disabled unless configured:

- Google OAuth: `NEXORA_GOOGLE_ENABLED=true` and OAuth client credentials
- GitHub / LinkedIn OAuth: their corresponding enable flags and client credentials
- Google Drive API: valid credentials and actual institutional folder IDs
- Tesseract OCR: install Tesseract and configure `NEXORA_TESSDATA_PATH`, `NEXORA_OCR_LANGUAGE`, and `NEXORA_OCR_DPI`
- Public market data pages: outbound HTTP access is needed; the market page falls back to unavailable values rather than fabricating missing live values

Never commit database, SMTP, OAuth, payment or institutional credentials.

## Validation performed for this package

- 36 Thymeleaf templates parsed; no duplicate IDs within any one template and no missing local CSS/JS/image assets found.
- 12 CSS files and all inline style blocks parsed with no CSS syntax errors.
- All 5 local JavaScript files passed `node --check`.
- POST-form scan found no missing CSRF token in the templates.
- The Development GPS and Career Guidance market snapshot is projected into explicit null-safe maps for Thymeleaf; nested market jobs/sectors use the same shape, and non-HTTP job links are dropped. A dedicated projection test was added.
- Added JUnit test cases for the Free/Premium-only plan catalogue and direct NCS advertisement links. The Maven test command was attempted, but the Maven wrapper could not download Maven 3.9.16 from Maven Central in the authoring environment. Therefore a full Java compile, Spring context test and database-backed end-to-end run are **not verified** in this environment. Run the Maven commands above in an environment with repository access before public deployment.

## Deployment status

This source package contains implemented changes and static validation, but it is **not certified production-ready** until Maven tests/build, live MySQL startup/migrations, SMTP OTP flows, admin authorization, subscription approval and external integration flows are exercised in a configured environment. See `IMPLEMENTATION-STATUS-2026-10-11.md` for the exact changes and remaining verification gates.

### Local module preview without login/signup work

Login, registration and OTP work is intentionally deferred while the main website modules are reviewed. For a **local-only** demo using an existing student record, set `NEXORA_DEV_ACCESS=true` in the IDE/run environment before starting Spring Boot. Keep it `false` or unset on any shared/public deployment; the preview filter is not a production authentication replacement and does not grant staff/admin access.

## Implementation update — 11 October 2026 (v2)

The latest source pass applies the requested module separation and workflows:

- A consistent global NEXORA header appears on student-facing modules. The extra menu block beside the profile avatar is hidden, and Logout is present in navigation.
- Dashboard content is intentionally limited to the welcome area and XP/redeem workflow. XP is not a separate student module.
- Career GPS is presented as **Career Selection**. It saves the on-/off-campus path and selected CSE career; the existing directory contains 27 curated career entries plus directory-backed roles. Development GPS consumes the saved target.
- Development GPS shows Class 10, Class 12 and CGPA eligibility only when matching verified criteria exist, plus progress counts from stored project, internship, hackathon, certification and problem records. Suggested milestone targets are preparation guidance, not employer eligibility rules. Manual skill entry is disabled.
- Class 10 and Class 12 percentages are optional Profile fields after registration.
- Connections shows LinkedIn as the only professional-profile provider, plus separate LeetCode, HackerRank, CodeChef, Codeforces, GitHub and Kaggle destinations. Manual certificate upload and manual problem mapping are removed; data is not shown as synced unless a real integration provides it.
- Academic Development shows only the automatically calculated current-semester subject list. The separate combined subject workspace and CLA-specific items are removed. Each subject opens its own submodule workspace, including learning materials, PYQ archive, chapter/topic evidence, Subject Preparation, and **Upload PYQ**.
- Student semester PYQs must be PDFs named `Month_Year.pdf` (for example `May_2025.pdf`). Drive indexing/OCR is scheduled nightly when Drive credentials are configured, with a manual subject sync still available.
- Premium **PYQ Intelligence** follows unit → topic → Subjective/MCQ and lists indexed questions newest-to-oldest. OCR-detected MCQs retain their option text for a cleaner layout. It never invents questions when no indexed source exists.
- Staff important-topic JPG/PNG/PDF uploads are OCR-processed. The workspace can generate a focused PDF from source-material pages that match selected syllabus topics, and link each detected topic to Subjective and MCQ PYQ views.
- SGPA / CGPA Calculator is a separate module with only two tabs. SGPA is locked to the current semester; submitting the current SGPA saves it automatically for CGPA history. Subject suggestions map the supplied Semester 3 credit values. The CGPA tab can import past-semester SGPA + credits to establish history. Development GPS reads the same cumulative calculation.
- Subscription plans are Free (₹0) and Premium (₹49/semester or ₹89/year), available inside Profile and Settings rather than a separate Premium module.

### Configuration and verification limits

The standalone `NEXORA-Visual-Preview.html` is for rapid UI review only; its demo interactions do not connect to Spring Boot or MySQL. Drive OCR/sync requires valid Drive credentials, OCR of images requires the configured OCR engine, and persistent use requires MySQL. A full Maven build/test could not be completed in this environment because the Maven wrapper could not download Maven 3.9.16. Static checks and browser checks of the standalone preview are not a substitute for a successful Java build and live integration tests.


### Local module-review mode (no login/logout screens)

For your current review, start with the Spring profile `review`. In IntelliJ set **Active profiles** to `review`. This profile bypasses login/signup/OTP screens and temporarily unlocks every Premium-gated section for testing while preserving the Premium labels in the UI. Premium access is not changed in the normal/default profile. Do not expose the review profile on a public deployment.
