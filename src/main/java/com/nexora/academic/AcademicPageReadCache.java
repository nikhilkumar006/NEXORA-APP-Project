package com.nexora.academic;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Short-lived cache for read-heavy academic pages; explicit syncs clear it immediately. */
@Component
public class AcademicPageReadCache {
    private static final long TTL_MILLIS = Duration.ofMinutes(2).toMillis();
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> keyLocks = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Supplier<T> loader) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Academic cache key must not be blank");
        }
        if (loader == null) {
            throw new IllegalArgumentException("Academic cache loader must not be null");
        }

        final long initialTime = System.currentTimeMillis();
        cleanupExpiredEntries(initialTime);
        Entry entry = entries.get(key);
        if (entry != null && entry.expiresAt > initialTime) return (T) entry.value;

        Object lock = keyLocks.computeIfAbsent(key, ignored -> new Object());
        synchronized (lock) {
            final long checkedAt = System.currentTimeMillis();
            entry = entries.get(key);
            if (entry != null && entry.expiresAt > checkedAt) return (T) entry.value;

            // Exceptions are deliberately not cached. A transient database failure can
            // recover on the next request instead of poisoning the cache for the whole TTL.
            T value = loader.get();
            if (value != null) {
                final long ttlMillis = value instanceof Collection<?> collection && collection.isEmpty()
                        ? Duration.ofSeconds(3).toMillis() : TTL_MILLIS;
                entries.put(key, new Entry(value, System.currentTimeMillis() + ttlMillis));
            }
            if (entries.size() > 2048) cleanupExpiredEntries(System.currentTimeMillis());
            return value;
        }
    }

    private void cleanupExpiredEntries(final long cleanupAt) {
        if (entries.size() > 512) {
            entries.entrySet().removeIf(candidate -> candidate.getValue().expiresAt <= cleanupAt);
        }
    }

    public void clear() { entries.clear(); }

    private record Entry(Object value, long expiresAt) { }
}
