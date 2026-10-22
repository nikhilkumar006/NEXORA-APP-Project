package com.nexora.controller;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** A tiny per-student cache for the expensive Development GPS read model. */
@Component
public class DevelopmentGpsPageCache {
    private static final long TTL_MILLIS = 6_000L;
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    public Map<String, Object> get(String registrationNo) {
        if (registrationNo == null || registrationNo.isBlank()) return null;
        Entry entry = entries.get(registrationNo);
        if (entry == null) return null;
        if (entry.expiresAt() <= System.currentTimeMillis()) {
            entries.remove(registrationNo, entry);
            return null;
        }
        return new LinkedHashMap<>(entry.attributes());
    }

    public void put(String registrationNo, Map<String, ?> attributes) {
        if (registrationNo == null || registrationNo.isBlank() || attributes == null || attributes.isEmpty()) return;
        Map<String, Object> snapshot = new LinkedHashMap<>();
        attributes.forEach((key, value) -> {
            if (key != null && value != null) snapshot.put(key, value);
        });
        entries.put(registrationNo, new Entry(Map.copyOf(snapshot), System.currentTimeMillis() + TTL_MILLIS));
        if (entries.size() > 512) {
            long now = System.currentTimeMillis();
            entries.entrySet().removeIf(item -> item.getValue().expiresAt() <= now);
        }
    }

    public void evict(String registrationNo) {
        if (registrationNo != null) entries.remove(registrationNo);
    }

    private record Entry(Map<String, Object> attributes, long expiresAt) { }
}
