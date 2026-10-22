# NEXORA Repair Audit — 13 October 2026

This source tree is a repair candidate, not a claim of successful production deployment. Open this folder's `pom.xml` in IntelliJ (Java 21) and test against the configured MySQL and mail services.

## Source-level fixes in this repair

- Academic home (`AcademicController` + `academic.html`) now loads the current-semester subject catalogue without preloading unit/resource/question aggregates for every subject. Subject overview no longer loads/deduplicates every PYQ merely to compute values the template does not use. Questions remain loaded on unit/topic/archive/intelligence routes where they are needed. Ordinary GET rendering does not synchronize Drive or run OCR.
- Career Selection (`CareerGpsController` + `career-gps.html`) now keeps on-campus and off-campus fields mutually exclusive, validates the placement path on the server, handles save messages as strings so primary/secondary success can be distinguished, and loads secondary candidates asynchronously. The candidate endpoint uses the same `gps.roleReadiness(...)` rule as the save handler so displayed choices and server-side validation agree.
- Career Guidance retains an expanded multi-family CSE directory and a visible O*NET role selector. The O*NET selector opens role-specific searches on the official O*NET OnLine website. This is a link-based reference, not an O*NET API integration.
- Neopat/Examly supports importing a provider-exported CSV snapshot into `nexora_neopat_progress`; the schema is provisioned during application initialization rather than by the import request. This is not automatic live synchronization; no provider API credential/integration was available in this project.
- The Academic subject sidebar's dead `/premium` URL now points to the existing profile/subscription route.

## How to check the result

1. Configure `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` for a MySQL database, and the required mail properties/environment variables for email OTP.
2. Start the application with Java 21; keep development review access disabled when testing normal authentication.
3. Test `/login`, signup/email verification, login OTP, logout, `/academic`, a subject page and its unit/archive routes, `/career`, `/career-selection` for both paths, and `/integrations` with a representative Neopat CSV.
4. Confirm schema initialization succeeds and look at application logs if a route reports empty data.

## Verification status

Static JavaScript syntax checks, key route/template checks, CSRF form checks and archive integrity checks were performed in the available environment. A complete Maven package/build and live MySQL/SMTP/browser smoke test could not be completed here because the Maven wrapper could not download Maven from Maven Central and no project database/mail credentials or running database were available. Do not describe the application as fully runtime-tested until the above checks pass in the configured environment.
