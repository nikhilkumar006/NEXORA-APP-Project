# NEXORA Academic unit-tab and original-paper archive refinement

## Implemented in this pass
- Unit workspace now uses a three-option horizontal tab bar: Learning Materials, PYQ Archive, and Chapter Weightage & Prerequisites. Selecting a tab displays only that selected workspace.
- Learning Materials shows all indexed study-material files for the selected subject, rather than filtering them by the current unit name. Drive links are used when present; otherwise the indexed local file is opened.
- The PYQ view lists original semester paper files by year and exposes only Open Drive links when available. It never renders extracted questions.
- Semester PYQ processing is explicitly excluded from OCR/question extraction. Existing extracted-question rows associated with a semester paper are cleared when that paper is processed again.
- Student uploads preserve the descriptive original PDF filename and infer the year from a four-digit year in that filename. There are no month/year form fields. The year is required for year-wise archive grouping.
- Chapter weightage is normalized across all configured units using mapped marks, or mapped-question counts when marks are unavailable. Integer chapter percentages use a largest-remainder allocation so the displayed percentages sum to 100% when mapped evidence exists. No percentage is fabricated when there is no evidence.
- The prerequisites view lists every configured topic and lets the student expand it to see direct prerequisites, the full mapped prerequisite chain, dependent topics, unit locations, and clearly labeled syllabus-order guidance when no formal relationship is mapped.

## Verification limits
Static source and template checks were performed. A full Spring/Maven compile, MySQL integration test, Google Drive credentials test, and browser end-to-end run remain necessary. This environment has no system Maven installation, and Maven Wrapper dependency download was not used as a substitute for a successful build.
