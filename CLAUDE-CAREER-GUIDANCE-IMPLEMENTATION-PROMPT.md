# Claude implementation prompt — NEXORA Career Guidance only

You are working inside my existing NEXORA Java web project. **Implement the changes in the project files; do not reply with recommendations alone.** Inspect the whole existing codebase before editing, trace the actual routes and Thymeleaf templates, then make and test the required code changes.

## Scope boundary

The primary page to improve is **Career Guidance only** (`/career`). Do not redesign unrelated modules or replace the project with a new application. You may change only the Career Guidance templates/controllers/catalogue and the smallest integration points absolutely needed to make mappings work between Career Guidance, Career Selection, Development GPS, and Recommended Courses. Do not rewrite Academic Development, authentication, subscription/payment, achievements, Connections, or other modules. Preserve the current design language, navigation, session/security requirements, database contracts, and all existing working behavior.

## Existing stack and files to inspect first

- Java 21, Spring Boot, Spring MVC, Thymeleaf, Spring Security, MySQL/JdbcTemplate.
- Main Career Guidance controller: `src/main/java/com/nexora/controller/CareerController.java`.
- Role/family/company/location/skill detail routes: `src/main/java/com/nexora/controller/CareerExplorerController.java`.
- Main view reused by Career Guidance detail routes: `src/main/resources/templates/career.html`.
- Career catalogue and canonical role/slug/alias mapping: `src/main/java/com/nexora/career/market/CareerDirectoryService.java` and the market data service/classes it relies on.
- Career Selection form and shared target: `src/main/java/com/nexora/controller/CareerGpsController.java`, `src/main/resources/templates/career-gps.html`, and `CareerGpsSelection` / `CareerGpsService`.
- Development GPS: `DevelopmentController.java`, `DevelopmentServiceImpl.java`, `development.html`.
- Recommended learning: `RecommendedCoursesController.java`, `recommended-courses.html`.

Check all route annotations and template links before adding anything. Existing intended routes include `/career`, `/career/role/{slug}`, `/career/family/{slug}`, `/career/company/{slug}`, `/career/location/{slug}`, and `/career/skill/{slug}`. The current detail routes reuse `career.html` with a `pageType`; preserve that architecture unless the real source proves a specific route cannot be supported safely.

## Main objective

Build a complete, usable CSE career-guidance experience with a broad, well-organized catalogue of realistic Computer Science and Engineering career paths, correctly mapped detail pages, working search/filter/sort controls, useful role-specific guidance, and a reliable “Use this career” flow. Every card, chip, family, skill, company, location filter, CTA, and form submission must lead to a valid route and the expected view. No dead links, blank details, mismatched slugs, broken redirects, fake buttons, JavaScript console errors, or role values that silently fail to map.

## CSE career catalogue coverage

Audit the existing role list first. Preserve current useful entries, merge true duplicates, and add missing representative roles/aliases/skills/entry guidance to cover these broad families. Do not claim a literal finite list contains every possible job title; instead make it extensive, searchable, and easy to extend.

1. **Software engineering:** software engineer/developer, backend, frontend, full-stack, API/integration, Java, Python, .NET, systems, distributed systems, enterprise, application, solutions engineer, software architect, technical lead, mobile Android/iOS/cross-platform, web application, game development, graphics, developer tooling, low-level/performance, open-source engineering.
2. **Quality engineering:** QA engineer, manual test engineer, test automation, SDET, performance/load testing, reliability testing, accessibility testing, security testing, test infrastructure.
3. **Data and analytics:** data analyst, business intelligence analyst/engineer, analytics engineer, data engineer, database engineer/administrator, data architect, data quality/governance, data scientist, applied scientist, quantitative/data research, ETL/ELT engineer.
4. **AI and ML:** AI engineer, applied/generative AI engineer, LLM application engineer, ML engineer, deep-learning engineer, NLP engineer, computer-vision engineer, recommendation/search engineer, MLOps/LLMOps engineer, ML platform engineer, AI evaluation/safety engineer, AI research roles where appropriately labelled.
5. **Cloud, DevOps and reliability:** cloud engineer/architect, DevOps engineer, site reliability engineer, platform engineer, build/release engineer, CI/CD engineer, infrastructure automation, Kubernetes/container platform engineer, observability engineer, FinOps/cost optimisation roles.
6. **Cybersecurity:** security analyst/SOC, security engineer, application/product security, penetration tester (authorised testing only), red-team/blue-team roles, incident response/DFIR, threat intelligence, IAM, cloud security, DevSecOps, vulnerability management, governance/risk/compliance, privacy engineering.
7. **Networking and IT operations:** network engineer, systems administrator, Linux/Windows administrator, IT infrastructure engineer, systems engineer, cloud/network operations, technical support/escalation engineer, site/IT reliability, IT service management.
8. **Embedded and computer systems:** embedded software engineer, firmware engineer, IoT engineer, robotics software engineer, automotive software, device/edge computing, computer architecture/low-level systems, FPGA/ASIC/verification roles when aligned with the CSE catalogue.
9. **Business/product/enterprise technology:** technical business analyst, systems analyst, product analyst, technical product manager, product manager, technical program/project manager, scrum/agile technical roles, solutions architect, implementation/technical consultant, ERP/SAP developer or consultant, sales engineer/solutions consultant, customer reliability/technical account roles.
10. **Other CSE paths:** blockchain/distributed-ledger engineering when present, search/recommendation systems, AR/VR, developer relations/technical writing, academic teaching/research, entrepreneurship/startup engineering, digital forensics, accessibility engineering, and specialist pathways supported by existing catalogue fields.

