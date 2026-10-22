package com.nexora.career;

/** Student's saved Career GPS destination. Programme is explicit to avoid mixing employer drives. */
public record CareerGpsSelection(
        String registrationNo,
        String primaryPath,
        String targetCompany,
        String targetProgram,
        String targetRole,
        String targetBand,
        String sourceVersion
) {}
