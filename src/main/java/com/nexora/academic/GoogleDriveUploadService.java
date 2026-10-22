package com.nexora.academic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class GoogleDriveUploadService {
    private final DriveAuthService auth;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String configuredRootFolder;

    public GoogleDriveUploadService(
            DriveAuthService auth,
            @Value("${nexora.drive.upload-folder-id:${NEXORA_DRIVE_FOLDER_ID:}}") String configuredRootFolder) {
        this.auth = auth;
        this.configuredRootFolder = configuredRootFolder == null ? "" : configuredRootFolder.trim();
    }

    public boolean isConfigured() {
        return !configuredRootFolder.isBlank() && auth.accessTokenOrNull() != null;
    }

    public String upload(byte[] bytes, String fileName, String mimeType,
                         String subjectCode, String resourceType,
                         Integer year, String month, String uploadedBy) {
        String token = auth.accessTokenOrNull();
        if (token == null || configuredRootFolder.isBlank()) return null;
        try {
            String subjectFolder = findOrCreateFolder(token, configuredRootFolder, subjectCode);
            String typeFolder = findOrCreateFolder(token, subjectFolder, resourceType);
            if (findFileByName(token, typeFolder, fileName) != null) {
                throw new IllegalStateException("A Drive paper with the generated filename already exists: " + fileName);
            }
            String description = "NEXORA academic archive | subject=" + subjectCode
                    + " | resourceType=" + resourceType
                    + (year == null ? "" : " | year=" + year)
                    + (month == null || month.isBlank() ? "" : " | month=" + month)
                    + " | uploadedBy=" + (uploadedBy == null ? "STUDENT" : uploadedBy);
            return uploadBinary(token, typeFolder, fileName, mimeType, description, bytes);
        } catch (Exception e) {
            if (e instanceof IllegalStateException) throw (IllegalStateException)e;
            throw new IllegalStateException("Google Drive upload failed: " + safe(e.getMessage()), e);
        }
    }

    private String findOrCreateFolder(String token, String parentId, String name) throws Exception {
        String escaped = name.replace("'", "\\'");
        String q = "name='" + escaped + "' and mimeType='application/vnd.google-apps.folder' and '" + parentId + "' in parents and trashed=false";
        URL listUrl = new URL("https://www.googleapis.com/drive/v3/files?q=" + java.net.URLEncoder.encode(q, StandardCharsets.UTF_8)
                + "&pageSize=1&fields=files(id,name)");
        HttpURLConnection c = connection(listUrl, "GET", token);
        String body = read(c);
        JsonNode files = mapper.readTree(body).path("files");
        if (files.isArray() && files.size() > 0) return files.get(0).path("id").asText();

        URL createUrl = new URL("https://www.googleapis.com/drive/v3/files");
        HttpURLConnection create = connection(createUrl, "POST", token);
        create.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        create.setDoOutput(true);
        String metadata = mapper.writeValueAsString(java.util.Map.of(
                "name", name,
                "mimeType", "application/vnd.google-apps.folder",
                "parents", List.of(parentId)
        ));
        try (OutputStream out = create.getOutputStream()) { out.write(metadata.getBytes(StandardCharsets.UTF_8)); }
        return mapper.readTree(read(create)).path("id").asText();
    }

    private String findFileByName(String token, String parentId, String fileName) throws Exception {
        String escaped = fileName.replace("'", "\\'");
        String q = "name='" + escaped + "' and '" + parentId + "' in parents and trashed=false";
        URL listUrl = new URL("https://www.googleapis.com/drive/v3/files?q=" + java.net.URLEncoder.encode(q, StandardCharsets.UTF_8)
                + "&pageSize=1&fields=files(id,name)");
        HttpURLConnection c = connection(listUrl, "GET", token);
        JsonNode files = mapper.readTree(read(c)).path("files");
        return files.isArray() && files.size() > 0 ? files.get(0).path("id").asText() : null;
    }

    private String uploadBinary(String token, String parentId, String fileName, String mimeType,
                                String description, byte[] bytes) throws Exception {
        String boundary = "NEXORA_" + System.nanoTime();
        URL url = new URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,webViewLink");
        HttpURLConnection c = connection(url, "POST", token);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "multipart/related; boundary=" + boundary);

        String metadata = mapper.writeValueAsString(java.util.Map.of(
                "name", fileName,
                "description", description,
                "mimeType", mimeType == null || mimeType.isBlank() ? "application/octet-stream" : mimeType,
                "parents", List.of(parentId)
        ));
        try (OutputStream out = c.getOutputStream()) {
            out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.write("Content-Type: application/json; charset=UTF-8\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            out.write(metadata.getBytes(StandardCharsets.UTF_8));
            out.write(("\r\n--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Type: " + (mimeType == null || mimeType.isBlank() ? "application/octet-stream" : mimeType) + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(bytes);
            out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        }
        return mapper.readTree(read(c)).path("id").asText();
    }

    private String safe(String message) {
        if (message == null || message.isBlank()) return "unknown Drive error";
        return message.replaceAll("[\\r\\n]+", " ").replaceAll("[\\t]+", " ");
    }

    private HttpURLConnection connection(URL url, String method, String token) throws Exception {
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("Authorization", "Bearer " + token);
        c.setRequestProperty("Accept", "application/json");
        return c;
    }

    private String read(HttpURLConnection c) throws Exception {
        int code = c.getResponseCode();
        java.io.InputStream stream = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        String body = stream == null ? "" : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        if (code < 200 || code >= 300) throw new IllegalStateException("Google Drive API returned HTTP " + code + ": " + body);
        return body;
    }
}
