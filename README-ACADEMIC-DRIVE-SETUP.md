# NEXORA Academic Drive + OCR Setup

## What this implementation does

1. Reads institutional academic files from the configured Google Drive folder.
2. Recursively detects subject/resource folders such as semester PYQ and study-material folders, plus teacher-important-topic evidence.
3. Downloads supported PDF/image files into NEXORA's local academic archive.
4. Extracts selectable PDF text with PDFBox.
5. Automatically falls back to Tesseract OCR for scanned/image-only PDFs and images.
6. Detects numbered questions deterministically.
7. Detects marks when a marks value is visible in the extracted question text.
8. Maps questions to the configured NEXORA subject topics using explicit keyword/topic rules.
9. Stores extracted questions and confidence in MySQL.
10. Shows the actual extracted questions on the subject page.
11. Stores the raw extracted text beside the downloaded document and provides an Extracted Text link.
12. When a student uploads a supported academic document, NEXORA validates it, analyzes it, stores it locally, and uploads it to the configured institutional Drive archive when write credentials are configured.

## Drive read access

### Public/readable source folder

Set:

- `NEXORA_DRIVE_FOLDER_ID`
- `NEXORA_DRIVE_API_KEY`

This is sufficient only when the source folder/files are readable with that API-key access.

### Private institutional Drive

Use a Google service account. Share the target Drive folder with the service-account email as an Editor/Writer. Then set:

- `NEXORA_DRIVE_FOLDER_ID`
- `NEXORA_DRIVE_UPLOAD_FOLDER_ID` (can be the same folder)
- `NEXORA_DRIVE_SERVICE_ACCOUNT_JSON` OR `NEXORA_DRIVE_SERVICE_ACCOUNT_FILE`

The service account is used for both reading the institutional folder and writing student uploads into it.

## OCR

Install Tesseract OCR on the Windows machine and make sure the selected language trained-data file exists.

Example:

`NEXORA_OCR_ENABLED=true`

`NEXORA_TESSDATA_PATH=C:\Program Files\Tesseract-OCR\tessdata`

`NEXORA_OCR_LANGUAGE=eng`

`NEXORA_OCR_DPI=300`

## Important behavior

- NEXORA does not fabricate question-paper data.
- OCR output is treated as extracted evidence and is shown with mapping confidence.
- If OCR cannot read a document, the resource is retained and marked `NEEDS_REVIEW` instead of being silently treated as verified.
- Student-uploaded teacher-question files remain student-submitted evidence; they are not automatically labeled as institutionally verified.
- An API key is not an upload credential. Automatic student upload-to-Drive requires a Drive write-capable identity such as the configured service account.
