package com.nexora.career.market;

import java.util.List;

/**
 * A dated, source-attributed snapshot of the wider India tech market.
 * It is deliberately a snapshot rather than pretending to be live in-process data.
 */
public record MarketPulse(
        String snapshotDate,
        String livePostings,
        String companiesTracked,
        String jdsAnalysed,
        String salaryCohorts,
        List<String> topSkills,
        String source,
        String sourceUrl
) {}
