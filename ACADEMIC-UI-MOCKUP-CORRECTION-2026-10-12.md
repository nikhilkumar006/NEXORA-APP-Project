# NEXORA Academic UI mockup correction — 12 October 2026

This pass is limited to the Academic UI, before further backend changes.

- Unit/chapter cards on the subject page now display the chapter name and navigation arrow only, matching the requested clean chapter-list view.
- Chapter workspace retains the syllabus/topics section and the two-column lower dashboard layout for Learning Material, Chapter Weightage, Topic Weightage and Prerequisites.
- Weightage and prerequisites cards remain visible when no indexed evidence exists, with explicit empty states instead of disappearing and shifting the layout. No sample percentages or prerequisite data are generated.
- No semester-PYQ upload controls are present inside a unit workspace. The contribution form remains on the subject page only and validates `Month_Year.pdf` on the server.
- PYQ Archive displays original papers grouped by year only. OCR-extracted question rows and counts are not rendered; paper actions use “Open Drive”.
- The existing Academic home dashboard and existing Spring Boot/Thymeleaf data model are retained.

## Verification boundary

Static template checks and ZIP integrity checks are performed for this UI-only pass. Full Maven/Spring runtime and browser screenshot comparison require running the app with its configured database and integrations; they are not claimed as passed here.
