# NEXORA OCR setup (Windows / IntelliJ)

NEXORA uses Tess4J to call the Tesseract OCR engine for scanned PDFs. Tess4J 5.20.0 is declared in `pom.xml`.

## 1. Install Tesseract
Install a current Windows Tesseract build. The official Tesseract documentation lists Windows installers and explains that the OCR engine and language traineddata are separate pieces.

After installation, locate the Tesseract `tessdata` directory. A common location is:
`C:\Program Files\Tesseract-OCR\tessdata`

## 2. Check English language data
Make sure the folder contains:
`tessdata\eng.traineddata`

## 3. IntelliJ environment variables
Keep the existing NEXORA variables and add:

```text
NEXORA_OCR_ENABLED=true
NEXORA_TESSDATA_PATH=C:\Program Files\Tesseract-OCR\tessdata
NEXORA_OCR_LANGUAGE=eng
NEXORA_OCR_DPI=300
```

The code reads these values from environment variables, so no OCR path is hard-coded into the application.

## 4. Test
Run NEXORA, open **Documents**, choose a scanned/image-only PDF, and select **Run OCR on scanned PDF**.

Selectable PDFs can still use the existing **Extract PDF text** operation.

## Notes
- OCR is recognition of text from an image; it does not prove that a document is authentic.
- Clear scans and approximately 300 DPI generally give better recognition than low-resolution scans.
- If the app reports `eng.traineddata` missing, point `NEXORA_TESSDATA_PATH` at the folder that actually contains that file.
