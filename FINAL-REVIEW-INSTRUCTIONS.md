# NEXORA — manual review instructions

## 1. Temporarily skip login/signup/OTP during module review

In IntelliJ IDEA:
1. Open **Run → Edit Configurations**.
2. Select the NEXORA Spring Boot run configuration.
3. Set **Active profiles** to `review`.
4. Run the application and open `http://localhost:8080/`.

The `review` profile redirects login/signup/OTP routes to the dashboard and uses the local preview authentication filter. It is only for local module review. **Do not activate it on a public deployment.** Remove `review` from Active profiles to restore the normal secured authentication flow.

## 2. Database and source integrations

- Configure MySQL using the existing `application.properties` / environment variables.
- The on-campus planner cards use `src/main/resources/data/placement-planner.json`, extracted from the two original workbooks retained in `docs/source-data/`.
- Google Drive sync requires valid Drive credentials and the configured academic folder IDs.
- GitHub OAuth requires a registered OAuth app, callback URL and the `integrations` configuration profile/credentials. Only public, non-fork repositories are imported. Sync is scheduled every 15 minutes while the OAuth client token is available.
- Codeforces connection requires the student's handle. The official public API imports accepted problem URLs; initial backfill is capped at 10,000 recent submissions and new accepted submissions are refreshed hourly.
- LinkedIn OIDC imports only profile fields allowed by the configured permissions. Certificate and internship history require LinkedIn product/partner access; they are not fabricated if the application lacks that approval.

## 3. Subscription QR limitation

NEXORA calculates the expected amount server-side from the selected plan and period (Premium: ₹49/semester, ₹89/year). The current QR is a static QR and cannot force every UPI app to lock its amount field. A truly amount-bound QR requires the payee VPA or a payment-provider dynamic-QR API. The manual approval workflow must reject amount mismatches.

## 4. Verification boundary

Static checks were run for HTML parsing/duplicate IDs, local asset references, CSS parsing, inline JavaScript syntax, and Java syntax patterns. A full Maven build and authenticated browser E2E could not be completed in this environment because the Maven Wrapper could not download Maven 3.9.16 and no local MySQL service was available.

If a route still displays **“Something needs attention”**, copy the exception stack trace from the IntelliJ Run console and include the route URL. The generic browser page intentionally does not expose database/provider secrets.


## Premium and authentication review mode

The ZIP defaults to the `review` Spring profile while authentication is deferred. It temporarily unlocks all Premium-gated modules (they remain visibly labelled Premium) and routes login, signup, verification, and logout navigation back to the dashboard. No login/logout screen is intended to appear in this mode. The override is controlled by `nexora.premium-review-access.enabled=true` in `application-review.properties`; it defaults to false outside this profile. Never enable this setting on a public deployment.

## XP, PYQ integrity, and chapter tests (review build)

- Approved unique semester PYQ contribution: **10 contribution XP**, awarded only after OCR validation, metadata validation, duplicate checks, and successful Google Drive archival.
- Contributor redemption: **₹25 for 50 available contribution XP**. Pending/paid requests reserve points; rejected claims release them.
- Confirmed paper subject-code/name or exam month/year mismatch: reject the upload and show a warning. The first three confirmed violations warn; the fourth deactivates the student account. An account disabled for this rule is blocked even if an old session is open.
- If the OCR cannot confidently read the paper's subject and month/year metadata, the upload is rejected without XP and without a cheating warning; this avoids treating unreadable scans as proof of cheating.
- Full chapter tests use server-side answer keys. A score of **75% or higher** awards **5 XP once per student per subject/chapter**; submitted score values from the browser are never trusted.
- Add actual MCQ test questions at `http://localhost:8080/staff/chapter-tests` while the local `review` profile is active. Choose the real subject and chapter index, enter all four options, and set the answer key. No demo questions are seeded. In non-review profiles this route remains restricted to staff/admin.
- Google Drive archiving requires valid Drive service-account credentials and `NEXORA_DRIVE_FOLDER_ID` (or `nexora.drive.upload-folder-id`). The app creates/reuses a subject-code folder and then a resource-type folder under it. Without working Drive access, the upload does not earn XP.

## Performance and calendar checks
- Dashboard greeting is based on Asia/Kolkata time: 00:00–11:59 = Good Morning; 12:00–23:59 = Good Evening.
- Semester is based on the student's configured batch start year and India-local date; students do not manually select it.
- The repository avoids repeated student re-queries and writes academic period changes only when the stored year/semester changes.
- For a fast sanity check, test the greeting boundary at 11:59 AM and 12:00 PM IST and the semester transitions in May/June and July/August. The added unit tests cover the underlying boundary logic.
