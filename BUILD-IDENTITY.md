# Build identity

Build ID: `NEXORA-2026-10-13-CAREER-GUIDANCE-FIX-R4`

This complete website source archive is based on the targeted NEXORA source tree with R4 updates. Career Guidance now renders only the CSE career fields on its landing page and only the mapped role titles within each field page. The academic read-cache lambda compiler error shown in the supplied screenshot is corrected. Existing unrelated modules are retained.

Validation completed: the fixed AcademicPageReadCache class compiled in isolation; the actual career-directory implementation compiled with its related data classes and was tested to verify all 74 unique role slugs are assigned to exactly one of the eight non-empty career fields; the career-explorer JavaScript syntax and career-template structure checks passed. A complete Maven build and live database/SMTP/browser acceptance suite could not be completed because the Maven distribution download was unavailable. Do not represent those runtime tests as passed until run locally.
