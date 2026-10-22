# Academic PPT Preview and Material Grouping — 12 Oct 2026

## Changes
- Drive-backed learning materials now use the Google Drive file viewer route (`/file/d/{fileId}/view`) instead of the generic `open?id=` route. This asks Drive to preview supported PowerPoint/PDF/Word files rather than treating them as a direct download link.
- The same subject template is used by all academic subjects, so the change applies consistently across every subject.
- Subject-wide learning materials are organized into chapter/unit groups when the filename explicitly identifies a unit or chapter (examples: `Unit 1 - ...pptx`, `Chapter 2.pdf`). Files without an unambiguous chapter marker remain visible in `Other / not chapter-labelled`; files are not discarded.
- The existing Sync Drive button and current unit navigation are preserved.

## Important grouping detail
The current academic resource table stores the filename and Drive ID but not the complete nested Drive folder path. Therefore grouping is reliable when the filename includes a unit/chapter marker. Files with generic names inside nested Drive folders are retained under `Other / not chapter-labelled` rather than being guessed into the wrong chapter. Persisting Drive folder ancestry would require a schema migration and a corresponding resource model update.

## Validation
- Checked Java source insertion and Thymeleaf template changes.
- ZIP integrity is checked after packaging.
- A full Maven package could not be run in this environment because the Maven wrapper could not download Maven from Maven Central (network fetch failed). Run `./mvnw -DskipTests package` in the project environment to perform the full compile.
