# NEXORA targeted repair — build R3

Date: 13 October 2026
Build ID: `NEXORA-2026-10-13-TARGETED-R3`

## Scope

This build targets only the requested areas: Academic Development performance, Career Guidance role mapping, Career Selection behaviour, Development GPS, Recommended Courses, and the minimum supporting Connections cache invalidation needed to keep Development GPS progress accurate. The rest of the website source is retained.

## Changes

### Academic Development
- Catalogue pages stay lightweight and do not preload all unit/question data for all subjects.
- Successful subject/read lookups are cached for 2 minutes; empty lookup results are cached briefly (3 seconds) to avoid hammering a slow database while still allowing recovery.
- Subject resolution uses the short-lived cache and existing verified fallbacks. Explicit sync paths still clear the academic cache.

### Career Guidance / mapping
- Saving the existing Career Guidance target persists a canonical role into the shared `CareerGpsSelection` record in the same transactional flow as the Career Profile.
- The Career Guidance landing page can recover the highlighted selected role from Career Selection when older records contain a missing Career Profile mapping.
- The Career Selection page resolves a saved target from the shared selection first, then falls back to a valid Career Guidance `CareerProfile` role when older records have a blank/unknown selection role.
- Career Guidance role detail links pass the role slug into `/career-selection?role=...`; the form preselects the role without silently changing on-campus/off-campus preference.
- The career directory already contains a broad CSE role catalogue and supports lookup by slug, normalized title, or alias. The included Claude prompt gives a complete audit/implementation task specifically for the Career Guidance page, mappings, O*NET search, role families, company/location/skill routes, and tests.

### Career Selection
- The target role is visible and required for both placement paths, so downstream modules always have a shared career key.
- Company and programme fields are shown and required as appropriate for off-campus selection; the package band is used only for the on-campus planning path.
- Company/programme and evidence criteria are fetched from the existing `/career-gps/programs` and `/career-gps/criteria` endpoints. Async responses use a sequence guard so a slow old response cannot overwrite the currently selected company/role. Safe DOM text rendering is used for external criteria labels.
- Criteria are displayed only when matching verified source records exist; the UI does not invent a guaranteed company requirement.

### Development GPS
- Uses one authenticated Student lookup on initial render and returns immediately from a short (6-second) per-student view-model cache on rapid repeat navigation.
- Resolves the canonical career role in this order: Career Selection, Career Guidance Profile, legacy Development target. It passes the same resolved role to skill-gap/insight calculations, avoiding a generic foundation recommendation when the selected role exists only in Career Guidance.
- Loads skills, projects, and experiences once per uncached request and reuses those values across recommendations and insights.
- Combines academic-mark/profile lookups and milestone counters into one query round-trip on the expected current schema. If an optional/older schema table is missing, the controller keeps its existing individually guarded fallback queries.
- Avoids duplicate threshold lookups for the eligibility summary and readiness table. Milestone averages work with any `Number` value type rather than assuming `Double`.
- Cache is evicted on project/experience writes, role changes, course-progress writes, and successful GitHub/Codeforces sync writes.

### Recommended Courses
- Resolves target role through Career Selection, then Career Guidance Profile, then legacy Development target. This keeps old student records usable even if one mapping table is stale.
- Role-aligned resource catalogue and `skillsToBuild` are computed separately from search results. Searching no longer changes the course-progress denominator.
- Completed resources stay visible so users can revisit them or update the status. Progress forms preserve the active search query across POST/redirect.
- Resource progress is scoped to the selected role and valid statuses, stored via a parameterized/upsert query, and evicts Development GPS cache after update.
- Premium access remains gated by default. For a local review, set `NEXORA_PREMIUM_REVIEW_ACCESS_ENABLED=true` to unlock Premium-labelled course/academic features without bypassing login. Never set it on a public production deployment. `NEXORA_DEV_ACCESS` remains a separate opt-in and is false by default.

### Connections / external sync
- The normal Connections GET does not perform a live remote GitHub sync; sync remains available via explicit POST and scheduled refresh. This prevents a slow provider response from blocking the page render.
- Successful GitHub or Codeforces sync evicts the GPS cache so updated coding evidence appears on the next load.

## Static validations performed

- ZIP can be listed/tested after packaging.
- Node syntax checks are run against the inline JavaScript in the modified Career Guidance, Career Selection, Recommended Courses, Academic and Development templates.
- Source checks confirm the referenced route names/controllers, role fallbacks, target-role save path, CSRF form field, page-cache invalidation hooks, and the optional Premium review configuration.
- Java structural delimiter scan is run on the changed Java files.

## Important test limitation

A full Maven compile, JUnit suite, live MySQL test, real SMTP OTP test, and browser click-through could not be executed in this environment because the Maven wrapper distribution download was unavailable. Static checks are not a substitute for these integration tests. Run the provided local checklist using your configured JDK 21/MySQL/SMTP setup before describing the application as runtime-verified.
