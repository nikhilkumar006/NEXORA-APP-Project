# NEXORA Career Selection and Academic Drive Sync Hotfix — 12 Oct 2026

## Career Selection
- On-campus placement requires only the package band and no longer fails when the hidden target-career select is empty.
- An existing valid career is preserved when switching to on-campus; if the student has never chosen a career, `Not selected` is stored as the database-compatible sentinel so career-gated recommendations are not unlocked prematurely.
- The Career Selection page no longer silently preselects a career when no career has been selected.
- Off-campus placement still requires a valid role and company.

## Academic Drive Sync
- The manual sync request now sends Spring Security's CSRF token in the configured header, fixing the common 403 failure from browser `fetch()` POST calls.
- The page now displays the backend's actual HTTP or JSON error instead of hiding it behind a generic message.
- Sync results distinguish an inaccessible/configuration failure from a successful scan with supported files, and report discovery/index/skip/failure counts.
- PDF, PPT, and PPTX filtering remains in place; folders are never listed as materials.
- Existing processing logic stores `STUDY_MATERIALS` and `SEMESTER_PYQS` without OCR/question extraction, while retaining the original Drive links.

## Validation
- Parsed both modified Thymeleaf HTML templates with Python's HTML parser.
- Checked brace balance in the edited Java files.
- ZIP integrity is checked when packaging.
- Full Maven compile and live Google Drive verification were not possible in this environment because Maven 3.9.16 could not be downloaded. Verify against the project's configured database and Google Drive service-account access before production deployment.
