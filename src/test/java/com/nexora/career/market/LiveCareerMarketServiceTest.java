package com.nexora.career.market;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveCareerMarketServiceTest {
    private final LiveCareerMarketService service = new LiveCareerMarketService();

    @Test
    void keepsOnlyExplicitlyTechnicalNcsRowsWithTheirDirectAdvertisementLinks() {
        String html = """
                <table><tbody>
                  <tr><td>1</td><td>Example Tech</td><td>Software Developer</td><td>2026-10-01</td><td>2</td><td>Full-time</td><td><a href="https://careers.example.org/software-role.pdf">Advertisement</a></td></tr>
                  <tr><td>2</td><td>Example Office</td><td>Driver (Ordinary Grade)</td><td>2026-10-01</td><td>1</td><td>Contract</td><td><a href="https://careers.example.org/driver.pdf">Advertisement</a></td></tr>
                  <tr><td>3</td><td>Example Tech</td><td>Backend Developer</td><td>2026-10-02</td><td>3</td><td>Full-time</td><td><a href="blob:https://ncs.gov.in/123">Advertisement</a></td></tr>
                </tbody></table>
                """;

        List<LiveCareerMarketService.JobItem> jobs = service.parseNcsLatest(html);
        assertEquals(1, jobs.size());
        assertEquals("Software Developer", jobs.getFirst().position());
        assertEquals("https://careers.example.org/software-role.pdf", jobs.getFirst().sourceUrl());
    }

    @Test
    void returnsNoJobsWhenAdvertLinkIsMissing() {
        String html = """
                <table><tbody>
                  <tr><td>1</td><td>Example Tech</td><td>Software Engineer</td><td>2026-10-01</td><td>2</td><td>Full-time</td><td>Not supplied</td></tr>
                </tbody></table>
                """;
        assertTrue(service.parseNcsLatest(html).isEmpty());
    }
}
