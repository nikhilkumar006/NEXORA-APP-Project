package com.nexora.controller;

import com.nexora.document.PdfTextService;
import com.nexora.evidence.CertificateVerificationService;
import com.nexora.evidence.EvidenceService;
import com.nexora.security.InvalidEvidenceException;
import com.nexora.repository.StudentRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Controller
public class EvidenceController {
    private static final long MAX_FILE_BYTES = 15L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "png", "jpg", "jpeg", "webp");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf", "image/png", "image/jpeg", "image/webp", "application/octet-stream");
    private final EvidenceService evidence;
    private final CertificateVerificationService certificateVerification;
    private final PdfTextService pdf;
    private final StudentRepository students;

    public EvidenceController(EvidenceService evidence, CertificateVerificationService certificateVerification,
                              PdfTextService pdf, StudentRepository students) {
        this.evidence = evidence;
        this.certificateVerification = certificateVerification;
        this.pdf = pdf;
        this.students = students;
    }

    @GetMapping("/evidence")
    public String page(Authentication authentication, Model model) {
        String registrationNo = requireStudent(authentication);
        model.addAttribute("evidence", evidence.all(registrationNo));
        return "evidence";
    }

    @PostMapping("/evidence/add")
    public String add(Authentication authentication, @RequestParam String category,
                      @RequestParam String metric, @RequestParam String value,
                      @RequestParam(defaultValue = "Student") String source,
                      @RequestParam(required = false) String referenceUrl,
                      @RequestParam(required = false) MultipartFile file,
                      @RequestParam(required = false) String notes) throws IOException {
        String registrationNo = requireStudent(authentication);
        String cleanCategory = cleanRequired(category, 60, "Choose an evidence category.");
        String cleanMetric = cleanRequired(metric, 100, "Enter the evidence metric.");
        String cleanValue = cleanRequired(value, 255, "Enter the evidence value.");
        String cleanSource = clean(source, 100);
        String cleanNotes = clean(notes, 1000);
        String cleanUrl = validateOptionalUrl(referenceUrl);
        String fileName = saveEvidenceFile(file, Path.of("nexora-data", "uploads"));
        // Client submissions are never allowed to set their own trusted verification status.
        evidence.add(registrationNo, cleanCategory, cleanMetric, cleanValue,
                cleanSource.isBlank() ? "Student" : cleanSource, "SELF_REPORTED", cleanUrl, fileName, cleanNotes);
        return "redirect:/evidence?saved";
    }

    @PostMapping("/evidence/certificate")
    public String certificate(Authentication authentication, @RequestParam String issuer,
                              @RequestParam String certificateNo,
                              @RequestParam(required = false) String verificationUrl,
                              @RequestParam(required = false) MultipartFile file,
                              @RequestParam(required = false) String extractedText) throws IOException {
        String registrationNo = requireStudent(authentication);
        String cleanIssuer = cleanRequired(issuer, 180, "Enter the certificate issuer.");
        String cleanCertificateNo = cleanRequired(certificateNo, 180, "Enter the certificate number.");
        String cleanUrl = validateOptionalUrl(verificationUrl);
        if (cleanUrl.isBlank() && verificationUrl != null && !verificationUrl.isBlank()) {
            throw new InvalidEvidenceException("Verification URL must be a valid HTTP or HTTPS URL.");
        }
        String cleanText = clean(extractedText, 200_000);
        String fileName = null;
        if (file != null && !file.isEmpty()) {
            fileName = saveEvidenceFile(file, Path.of("nexora-data", "certificates"));
            String extension = extension(file.getOriginalFilename());
            if ((cleanText.isBlank()) && "pdf".equals(extension)) {
                try { cleanText = pdf.extract(file.getBytes()); }
                catch (Exception ignored) { cleanText = ""; }
            }
        }
        // This is a deterministic text/structure check, not issuer authentication.
        String status = certificateVerification.assess(cleanIssuer, cleanCertificateNo, cleanUrl, cleanText);
        evidence.add(registrationNo, "CERTIFICATION", "certifications", "1", "Certificate upload", status,
                cleanUrl.isBlank() ? null : cleanUrl, fileName,
                "Issuer=" + cleanIssuer + "; Certificate No=" + cleanCertificateNo + "; Status is not official verification.");
        return "redirect:/evidence?certificate=" + status;
    }

    private String requireStudent(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null
                || authentication.getName().isBlank() || "anonymousUser".equals(authentication.getName())) {
            throw new InvalidEvidenceException("Sign in with your student account to manage evidence.");
        }
        return students.findByLoginId(authentication.getName()).map(s -> s.getRegistrationNo()).orElseThrow(
                () -> new InvalidEvidenceException("The signed-in account is not linked to a student record."));
    }

    private String saveEvidenceFile(MultipartFile file, Path configuredDirectory) throws IOException {
        if (file == null || file.isEmpty()) return null;
        if (file.getSize() > MAX_FILE_BYTES) throw new InvalidEvidenceException("Evidence files must be 15 MB or smaller.");
        String extension = extension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new InvalidEvidenceException("Evidence file must be PDF, PNG, JPG, JPEG or WEBP.");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT).trim();
        if (!contentType.isBlank() && !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new InvalidEvidenceException("The uploaded file type is not supported.");
        }
        Path root = configuredDirectory.toAbsolutePath().normalize();
        Files.createDirectories(root);
        String safeName = UUID.randomUUID() + "." + extension;
        Path target = root.resolve(safeName).normalize();
        if (!target.startsWith(root)) throw new InvalidEvidenceException("Invalid evidence file path.");
        try (var input = file.getInputStream()) {
            Files.copy(input, target);
        }
        return target.toString();
    }

    private String validateOptionalUrl(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String value = raw.trim();
        if (value.length() > 500) throw new InvalidEvidenceException("Reference URL must be 500 characters or fewer.");
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            if (uri.getHost() == null || scheme == null ||
                    !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))) {
                throw new IllegalArgumentException("Invalid URL");
            }
            return uri.toASCIIString();
        } catch (RuntimeException ex) {
            throw new InvalidEvidenceException("Reference URL must be a valid HTTP or HTTPS URL.");
        }
    }

    private String cleanRequired(String raw, int max, String message) {
        String value = clean(raw, max);
        if (value.isBlank()) throw new InvalidEvidenceException(message);
        return value;
    }

    private String clean(String raw, int max) {
        if (raw == null) return "";
        String value = raw.trim();
        return value.length() > max ? value.substring(0, max) : value;
    }

    private String extension(String filename) {
        if (filename == null) return "";
        String name = Path.of(filename).getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
