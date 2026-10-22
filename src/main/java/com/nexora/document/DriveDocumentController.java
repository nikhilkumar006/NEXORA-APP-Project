package com.nexora.document;

import com.nexora.academic.GoogleDriveService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class DriveDocumentController {
    private final GoogleDriveService drive;
    private final PdfTextService pdf;
    public DriveDocumentController(GoogleDriveService drive, PdfTextService pdf) { this.drive=drive; this.pdf=pdf; }

    @GetMapping("/documents/drive")
    public String extractDrive(@RequestParam String fileId, Model model) {
        try {
            byte[] bytes = drive.downloadFile(fileId);
            String text = pdf.extract(bytes);
            model.addAttribute("text", text);
            model.addAttribute("fileName", "Google Drive file " + fileId);
            model.addAttribute("note", text.isBlank() ? "No selectable text was found; OCR is required for a scanned PDF." : "Drive PDF text extracted successfully.");
        } catch (Exception e) {
            model.addAttribute("error", "Drive PDF extraction failed: " + e.getMessage());
        }
        return "documents";
    }
}
