# NEXORA Academic Intelligence — Current Implementation Notes

## Student academic flow

1. The Dashboard and Academic Development page calculate current year/semester from the student's saved `batch_start_year` and the NEXORA academic calendar.
2. Academic home displays only the subjects mapped to that calculated semester, ordered by semester, display order and subject code. The student cannot manually choose the current semester.
3. Subject pages show their saved units, topics and dependencies in chapter order.
4. The student-facing PYQ archive uses approved semester question-paper resources and extracted topic-question records.
5. Uploads are validated, fingerprinted to prevent duplicate submissions, and held pending until the configured review flow approves them.
6. PDF text extraction and OCR provide source-linked extracted evidence. Failed extraction or uncertain topic mapping is not silently treated as verified.
7. XP is awarded only for eligible recorded platform events and successful approvals; the page does not invent an XP balance.

## Academic evidence

The analysis workflow reads the saved extracted text, maps it to configured syllabus topics, records the source file and retains confidence where available. Topic counts and weightage are shown only when supported by indexed semester-paper questions. Empty evidence sets are shown as unavailable rather than populated with estimates.

## Subscription

- Free: ₹0
- Premium: ₹49/semester, ₹89/year

Manual payment requests remain pending until an administrator reviews the submitted transaction details and evidence. Premium access is enforced server-side.

## Build note

A complete Maven build cannot be claimed in the isolated build environment because Maven wrapper/dependency downloads are unavailable there. Static checks are documented in `IMPLEMENTATION-STATUS-2026-10-11.md`.
