package com.nexora.service;

public final class StudentLoginId {
    private StudentLoginId() {}
    public static String normalize(String value) {
        String v = value == null ? "" : value.trim();
        if (v.isBlank()) return "";
        if (v.contains("@")) return v.toLowerCase();
        return v.toLowerCase();
    }
    public static String normalizeEmail(String value) {
        String v = value == null ? "" : value.trim().toLowerCase();
        if (!v.contains("@")) v = v + "@srmist.edu.in";
        return v;
    }
}
