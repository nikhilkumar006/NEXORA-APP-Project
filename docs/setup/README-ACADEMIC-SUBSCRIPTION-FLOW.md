# NEXORA Academic + Subscription Flow

## Academic uploads
Students can contribute approved semester previous-year question papers using the supported `SEMESTER_PYQS` resource type. Files are validated, fingerprinted to block exact duplicates, and analysed with PDF extraction/OCR plus question, marks and topic mapping. Contributions remain pending until reviewed.

Approved resources are indexed in the academic archive. The website shows one row per saved source file and links extracted questions back to their source resource. An eligible approved contribution can award XP only once through the recorded event ledger.

## PYQ presentation
Topic pages group extracted questions by year (newest first), and show question models such as program/implementation, algorithm/procedure, compare/differentiate, explanation, calculation and construction/tracing when the indexed evidence supports them. Duplicate question records are suppressed in the student-facing archive.

## Automatic semester/year
The student's permanent `batch_start_year` is the source of truth. NEXORA calculates the current semester from the current date in the Asia/Kolkata time zone:
- August–December: odd semester
- January–May: even semester
- June–July: academic break; retain the most recently completed semester

Students cannot choose a different current semester on Academic Development. The page filters subjects using the calculated period and orders them using `display_order`.

## Subscriptions
Plans:
- Free: ₹0
- Premium: ₹49/semester, ₹89/year

Every student starts on Free. Payment is manual: choose Premium and a billing cycle, use the configured NEXORA QR, submit transaction reference/date/time and optional evidence, then wait for administrator verification. Only approval activates the paid plan.

Set `NEXORA_ADMIN_REGISTRATION` or `NEXORA_ADMIN_EMAIL` locally for the account that should access `/admin/review`. The payment QR is stored as `src/main/resources/static/images/nexora-payment-qr.png`.
