package com.nexora.career.market;

import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LiveCareerMarketService {
    private static final URI NCS_HOME = URI.create("https://ncs.gov.in/");
    private static final URI NCS_LATEST = URI.create("https://ncs.gov.in/latest-update");
    private static final URI TEAMLEASE_OUTLOOK = URI.create("https://group.teamlease.com/insights/employment-outlook-report-hy2-fy27/");
    private static final URI TEAMLEASE_SALARY = URI.create("https://group.teamlease.com/insights/jobs-and-salaries-primer-2026/");
    private static final URI MOSPI_PLFS = URI.create("https://www.mospi.gov.in/themes/product/69--");
    private static final URI NCS_IT_SECTOR = URI.create("https://www.ncs.gov.in/Documents/NCS%20E-Newsletter_February%202026.pdf");

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private volatile Snapshot cached;
    private volatile Instant fetchedAt = Instant.EPOCH;
    private volatile CompletableFuture<Void> backgroundRefresh;

    /** Fast path for pages where market data is useful but must never block page rendering. */
    public Snapshot getQuick() {
        Snapshot current = cached;
        if (current == null || fetchedAt.plus(Duration.ofMinutes(15)).isBefore(Instant.now())) {
            synchronized (this) {
                if (backgroundRefresh == null || backgroundRefresh.isDone()) {
                    backgroundRefresh = CompletableFuture.runAsync(() -> {
                        try { get(); } catch (RuntimeException ignored) { /* keep last good snapshot */ }
                    });
                }
            }
        }
        return current;
    }

    public synchronized Snapshot get() {
        if (cached == null || fetchedAt.plus(Duration.ofMinutes(15)).isBefore(Instant.now())) {
            cached = fetch();
            fetchedAt = Instant.now();
        }
        return cached;
    }

    private Snapshot fetch() {
        // Fetch independent sources concurrently. A slow public site must not make a student wait
        // through five sequential network timeouts before the page can render.
        CompletableFuture<String> ncsFuture = getText(NCS_HOME);
        CompletableFuture<String> latestFuture = getText(NCS_LATEST);
        CompletableFuture<String> outlookFuture = getText(TEAMLEASE_OUTLOOK);
        CompletableFuture<String> salaryFuture = getText(TEAMLEASE_SALARY);
        CompletableFuture<String> plfsFuture = getText(MOSPI_PLFS);
        CompletableFuture.allOf(ncsFuture, latestFuture, outlookFuture, salaryFuture, plfsFuture).join();
        String ncsRaw = ncsFuture.join();
        String latestRaw = latestFuture.join();
        String outlook = strip(outlookFuture.join());
        String salary = strip(salaryFuture.join());
        String plfs = strip(plfsFuture.join());

        List<JobItem> jobs = parseNcsLatest(latestRaw);
        String ncsUpdated = extract(ncsRaw, "Last updated:\\s*([^<]{5,20})", "");

        return new Snapshot(
                metric(ncsRaw, "Registered Jobseekers", "Active Employers"),
                metric(ncsRaw, "Active Employers", "Job Applications Submitted"),
                metric(ncsRaw, "Job Applications Submitted", "Total Job Listings"),
                metric(ncsRaw, "Total Job Listings", "Search Jobs On-the-Go"),
                ncsUpdated,
                jobs,
                firstNonEmpty(parseBullet(outlook, "Net employment change projected to rise to"), ""),
                firstNonEmpty(parseBullet(outlook, "60% of employers plan to grow their workforce"), ""),
                firstNonEmpty(parseBullet(outlook, "Sales & Marketing and IT roles see the strongest demand"), ""),
                firstNonEmpty(parseBullet(outlook, "Digital Literacy tops the skills wishlist at 59%"), ""),
                firstNonEmpty(parseBullet(outlook, "Bengaluru, Hyderabad and Pune top expansion targets"), ""),
                firstNonEmpty(parseBullet(outlook, "E-commerce and Travel & Hospitality lead industry growth"), ""),
                firstNonEmpty(parseBullet(outlook, "85% of employers use AI, but only 13% at scale"), ""),
                firstNonEmpty(parseBullet(salary, "India’s projected salary increments range from"), ""),
                firstNonEmpty(parseBullet(salary, "Technical areas lead the functional growth"), ""),
                firstNonEmpty(parsePlfs(plfs, "Labour Force Participation Rate"), ""),
                firstNonEmpty(parsePlfs(plfs, "Unemployment Rate"), ""),
                "5.78 lakh mobilised / 10,707 active (published 28 Feb 2026)",
                "4.46 lakh IT functional-area vacancies mobilised in FY 2025-26 snapshot",
                "NCS IT & Communication sector snapshot · 28 Feb 2026",
                List.of(
                        new SectorSignal("Information Technology", "4,46,024", "Highest functional-area volume in the published IT & Communication snapshot", "NCS", "2026-02-28"),
                        new SectorSignal("Others", "57,821", "Second-highest functional-area volume in that snapshot", "NCS", "2026-02-28"),
                        new SectorSignal("Customer Care Service", "27,349", "Published functional-area volume", "NCS", "2026-02-28"),
                        new SectorSignal("Administration / Back Office", "24,494", "Published functional-area volume", "NCS", "2026-02-28"),
                        new SectorSignal("Marketing & Sales", "9,674", "Published functional-area volume", "NCS", "2026-02-28")
                ),
                "https://ncs.gov.in/", "https://group.teamlease.com/insights/employment-outlook-report-hy2-fy27/", "https://group.teamlease.com/insights/jobs-and-salaries-primer-2026/", "https://www.mospi.gov.in/themes/product/69--", NCS_IT_SECTOR.toString(), Instant.now().toString()
        );
    }

    private CompletableFuture<String> getText(URI uri) {
        try {
            HttpRequest req = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5))
                    .header("User-Agent", "NEXORA-Career-Intelligence/1.0").GET().build();
            return client.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                    .thenApply(res -> res.statusCode() >= 200 && res.statusCode() < 300 ? res.body() : "")
                    .completeOnTimeout("", 6, TimeUnit.SECONDS)
                    .exceptionally(ignored -> "");
        } catch (Exception ignored) {
            return CompletableFuture.completedFuture("");
        }
    }

    private String strip(String html) {
        return html.replaceAll("(?is)<script.*?</script>", " ")
                .replaceAll("(?is)<style.*?</style>", " ")
                .replaceAll("(?is)<[^>]+>", " ")
                .replace("&nbsp;", " ").replace("&amp;", "&").replace("&#39;", "'").replace("&quot;", "\"")
                .replaceAll("\\s+", " ").trim();
    }

    private String metric(String text, String key, String nextKey) {
        if (text == null || text.isBlank()) return "";
        String plain = strip(text);
        Pattern p = Pattern.compile(Pattern.quote(key) + "\\s*([0-9][0-9.,+ ]*(?:Million|Milli|K|Lakh|Crore)?\\+?)\\s*" + Pattern.quote(nextKey), Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(plain);
        if (m.find()) return cleanMetric(m.group(1));
        int start = plain.indexOf(key);
        if (start >= 0) {
            String tail = plain.substring(start + key.length(), Math.min(plain.length(), start + key.length() + 80));
            Matcher n = Pattern.compile("^\\s*([0-9][0-9.,+ ]*(?:Million|Milli|K|Lakh|Crore)?\\+?)").matcher(tail);
            if (n.find()) return cleanMetric(n.group(1));
        }
        return "";
    }

    private String cleanMetric(String s) { return s.replaceAll("\\s+", " ").trim(); }

    private String extract(String text, String regex, String fallback) {
        Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(text == null ? "" : text);
        return m.find() ? strip(m.group(1)).trim() : fallback;
    }

    private String parseBullet(String text, String startsWith) {
        if (text == null || text.isBlank()) return "";
        int i = text.indexOf(startsWith);
        if (i < 0) return "";
        int end = text.indexOf('.', i);
        if (end < 0) end = Math.min(text.length(), i + 220);
        return text.substring(i, Math.min(end + 1, text.length())).trim();
    }

    private String parsePlfs(String text, String key) {
        if (text == null || text.isBlank()) return "";
        int i = text.indexOf(key);
        if (i < 0) return "";
        String tail = text.substring(Math.max(0, i - 100), Math.min(text.length(), i + 500));
        Matcher m = Pattern.compile("(\\d+(?:\\.\\d+)?%)").matcher(tail);
        return m.find() ? key + ": " + m.group(1) : "";
    }

    private String firstNonEmpty(String a, String b) { return a == null || a.isBlank() ? b : a; }

    List<JobItem> parseNcsLatest(String html) {
        List<JobItem> result = new ArrayList<>();
        if (html == null || html.isBlank()) return result;
        Matcher row = Pattern.compile("(?is)<tr[^>]*>(.*?)</tr>").matcher(html);
        while (row.find() && result.size() < 12) {
            List<String> cells = new ArrayList<>();
            List<String> rawCells = new ArrayList<>();
            Matcher cell = Pattern.compile("(?is)<td[^>]*>(.*?)</td>").matcher(row.group(1));
            while (cell.find()) {
                rawCells.add(cell.group(1));
                cells.add(strip(cell.group(1)));
            }
            // NCS publishes a separate Advertisement Link column. Never label the generic
            // latest-updates landing page as evidence for an individual opportunity.
            if (cells.size() >= 7 && cells.get(0).matches("\\d+")) {
                String directSource = extractAdvertisementUrl(rawCells.get(rawCells.size() - 1));
                if (directSource == null || !isClearlyCseRelevant(cells.get(2), cells.get(5))) continue;
                result.add(new JobItem(cells.get(1), cells.get(2), cells.get(3), cells.get(4), cells.get(5), directSource));
            }
        }
        return result;
    }

    private String extractAdvertisementUrl(String cellHtml) {
        if (cellHtml == null || cellHtml.isBlank()) return null;
        Matcher link = Pattern.compile("(?is)<a\\b[^>]*href\\s*=\\s*(['\"])(.*?)\\1[^>]*>(.*?)</a>").matcher(cellHtml);
        while (link.find()) {
            String candidate = link.group(2).replace("&amp;", "&").replace("&#39;", "'").replace("&quot;", "\"").trim();
            try {
                URI uri = URI.create(candidate);
                if (!uri.isAbsolute()) uri = NCS_LATEST.resolve(uri);
                String scheme = uri.getScheme();
                if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) continue;
                if (uri.getHost() == null || uri.getUserInfo() != null) continue;
                String host = uri.getHost().toLowerCase(java.util.Locale.ROOT);
                if (host.equals("localhost") || host.endsWith(".localhost") || host.equals("127.0.0.1")) continue;
                return uri.normalize().toASCIIString();
            } catch (IllegalArgumentException ignored) {
                // Malformed, blob, data and javascript URLs are not evidence links.
            }
        }
        return null;
    }

    private boolean isClearlyCseRelevant(String position, String type) {
        String text = ((position == null ? "" : position) + " " + (type == null ? "" : type))
                .toLowerCase(java.util.Locale.ROOT);
        // The NCS feed is general-purpose. Avoid presenting a generic internship, JRF, driver,
        // legal or administrative role as a CSE opportunity when its title does not establish
        // a computing/technology discipline. The direct source remains available on NCS itself.
        return text.matches("(?s).*\\b(software|computer science|information technology|\\bit\\b|data engineer|data analyst|data scientist|machine learning|artificial intelligence|\\bai\\b|cyber.?security|security engineer|developer|programmer|web developer|cloud engineer|devops|site reliability|database|network engineer|qa automation|sdet|application developer|systems engineer|ict|digital technology|technology intern(ship)?)\\b.*");
    }

    public record Snapshot(String registeredJobseekers, String activeEmployers, String applications,
                           String totalListings, String ncsUpdated, List<JobItem> latestJobs,
                           String employmentOutlook, String workforceExpansion, String roleDemand,
                           String skillsSignal, String locationSignal, String industrySignal,
                           String aiSignal, String salarySignal, String functionalSalarySignal,
                           String labourParticipation, String unemploymentRate,
                           String itSectorVolume, String itFunctionalTop, String itSectorSource,
                           List<SectorSignal> sectorSignals,
                           String ncsUrl, String outlookUrl, String salaryUrl, String plfsUrl,
                           String itSectorUrl, String lastFetchedAt) {}

    public record JobItem(String organization, String position, String postingDate, String vacancy, String type, String sourceUrl) {}
    public record SectorSignal(String name, String value, String note, String source, String publishedDate) {}
}
