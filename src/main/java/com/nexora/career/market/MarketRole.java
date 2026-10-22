package com.nexora.career.market;

import java.util.List;

public record MarketRole(
        String role,
        String shortName,
        String demandRank,
        String salaryP50,
        String salaryNote,
        String yoy,
        String demandSignal,
        String topSkill,
        List<String> coreSkills,
        String whatYouDo,
        String entryPath,
        String futureOutlook,
        String futureEvidence,
        List<String> futureDrivers,
        List<String> watchouts,
        String source,
        String sourceDate,
        String sourceUrl
) {}
