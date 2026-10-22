# NEXORA Academic Drive Sync Fix — 12 October 2026

## Changes
- Subject Learning Materials accepts PDF, PPT, PPTX, DOC and DOCX files.
- Drive discovery uses the registered academic subject catalogue in addition to the legacy subject-code/name aliases, so adding a subject does not require hard-coding every subject name in `GoogleDriveService`.
- Subject Materials list uses a canonical `https://drive.google.com/open?id=<fileId>` link when a Drive file ID is available. It no longer falls back to `#`, which made a click appear to return to the same page. If there is no Drive ID or valid Google Drive/Docs URL, it opens the indexed local resource endpoint.
- Sync feedback mentions supported formats and keeps sync scoped to the currently selected subject.
- Semester PYQs remain original-paper records; this change does not enable OCR or question extraction for semester papers.

## Runtime requirements
- Set `NEXORA_DRIVE_FOLDER_ID` to the Drive folder that contains the subject folders/files.
- For private folders, configure `NEXORA_DRIVE_SERVICE_ACCOUNT_JSON` or `NEXORA_DRIVE_SERVICE_ACCOUNT_FILE` and share the source folder with that service account's `client_email` as Viewer.
- For public folders only, configure `NEXORA_DRIVE_API_KEY` and ensure the folder/files are accessible to the API key's project.
- Restart the Spring Boot application after environment/configuration changes, open each subject's Learning Materials tab, and click **Sync Drive files for this subject**. Read the full status message if a folder is inaccessible.

## Validation note
The source and template were inspected and the archive integrity was checked. A full Maven compile and live Drive sync could not be executed in the packaging environment; those require Maven dependencies plus the user's real Drive configuration and database.