Each role must have a stable unique slug, clear canonical title, family, aliases, meaningful required/core skills, “what you do,” a practical entry path/learning roadmap, relevant related roles, and suitable links or resources where the schema supports these. Keep role data source-backed where required. Do not invent salaries, rankings, demand metrics, vacancies, employer eligibility, or live-market statistics. Clearly distinguish curated guidance from verified market/employer evidence.

## Search, filters, and navigation

- Search must match role title, aliases, family, skills, and descriptions; trim/case-fold queries safely.
- Family, skill, company, location, sort, and filter controls must actually change the rendered results and preserve required query parameters when navigating.
- Each role opens `/career/role/{slug}` with the correct role; aliases should resolve to one canonical role.
- Family, company, location, and skill links must resolve only to real records. Invalid slugs should redirect safely to `/career` rather than cause a 500 or blank page.
- “Related careers” must exclude the current role, avoid duplicates, and never include null roles.
- Use Thymeleaf URL expressions or safe URL building. Do not concatenate unescaped user input into HTML/JavaScript. In JavaScript, populate user/data text with `textContent`, not `innerHTML`.
- All forms must include the correct CSRF token; do not disable CSRF or weaken Spring Security to make the UI work.
- Avoid doing external network/API calls synchronously while rendering the first page. Use existing cached/quick-read market services and show a clear empty/loading/error state for optional evidence.

## Mapping requirements — most important

There must be one predictable canonical role mapping across the modules:

1. Clicking **Use this career / Choose as my target** on a role detail page opens `/career-selection?role={role.slug}`.
2. Career Selection resolves the supplied slug or title through the existing `CareerDirectoryService`, preselects the exact canonical role, and allows the student to choose on-campus/off-campus independently. Do not silently change placement path just because a role link was clicked.
3. Saving Career Guidance’s own target must synchronize the role with the existing canonical `CareerGpsSelection`/`CareerGpsService` mapping without losing a compatible existing company choice. If the role changes, do not carry an old company/programme combination into the new role. Keep CareerProfile and CareerGpsSelection coherent.
4. Development GPS must show the canonical saved role, calculate recommendations and milestone labels for that role, and fall back safely to existing legacy Development target records only if the current CareerGpsSelection is missing/invalid.
5. Recommended Courses must resolve that same role by canonical slug/title/alias and show role-relevant resources. If the selected role is invalid or missing, fall back only to an actually valid legacy target; otherwise show a clear “choose a career” state, not a blank or generic wrong-role list.
6. Saving a target or changing a role must invalidate any related short-lived cache. Redirects must preserve the intended destination and show validation errors on the correct page.
7. Do not use role display strings as fragile database identifiers. Use the existing canonical slug/title/alias resolver; do not create duplicate role records for aliases.

## Role details and evidence

A role detail page should make these things clear without overwhelming the user:
- Role summary and realistic daily responsibilities.
- Required/core skills and what to learn first.
- Practical learning roadmap: fundamentals → guided practice → 2–3 portfolio projects → testing/deployment → interview preparation.
- Related roles and family navigation.
- Company, location, or market evidence only when the relevant data exists and the evidence fields are valid; include source and observed/updated date where applicable.
- A clear action to use the role in Career Selection and links to Development GPS and Recommended Courses.
- Loading, no-result, missing-data, and invalid-route states.

## Implementation and test requirements

1. Trace and explain the current request flow before changing it; do not assume filenames, endpoints, database columns, or model fields.
2. Implement the code changes in place; do not simply return snippets or pseudocode.
3. Add/extend automated tests where the existing project test setup supports them. Test slug/title/alias resolution, duplicate handling, invalid slugs, empty data, search/filter behaviour, redirects, and canonical role propagation.
4. Verify the Thymeleaf expressions used by the templates against actual model attributes and Java record getters. Check that no link points to a missing mapping/template.
5. Run the project’s Maven compile/tests using the repository wrapper (`./mvnw -DskipTests compile` and the relevant tests). If dependencies cannot be downloaded, state the exact blocker; never claim a build or runtime test passed if it did not.
6. Validate JavaScript syntax, route mappings, template references, and redirects. Where possible, test the page with an authenticated student and real development MySQL configuration.
7. Do not remove existing modules or silently change billing/authentication policies.
8. Keep an audit trail listing each file changed, each mapping fixed, tests actually executed, tests that could not be run, and remaining configuration dependencies.
9. Make a backup or use version control/diffs so the changes can be reviewed and rolled back.

## Definition of done

The work is complete only when the Career Guidance page is usable from the homepage through every family/role/company/location/skill detail route, each search/filter works, every role CTA maps the canonical role into Career Selection, and the saved selection is visible and correctly used by Development GPS and Recommended Courses after refresh. Provide the modified project files (and a ZIP only if you can confirm it contains the actual modified source), a concise change log, and honest build/test results. Do not say “everything works” based only on source edits or ZIP integrity.
