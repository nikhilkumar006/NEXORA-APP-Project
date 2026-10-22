# NEXORA whole-site repair notes — 13 October 2026

## Included repairs
- Removed `spring.profiles.default=review` so ordinary startup no longer silently activates the review profile. `NEXORA_DEV_ACCESS` remains false by default.
- Fixed Career Selection's path-dependent form state: campus selection does not require an off-campus career/company; off-campus selection requires both. Hidden/irrelevant controls are disabled to avoid stale form values.
- Expanded the CSE career directory with additional data, cloud, security, systems, product, enterprise and support roles. Existing family and role-detail routes remain the navigation source.
- Added role-specific official O*NET OnLine search links from career detail pages. These are external official searches, not an API integration; U.S. occupational data is not represented as Indian salary or eligibility evidence.
- Added the Neopat CSV import form to Connections, with CSRF token, file-size/format guidance, imported-record count and friendly error feedback. The existing controller imports student-provided CSV snapshots; this is not live Neopat sync.
- Added UTF-8 BOM handling to the first Neopat CSV header.
- Cached the academic subject catalogue per semester using the existing short-lived academic read cache to reduce repeated database reads when returning to Academic Home.

## Required runtime configuration
- Java 21 and MySQL configured using `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`.
- SMTP settings configured if testing registration/login email OTP.
- Drive folder and OAuth credentials are optional for corresponding integrations.

## Verification limits
- Static checks were run for the updated routes, form fields, profile defaults, O*NET link, Neopat upload form and CSV BOM handling.
- ZIP integrity is checked after packaging.
- A Maven test/build could not be run in this environment because the Maven wrapper distribution could not be downloaded from Maven Central. A real DB, SMTP and browser smoke test is still required in the configured environment; no claim of runtime verification is made.
