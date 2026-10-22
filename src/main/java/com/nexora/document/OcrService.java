package com.nexora.document;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class OcrService {
    private final boolean enabled;
    private final Path tessdataPath;
    private final String language;
    private final float dpi;

    public OcrService(
            @Value("${nexora.ocr.enabled:true}") boolean enabled,
            @Value("${nexora.ocr.tessdata-path:tessdata}") String tessdataPath,
            @Value("${nexora.ocr.language:eng}") String language,
            @Value("${nexora.ocr.dpi:300}") float dpi) {
        this.enabled = enabled;
        this.tessdataPath = Path.of(tessdataPath).toAbsolutePath().normalize();
        this.language = language;
        this.dpi = dpi;
    }

    public String extractImage(byte[] bytes) throws IOException, TesseractException {
        if (!enabled) throw new IllegalStateException("OCR is disabled. Set NEXORA_OCR_ENABLED=true.");
        Path trainedData = tessdataPath.resolve(language + ".traineddata");
        if (!Files.exists(trainedData)) {
            throw new IllegalStateException("OCR language data is missing: " + trainedData + ". Install Tesseract language data or set NEXORA_TESSDATA_PATH.");
        }
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null) throw new IllegalArgumentException("The uploaded image could not be decoded.");
        ITesseract tesseract = new Tesseract();
        tesseract.setDatapath(tessdataPath.toString());
        tesseract.setLanguage(language);
        tesseract.setPageSegMode(6);
        return tesseract.doOCR(image).trim();
    }

    public String extractPdf(byte[] bytes) throws IOException, TesseractException {
        if (!enabled) throw new IllegalStateException("OCR is disabled. Set NEXORA_OCR_ENABLED=true.");
        Path trainedData = tessdataPath.resolve(language + ".traineddata");
        if (!Files.exists(trainedData)) {
            throw new IllegalStateException("OCR language data is missing: " + trainedData + ". Install Tesseract language data or set NEXORA_TESSDATA_PATH.");
        }
        ITesseract tesseract = new Tesseract();
        tesseract.setDatapath(tessdataPath.toString());
        tesseract.setLanguage(language);
        tesseract.setPageSegMode(6);

        StringBuilder all = new StringBuilder();
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFRenderer renderer = new PDFRenderer(document);
            for (int page = 0; page < document.getNumberOfPages(); page++) {
                BufferedImage image = renderer.renderImageWithDPI(page, dpi, ImageType.GRAY);
                String text = tesseract.doOCR(image);
                all.append("--- Page ").append(page + 1).append(" ---\n");
                if (text != null) all.append(text.trim());
                all.append("\n\n");
            }
        }
        return all.toString().trim();
    }
}
