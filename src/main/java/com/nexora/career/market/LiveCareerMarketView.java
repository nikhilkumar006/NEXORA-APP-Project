package com.nexora.career.market;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Projects market-feed records into simple Thymeleaf-friendly maps. Keeping view data
 * explicit avoids template coupling to Java record/property accessor implementation details
 * and gives every nested collection a stable, null-safe shape.
 */
public final class LiveCareerMarketView {
    private LiveCareerMarketView() {}

    public static Map<String, Object> from(LiveCareerMarketService.Snapshot snapshot) {
        if (snapshot == null) return null;
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("registeredJobseekers", text(snapshot.registeredJobseekers()));
        view.put("activeEmployers", text(snapshot.activeEmployers()));
        view.put("applications", text(snapshot.applications()));
        view.put("totalListings", text(snapshot.totalListings()));
        view.put("ncsUpdated", text(snapshot.ncsUpdated()));
        view.put("employmentOutlook", text(snapshot.employmentOutlook()));
        view.put("workforceExpansion", text(snapshot.workforceExpansion()));
        view.put("roleDemand", text(snapshot.roleDemand()));
        view.put("skillsSignal", text(snapshot.skillsSignal()));
        view.put("locationSignal", text(snapshot.locationSignal()));
        view.put("industrySignal", text(snapshot.industrySignal()));
        view.put("aiSignal", text(snapshot.aiSignal()));
        view.put("salarySignal", text(snapshot.salarySignal()));
        view.put("functionalSalarySignal", text(snapshot.functionalSalarySignal()));
        view.put("labourParticipation", text(snapshot.labourParticipation()));
        view.put("unemploymentRate", text(snapshot.unemploymentRate()));
        view.put("itSectorVolume", text(snapshot.itSectorVolume()));
        view.put("itFunctionalTop", text(snapshot.itFunctionalTop()));
        view.put("itSectorSource", text(snapshot.itSectorSource()));
        view.put("ncsUrl", url(snapshot.ncsUrl()));
        view.put("outlookUrl", url(snapshot.outlookUrl()));
        view.put("salaryUrl", url(snapshot.salaryUrl()));
        view.put("plfsUrl", url(snapshot.plfsUrl()));
        view.put("itSectorUrl", url(snapshot.itSectorUrl()));
        view.put("lastFetchedAt", text(snapshot.lastFetchedAt()));

        List<Map<String, Object>> jobs = new ArrayList<>();
        if (snapshot.latestJobs() != null) {
            for (LiveCareerMarketService.JobItem job : snapshot.latestJobs()) {
                if (job == null) continue;
                String organization = text(job.organization());
                String position = text(job.position());
                String sourceUrl = url(job.sourceUrl());
                // A row is a recommendation only when the role and organization are identifiable
                // and a direct HTTP(S) source link is available. Do not render feed fragments.
                if (organization.isBlank() || position.isBlank() || sourceUrl.isBlank()) continue;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("organization", organization);
                item.put("position", position);
                item.put("postingDate", text(job.postingDate()));
                item.put("vacancy", text(job.vacancy()));
                item.put("type", text(job.type()));
                item.put("sourceUrl", sourceUrl);
                jobs.add(item);
            }
        }
        view.put("latestJobs", List.copyOf(jobs));

        List<Map<String, Object>> sectors = new ArrayList<>();
        if (snapshot.sectorSignals() != null) {
            for (LiveCareerMarketService.SectorSignal signal : snapshot.sectorSignals()) {
                if (signal == null) continue;
                String name = text(signal.name());
                String value = text(signal.value());
                if (name.isBlank() || value.isBlank()) continue;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", name);
                item.put("value", value);
                item.put("note", text(signal.note()));
                item.put("source", text(signal.source()));
                item.put("publishedDate", text(signal.publishedDate()));
                sectors.add(item);
            }
        }
        view.put("sectorSignals", List.copyOf(sectors));
        return view;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static String url(String value) {
        if (value == null || value.isBlank()) return "";
        String trimmed = value.trim();
        return trimmed.startsWith("https://") || trimmed.startsWith("http://") ? trimmed : "";
    }
}
