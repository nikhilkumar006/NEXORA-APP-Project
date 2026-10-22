package com.nexora.academic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Institutional academic Drive reader.
 *
 * Read access can use either:
 *  - NEXORA_DRIVE_API_KEY for a publicly readable folder, or
 *  - NEXORA_DRIVE_SERVICE_ACCOUNT_JSON / FILE when the institutional folder is shared with a service account.
 */
@Service
public class GoogleDriveService {
    private final String semester3FolderId;
    private final String apiKey;
    private final DriveAuthService auth;
    private final AcademicSubjectService subjects;
    private final RestTemplate restTemplate = createRestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile List<DriveFile> cachedDriveFiles = List.of();
    private volatile long cachedDriveFilesAt = 0L;
    private static final long DRIVE_LIST_CACHE_MILLIS = 20_000L;

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(5));
        factory.setReadTimeout(java.time.Duration.ofSeconds(12));
        return new RestTemplate(factory);
    }

    public GoogleDriveService(
            @Value("${nexora.drive.semester3.folder-id:}") String semester3FolderId,
            @Value("${NEXORA_DRIVE_API_KEY:}") String apiKey,
            DriveAuthService auth,
            AcademicSubjectService subjects) {
        this.semester3FolderId = semester3FolderId == null ? "" : semester3FolderId.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.auth = auth;
        this.subjects = subjects;
    }

    public boolean isConfigured() {
        return !semester3FolderId.isBlank() && (hasApiKey() || auth.hasCredentials());
    }

    public byte[] downloadFile(String fileId) {
        try {
            String url = "https://www.googleapis.com/drive/v3/files/" + enc(fileId)
                    + "?alt=media&supportsAllDrives=true";
            if (hasServiceToken()) {
                return authorizedGetBytes(URI.create(url));
            }
            if (!hasApiKey()) throw new IllegalStateException("Google Drive read access is not configured.");
            return restTemplate.getForObject(URI.create(url + "&key=" + enc(apiKey)), byte[].class);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to download Google Drive file " + fileId + ": " + e.getMessage(), e);
        }
    }

    public List<DriveFile> getSemester3Files() {
        long now = System.currentTimeMillis();
        List<DriveFile> cached = cachedDriveFiles;
        if (cachedDriveFilesAt > 0L && now - cachedDriveFilesAt < DRIVE_LIST_CACHE_MILLIS) return new ArrayList<>(cached);
        synchronized (this) {
            now = System.currentTimeMillis();
            cached = cachedDriveFiles;
            if (cachedDriveFilesAt > 0L && now - cachedDriveFilesAt < DRIVE_LIST_CACHE_MILLIS) return new ArrayList<>(cached);
            List<DriveFile> refreshed = loadSemester3Files();
            cachedDriveFiles = List.copyOf(refreshed);
            cachedDriveFilesAt = System.currentTimeMillis();
            return new ArrayList<>(cachedDriveFiles);
        }
    }

    private List<DriveFile> loadSemester3Files() {
        if (semester3FolderId.isBlank()) throw new IllegalStateException("NEXORA_DRIVE_FOLDER_ID is not configured.");
        if (!hasApiKey() && !hasServiceToken()) throw new IllegalStateException("Google Drive read access is not configured.");
        List<DriveFile> all = new ArrayList<>();
        String rootSubjectCode = null;
        String rootSubjectName = null;
        String rootResourceType = null;
        try {
            String rootUrl = "https://www.googleapis.com/drive/v3/files/" + enc(semester3FolderId)
                    + "?fields=id,name,mimeType&supportsAllDrives=true";
            String rootJson = authorizedOrApiKeyGet(rootUrl);
            JsonNode rootNode = objectMapper.readTree(rootJson);
            String rootName = rootNode.path("name").asText("");
            rootSubjectCode = getSubjectCode(rootName);
            rootSubjectName = getSubjectName(rootName);
            rootResourceType = getResourceType(rootName);
        } catch (Exception ignored) {
            // The configured ID can still be a semester container; recursive discovery below
            // will infer subject/type from its child folder names and filenames.
        }
        collectFilesRecursively(semester3FolderId, all, rootSubjectCode, rootSubjectName, rootResourceType);
        // A shared-drive tree can expose the same source more than once. Website entries
        // are keyed by the real Drive file ID, never by the containing folder.
        Map<String,DriveFile> unique = new LinkedHashMap<>();
        for (DriveFile f : all) unique.putIfAbsent(f.getId(), f);
        return new ArrayList<>(unique.values());
    }

    private void collectFilesRecursively(String folderId, List<DriveFile> all,
                                         String subjectCode, String subjectName, String currentResourceType) {
        String pageToken = null;
        do {
            StringBuilder url = new StringBuilder("https://www.googleapis.com/drive/v3/files?q=")
                    .append(enc("'" + folderId + "' in parents and trashed=false"))
                    .append("&spaces=drive")
                    .append("&supportsAllDrives=true")
                    .append("&includeItemsFromAllDrives=true")
                    .append("&fields=nextPageToken,files(id,name,mimeType,webViewLink,parents,size,modifiedTime,description)")
                    .append("&pageSize=1000");
            if (pageToken != null) url.append("&pageToken=").append(enc(pageToken));
            String response = authorizedOrApiKeyGet(url.toString());
            try {
                JsonNode root = objectMapper.readTree(response);
                JsonNode nodes = root.path("files");
                if (nodes.isArray()) {
                    for (JsonNode n : nodes) {
                        String id = n.path("id").asText();
                        String name = n.path("name").asText();
                        String mime = n.path("mimeType").asText();
                        String detectedCode = getSubjectCode(name);
                        String detectedName = getSubjectName(name);
                        String nextCode = detectedCode != null ? detectedCode : subjectCode;
                        String nextName = detectedName != null ? detectedName : subjectName;
                        String detectedType = getResourceType(name);
                        String nextType = detectedType != null ? detectedType : currentResourceType;
                        // Files directly under a recognized subject folder (or its Unit folders)
                        // are study materials by default unless their name/folder identifies a PYQ.
                        // This captures neutral names such as "Unit - 3.pdf" and every PPT/PPTX.
                        if (!"application/vnd.google-apps.folder".equals(mime) && nextType == null
                                && nextCode != null && isStudyFile(name) && !looksLikeSemesterPaper(name)) {
                            nextType = "STUDY_MATERIALS";
                        }

                        DriveFile file = new DriveFile();
                        file.id = id; file.name = name; file.mimeType = mime;
                        file.webViewLink = n.path("webViewLink").asText("");
                        file.parentId = folderId; file.subjectCode = nextCode; file.subjectName = nextName;
                        file.resourceType = nextType; file.size = n.path("size").asLong(0);
                        file.modifiedTime = n.path("modifiedTime").asText("");
                        file.description = n.path("description").asText("");
                        // Folders are traversal nodes only. Never return them to the academic
                        // resource index/UI: recursively discover their supported files instead.
                        if ("application/vnd.google-apps.folder".equals(mime)) {
                            collectFilesRecursively(id, all, nextCode, nextName, nextType);
                        } else if (isStudyFile(name) || looksLikeSemesterPaper(name)) {
                            // Return only actual supported document files. Parent folder names
                            // (Unit 1, Unit 2, PYQs, Study Materials, etc.) are never materials.
                            all.add(file);
                        }
                    }
                }
                pageToken = root.path("nextPageToken").asText("");
            } catch (Exception e) {
                throw new IllegalStateException("Unable to parse Google Drive response.", e);
            }
        } while (pageToken != null && !pageToken.isBlank());
    }

    /**
     * Execute a Drive GET using a URI object rather than RestTemplate's String URL overload.
     *
     * The query string is already percent-encoded by enc(). Passing that encoded URL
     * through RestTemplate as a String can cause the percent signs to be encoded again
     * (for example %27 -> %2527), which makes Google's Drive "q" parameter invalid.
     * URI.create() preserves the already-encoded query.
     */
    private String authorizedOrApiKeyGet(String urlWithoutAuth) {
        if (hasServiceToken()) return authorizedGet(URI.create(urlWithoutAuth));
        URI requestUri = URI.create(urlWithoutAuth + "&key=" + enc(apiKey));
        return restTemplate.getForObject(requestUri, String.class);
    }

    private String authorizedGet(String url) {
        return authorizedGet(URI.create(url));
    }

    private String authorizedGet(URI uri) {
        org.springframework.http.HttpHeaders h = new org.springframework.http.HttpHeaders();
        h.setBearerAuth(auth.accessTokenOrNull());
        org.springframework.http.ResponseEntity<String> response = restTemplate.exchange(uri, org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(h), String.class);
        if (!response.getStatusCode().is2xxSuccessful()) throw new IllegalStateException("Drive API HTTP " + response.getStatusCode());
        return response.getBody() == null ? "" : response.getBody();
    }

    private byte[] authorizedGetBytes(String url) {
        return authorizedGetBytes(URI.create(url));
    }

    private byte[] authorizedGetBytes(URI uri) {
        org.springframework.http.HttpHeaders h = new org.springframework.http.HttpHeaders();
        h.setBearerAuth(auth.accessTokenOrNull());
        org.springframework.http.ResponseEntity<byte[]> response = restTemplate.exchange(uri, org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(h), byte[].class);
        if (!response.getStatusCode().is2xxSuccessful()) throw new IllegalStateException("Drive API HTTP " + response.getStatusCode());
        return response.getBody() == null ? new byte[0] : response.getBody();
    }

    private boolean hasApiKey() { return !apiKey.isBlank(); }
    private boolean hasServiceToken() { return auth.accessTokenOrNull() != null; }

    /** Encode a query/path value without form-style '+' spaces. */
    private String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private boolean isStudyFile(String name) {
        if (name == null) return false;
        String n = name.toLowerCase(java.util.Locale.ROOT);
        // Keep this allow-list aligned with AcademicDriveSyncService and the subject page.
        // Ordinary Word notes were previously invisible because only PDF/PPT were accepted.
        return n.endsWith(".pdf") || n.endsWith(".ppt") || n.endsWith(".pptx")
                || n.endsWith(".doc") || n.endsWith(".docx");
    }

    private boolean looksLikeSemesterPaper(String name) {
        if (name == null) return false;
        String n = name.toLowerCase(java.util.Locale.ROOT).replace('_', ' ').replace('-', ' ');
        if (n.matches(".*\\b(pyq|previous year|question paper|semester end|end semester|end sem|semester final|exam paper|question papers)\\b.*")) return true;
        // A month + year filename such as "May 2025.pdf" is likely a semester paper;
        // named Unit files remain study materials even when their filename contains a year.
        boolean hasMonth = n.matches(".*\\b(jan|january|feb|february|mar|march|apr|april|may|jun|june|jul|july|aug|august|sep|sept|september|oct|october|nov|november|dec|december)\\b.*");
        boolean hasYear = n.matches(".*\\b(19|20)\\d{2}\\b.*");
        return hasMonth && hasYear && !n.matches(".*\\bunit\\s*\\d+\\b.*");
    }

    private String getSubjectCode(String name) {
        if (name == null) return null;
        String n = name.toUpperCase();
        if (n.startsWith("21DCS201P")) return "21DCS201P";
        if (n.startsWith("21CSC203P")) return "21CSC203P";
        if (n.startsWith("21CSC202J")) return "21CSC202J";
        if (n.startsWith("21CSC201J")) return "21CSC201J";
        if (n.startsWith("21CSS201T")) return "21CSS201T";
        if (n.startsWith("21MAB201T")) return "21MAB201T";
        if (n.contains("DESIGN THINKING") || n.contains("DESIGN METHODOLOGY")) return "21DCS201P";
        if (n.contains("DATA STRUCTURES") || n.contains("ALGORITHMS")) return "21CSC201J";
        if (n.contains("OPERATING SYSTEM")) return "21CSC202J";
        if (n.contains("ADVANCED PROGRAMMING") || n.contains("PROGRAMMING PRACTICE")) return "21CSC203P";
        if (n.contains("COMPUTER ORGANIZATION") || n.contains("ARCHITECTURE")) return "21CSS201T";
        if (n.contains("TRANSFORMS") || n.contains("BOUNDARY VALUE")) return "21MAB201T";
        // Resolve every subject registered in the academic subject table as well. This
        // avoids limiting Drive sync to the original hard-coded sample curriculum.
        try {
            for (AcademicSubject subject : subjects.getAllSubjects()) {
                if (subject == null || subject.getCode() == null || subject.getName() == null) continue;
                String code = subject.getCode().trim();
                if (n.contains(code.toUpperCase(java.util.Locale.ROOT))) return code;
                String candidate = normalizeDriveLabel(name);
                String subjectLabel = normalizeDriveLabel(subject.getName());
                if (subjectLabel.length() >= 6 && (candidate.equals(subjectLabel)
                        || candidate.startsWith(subjectLabel + " ")
                        || candidate.contains(" " + subjectLabel + " "))) return code;
            }
        } catch (RuntimeException ignored) {
            // Keep explicit mappings available if the subject table is temporarily unavailable.
        }
        return null;
    }

    private String normalizeDriveLabel(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }

    private String getSubjectName(String name) {
        String code = getSubjectCode(name);
        if (code == null) return null;
        try {
            AcademicSubject subject = subjects.getSubjectByCode(code);
            if (subject != null && subject.getName() != null && !subject.getName().isBlank()) return subject.getName();
        } catch (RuntimeException ignored) { }
        return switch (code) {
            case "21DCS201P" -> "Design Thinking Methodology";
            case "21CSC203P" -> "Advanced Programming Practice";
            case "21CSC202J" -> "Operating System";
            case "21CSC201J" -> "Data Structures and Algorithms";
            case "21CSS201T" -> "Computer Organization and Architecture";
            case "21MAB201T" -> "Transforms and Boundary Value Problems";
            default -> null;
        };
    }

    private String getResourceType(String name) {
        if (name == null) return null;
        String n = name.toLowerCase(java.util.Locale.ROOT).replace('_', ' ').replace('-', ' ');
        String compact = n.replaceAll("\\s+", " ").trim();
        if (compact.matches(".*\\b(important topics|important portion|important questions|teacher important|staff important)\\b.*")) return "IMPORTANT_TOPICS";
        if (compact.matches(".*\\b(study materials?|learning materials?|faculty notes?|lecture notes?|course materials?)\\b.*")) return "STUDY_MATERIALS";
        if (compact.matches(".*\\b(semester pyq|semester end|semester final|end sem|end semester|semester question paper|semester question|previous year questions?|pyqs?|question papers?)\\b.*")) return "SEMESTER_PYQS";
        return null;
    }

    public static class DriveFile {
        private String id, name, mimeType, webViewLink, parentId, subjectCode, subjectName, resourceType, modifiedTime, description;
        private long size;
        public String getId() { return id; }
        public String getName() { return name; }
        public String getMimeType() { return mimeType; }
        public String getWebViewLink() { return webViewLink; }
        public String getParentId() { return parentId; }
        public String getSubjectCode() { return subjectCode; }
        public String getSubjectName() { return subjectName; }
        public String getResourceType() { return resourceType; }
        public long getSize() { return size; }
        public String getModifiedTime() { return modifiedTime; }
        public String getDescription() { return description; }
    }
}
