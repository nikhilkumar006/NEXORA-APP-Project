package com.nexora.career;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Reads the source-backed on-campus readiness benchmarks supplied in the SRMIST planner workbooks. */
@Service
public class PlacementPlannerService {
    private final JsonNode root;

    public PlacementPlannerService(ObjectMapper mapper) {
        JsonNode parsed = mapper.createObjectNode();
        try (InputStream in = new ClassPathResource("data/placement-planner.json").getInputStream()) {
            parsed = mapper.readTree(in);
        } catch (Exception ignored) {
            // An absent catalogue should render an honest empty state, not break Development GPS.
        }
        this.root = parsed;
    }

    /**
     * Selects only the planner cohort supported by the uploaded HOD workbooks.
     * Prefer the student's recorded year of passing; use a four-year programme
     * fallback only when the stored value is missing or outside the workbook years.
     */
    public int resolveYearOfPassing(int batchEndYear, int batchStartYear) {
        if (supportsYear(batchEndYear)) return batchEndYear;
        int inferred = batchStartYear > 2000 && batchStartYear < 2100 ? batchStartYear + 4 : 0;
        return supportsYear(inferred) ? inferred : batchEndYear;
    }

    public boolean supportsYear(int yearOfPassing) {
        return yearOfPassing == 2028 || yearOfPassing == 2029 || yearOfPassing == 2030;
    }

    public List<Map<String, String>> criteria(int yearOfPassing, String targetBand) {
        String year = Integer.toString(yearOfPassing);
        String band = normalizeBand(targetBand);
        JsonNode rows = root.path("years").path(year).path(band);
        if (!rows.isArray()) return List.of();
        List<Map<String, String>> result = new ArrayList<>();
        for (JsonNode row : rows) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("metric", row.path("metric").asText(""));
            item.put("target", row.path("target").asText(""));
            item.put("source", row.path("source").asText("Uploaded SRMIST placement planner"));
            if (!item.get("metric").isBlank() && !item.get("target").isBlank()) result.add(item);
        }
        return List.copyOf(result);
    }

    public String note() {
        return root.path("note").asText("");
    }

    private String normalizeBand(String value) {
        String v = value == null ? "dream" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        return switch (v) {
            case "marquee" -> "Marquee";
            case "superdream" -> "Super Dream";
            case "standard" -> "Standard";
            default -> "Dream";
        };
    }
}
