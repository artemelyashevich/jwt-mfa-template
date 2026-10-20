package com.elyashevich.jwt_2fa_template.service.impl;

import com.elyashevich.jwt_2fa_template.dto.cache.CacheType;
import com.elyashevich.jwt_2fa_template.exception.AppException;
import com.elyashevich.jwt_2fa_template.service.CacheService;
import com.elyashevich.jwt_2fa_template.util.JsonHelper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CacheServiceImpl implements CacheService {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);
    private static final RedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>(
        "local count = redis.call('incr', KEYS[1]) if count == 1 then redis.call('pexpire', KEYS[1], ARGV[1]) end return count",
        Long.class
    );

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(CacheType type, Object key) {
        String redisKey = buildKey(type, key);
        return (Optional<T>) read(type, key, redisKey, stringRedisTemplate.opsForValue().get(redisKey));
    }

    @Override
    public <T> T get(CacheType cacheType, Object key, Supplier<T> loader) {
        Optional<T> cached;
        try {
            cached = get(cacheType, key);
        } catch (DataAccessException e) {
            return loader.get();
        }
        if (cached.isPresent()) {
            return cached.get();
        }
        T loaded = loader.get();
        try {
            put(cacheType, key, loaded);
        } catch (DataAccessException e) {
        }
        return loaded;
    }

    @Override
    public void put(CacheType type, Object key, Object value) {
        put(type, key, value, DEFAULT_TTL);
    }

    @Override
    public List<Optional<?>> getAll(Object key, CacheType... cacheTypes) {
        List<String> redisKeys = Arrays.stream(cacheTypes).map(cacheType -> buildKey(cacheType, key)).toList();
        List<String> values = stringRedisTemplate.opsForValue().multiGet(redisKeys);
        List<Optional<?>> result = new ArrayList<>(cacheTypes.length);
        for (int i = 0; i < cacheTypes.length; i++) {
            result.add(read(cacheTypes[i], key, redisKeys.get(i), values == null ? null : values.get(i)));
        }
        return result;
    }

    @Override
    public void put(CacheType type, Object key, Object value, Duration ttl) {
        requireNonNullValue(type, key, value);

        String redisKey = buildKey(type, key);

        String json = JsonHelper.toJson(value);

        stringRedisTemplate.opsForValue().set(redisKey, json, ttl);
    }

    @Override
    public void clear(CacheType type) {
        stringRedisTemplate.delete(type.name());

        ScanOptions options = ScanOptions.scanOptions()
            .match(type.name() + ":*")
            .count(1000)
            .build();

        try (RedisConnection connection = Objects.requireNonNull(
            stringRedisTemplate.getConnectionFactory()
        ).getConnection()) {

            List<byte[]> keysToDelete = new ArrayList<>();

            try (Cursor<byte[]> cursor = connection.scan(options)) {
                cursor.forEachRemaining(keysToDelete::add);
            }

            if (!keysToDelete.isEmpty()) {
                connection.keyCommands().del(keysToDelete.toArray(byte[][]::new));
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to clear cache: " + type, e);
        }
    }

    @Override
    public void evict(CacheType type, Object key) {
        stringRedisTemplate.delete(buildKey(type, key));
    }

    @Override
    public Duration getRemainingTtl(CacheType cacheType, String key) {
        Long seconds = stringRedisTemplate.getExpire(buildKey(cacheType, key), TimeUnit.SECONDS);
        if (seconds == null || seconds < 0) {
            return Duration.ZERO;
        }
        return Duration.ofSeconds(seconds);
    }

    @Override
    public boolean putIfAbsent(CacheType cacheType, Object key, Object value, Duration ttl) {
        requireNonNullValue(cacheType, key, value);

        String redisKey = buildKey(cacheType, key);

        String json = JsonHelper.toJson(value);

        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(redisKey, json, ttl);

        return Boolean.TRUE.equals(success);
    }

    @Override
    public long increment(CacheType cacheType, Object key, Duration window) {
        String redisKey = buildKey(cacheType, key);

        Long count = stringRedisTemplate.execute(INCREMENT_SCRIPT, List.of(redisKey), String.valueOf(window.toMillis()));
        if (count == null) {
            return 0L;
        }

        return count;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getAndDelete(CacheType cacheType, Object key) {
        String json = stringRedisTemplate.opsForValue().getAndDelete(buildKey(cacheType, key));
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable((T) JsonHelper.fromJson(json, cacheType.getType()));
        } catch (AppException e) {
            return Optional.empty();
        }
    }

    private String buildKey(CacheType cacheType, Object key) {
        return cacheType.name() + ":" + key;
    }

    private Optional<?> read(CacheType cacheType, Object key, String redisKey, String json) {
        if (json == null) {
            return Optional.empty();
        }
        try {
            Object value = JsonHelper.fromJson(json, cacheType.getType());
            return Optional.ofNullable(value);
        } catch (AppException e) {
            // An entry written by an older class version must not break the request: treat it as a miss
            stringRedisTemplate.delete(redisKey);
            return Optional.empty();
        }
    }

    private void requireNonNullValue(CacheType cacheType, Object key, Object value) {
        if (value == null) {
            throw new IllegalArgumentException(
                String.format(
                    "Cache value must not be null. cache=%s, key=%s",
                    cacheType,
                    key
                )
            );
        }
    }
}
