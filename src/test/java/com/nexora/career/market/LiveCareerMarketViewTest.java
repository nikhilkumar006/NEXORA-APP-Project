package com.nexora.career.market;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LiveCareerMarketViewTest {
    @Test
    void projectsSnapshotAndNestedRecordsToNullSafeTemplateMaps() {
        LiveCareerMarketService.Snapshot snapshot = new LiveCareerMarketService.Snapshot(
                "100", "20", "15", "40", "2026-10-12",
                List.of(new LiveCareerMarketService.JobItem("Acme", "Software Engineer", "2026-10-10", "2", "Full-time", "https://example.com/job")),
                "Outlook", "Expansion", "Role demand", "Skills", "Location", "Industry", "AI",
                "Salary", "Functional salary", "LFPR", "Unemployment", "1000", "900", "NCS · 2026",
                List.of(new LiveCareerMarketService.SectorSignal("IT", "900", "note", "NCS", "2026-02-28")),
                "https://ncs.gov.in/", "https://example.com/outlook", "https://example.com/salary",
                "https://example.com/plfs", "https://example.com/it.pdf", "2026-10-12T08:30:00Z");

        Map<String, Object> view = LiveCareerMarketView.from(snapshot);
        assertEquals("100", view.get("registeredJobseekers"));
        assertEquals("https://example.com/outlook", view.get("outlookUrl"));
        List<?> jobs = (List<?>) view.get("latestJobs");
        assertEquals("Software Engineer", ((Map<?, ?>) jobs.get(0)).get("position"));
        assertEquals("https://example.com/job", ((Map<?, ?>) jobs.get(0)).get("sourceUrl"));
        List<?> sectors = (List<?>) view.get("sectorSignals");
        assertEquals("IT", ((Map<?, ?>) sectors.get(0)).get("name"));
    }

    @Test
    void doesNotExposeInvalidOrMissingSourceUrlsAsLinks() {
        LiveCareerMarketService.Snapshot snapshot = new LiveCareerMarketService.Snapshot(
                null, null, null, null, null,
                List.of(new LiveCareerMarketService.JobItem(null, null, null, null, null, "javascript:alert(1)")),
                null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                List.of(), "not-a-url", null, null, null, null, null);
        Map<String, Object> view = LiveCareerMarketView.from(snapshot);
        assertEquals("", view.get("registeredJobseekers"));
        assertTrue(((List<?>) view.get("latestJobs")).isEmpty(), "job rows without an identifiable role, organization and direct source URL must be omitted");
        assertEquals("", view.get("ncsUrl"));
        List<?> jobs = (List<?>) view.get("latestJobs");
        assertTrue(jobs.isEmpty(), "a job row without a valid direct source URL must not be rendered as a verifiable listing");
    }

    @Test
    void nullSnapshotReturnsNullForExplicitEmptyState() {
        assertNull(LiveCareerMarketView.from(null));
    }
}
