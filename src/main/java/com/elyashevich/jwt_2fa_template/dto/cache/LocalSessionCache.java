package com.elyashevich.jwt_2fa_template.dto.cache;

import com.elyashevich.jwt_2fa_template.configuration.ApplicationProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LocalSessionCache {

    static final int MAX_ENTRIES = 50_000;

    private final ApplicationProperties applicationProperties;
    private final Map<Long, Entry> entries = new ConcurrentHashMap<>();
    private final AtomicLong version = new AtomicLong();

    public Optional<UserSessionCache> find(Long userId, String token) {
        Entry entry = entries.get(userId);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.isExpired(System.nanoTime())) {
            entries.remove(userId, entry);
            return Optional.empty();
        }
        return entry.matches(token) ? Optional.of(entry.session()) : Optional.empty();
    }

    public long version() {
        return version.get();
    }

    public void put(Long userId, String token, UserSessionCache session, long readVersion) {
        Duration ttl = applicationProperties.getCache().getLocalSessionTtl();
        if (!ttl.isPositive()) {
            return;
        }
        if (entries.size() >= MAX_ENTRIES) {
            purge();
        }
        Entry entry = new Entry(token, session, System.nanoTime() + ttl.toNanos());
        entries.put(userId, entry);
        if (version.get() != readVersion) {
            entries.remove(userId, entry);
        }
    }

    public void evict(Long userId) {
        version.incrementAndGet();
        entries.remove(userId);
    }

    int size() {
        return entries.size();
    }

    private void purge() {
        long now = System.nanoTime();
        entries.values().removeIf(entry -> entry.isExpired(now));
        if (entries.size() >= MAX_ENTRIES) {
            entries.clear();
        }
    }

    private record Entry(String token, UserSessionCache session, long expiresAtNanos) {

        boolean isExpired(long now) {
            return now - expiresAtNanos >= 0;
        }

        boolean matches(String candidate) {
            return MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), candidate.getBytes(StandardCharsets.UTF_8));
        }
    }
}