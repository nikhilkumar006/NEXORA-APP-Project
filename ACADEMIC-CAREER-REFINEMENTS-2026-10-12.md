# NEXORA Academic and Career refinements — 12 October 2026

## Implemented in this pass
- PYQ Archive now lists original paper PDFs only. Removed the OCR extracted-questions-by-year section and extracted-question count display.
- Archive actions now offer **Open Drive** only when an indexed paper has a Drive view link; the internal “Open paper” action was removed.
- Semester PYQ upload remains on the subject page only, outside each unit workspace. The month/year form fields were removed; the server parses `Month_Year.pdf` from the uploaded filename and rejects invalid names, empty files, non-PDF content types, and files larger than 20 MB.
- Removed the Upload PYQ panel from the chapter/unit workspace.
- Career Selection no longer displays the HOD planner criteria panel. The form now hides career selection for on-campus placement and shows only the package band; off-campus requires career and company.
- Premium prices remain ₹49 per semester and ₹89 per year.

## Explicit platform constraints
- A static UPI QR cannot force the payer's UPI app to lock an amount. For fixed-amount payment intent, use a payment gateway/dynamic UPI QR API and verify signed webhooks/server-side transaction status. The current manual QR + “I Paid” + administrator verification flow cannot guarantee amount-locking.
- Settings currently stores notification preferences. A preference being saved does not itself prove that email delivery or weekly summary jobs are wired and operational; test configured SMTP and scheduled delivery before promising notifications.
- LinkedIn visibility depends on configured OAuth client credentials and approved scopes. Automatic certificates/internships cannot be assumed from basic profile access.
- Live application build, database, Google Drive OAuth, and browser end-to-end tests are not certified in this environment.
