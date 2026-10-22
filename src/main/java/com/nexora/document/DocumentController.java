package com.nexora.document;

import net.sourceforge.tess4j.TesseractException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class DocumentController {
    private final PdfTextService pdfTextService;
    private final OcrService ocrService;
    public DocumentController(PdfTextService pdfTextService, OcrService ocrService) { this.pdfTextService = pdfTextService; this.ocrService = ocrService; }

    @GetMapping("/documents")
    public String page() { return "documents"; }

    @PostMapping("/documents/extract")
    public String extract(Authentication authentication, @RequestParam MultipartFile file, Model model) {
        try {
            if (file.isEmpty() || !"application/pdf".equalsIgnoreCase(file.getContentType())) {
                model.addAttribute("error", "Please upload a PDF file."); return "documents";
            }
            String text = pdfTextService.extract(file.getBytes());
            model.addAttribute("text", text); model.addAttribute("fileName", file.getOriginalFilename());
            model.addAttribute("note", text.isBlank() ? "No selectable text was found. This is likely a scanned/image-only PDF. Use Run OCR on scanned PDF." : "Text extracted successfully. Review the source before mapping it to NEXORA topics.");
        } catch (Exception ex) { model.addAttribute("error", "PDF extraction failed: " + safeMessage(ex)); }
        return "documents";
    }

    @PostMapping("/documents/ocr")
    public String ocr(@RequestParam MultipartFile file, Model model) {
        try {
            if (file.isEmpty() || !"application/pdf".equalsIgnoreCase(file.getContentType())) {
                model.addAttribute("error", "Please upload a PDF file for OCR."); return "documents";
            }
            if (file.getSize() > 15L * 1024 * 1024) {
                model.addAttribute("error", "PDF files must be 15 MB or smaller."); return "documents";
            }
            String text = ocrService.extractPdf(file.getBytes());
            model.addAttribute("text", text); model.addAttribute("fileName", file.getOriginalFilename());
            model.addAttribute("note", "OCR completed page-by-page. Review the recognized text before using it for academic mapping.");
        } catch (TesseractException ex) {
            model.addAttribute("error", "OCR failed: " + safeMessage(ex));
        } catch (Exception ex) {
            model.addAttribute("error", "OCR could not run: " + safeMessage(ex));
        }
        return "documents";
    }

    private String safeMessage(Exception ex) { return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(); }
}
