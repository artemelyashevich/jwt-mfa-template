package com.elyashevich.jwt_2fa_template.service;

import com.elyashevich.jwt_2fa_template.dto.cache.CacheType;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public interface CacheService {
    <T> Optional<T> get(CacheType type, Object key);

    <T> T get(CacheType type, Object key, Supplier<T> loader);

    void put(CacheType type, Object key, Object value);

    List<Optional<?>> getAll(Object key, CacheType... cacheTypes);

    void put(CacheType type, Object key, Object value, Duration ttl);

    void clear(CacheType type);

    void evict(CacheType type, Object key);

    Duration getRemainingTtl(CacheType cacheType, String userId);

    boolean putIfAbsent(CacheType cacheType, Object key, Object value, Duration ttl);

    long increment(CacheType cacheType, Object key, Duration window);

    <T> Optional<T> getAndDelete(CacheType cacheType, Object key);
}

