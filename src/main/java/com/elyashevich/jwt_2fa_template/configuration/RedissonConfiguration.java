package com.elyashevich.jwt_2fa_template.configuration;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.data.redis.autoconfigure.DataRedisConnectionDetails;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfiguration {

    private static final int RETRY_ATTEMPTS = 1;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(DataRedisConnectionDetails connectionDetails, DataRedisProperties redisProperties) {
        return Redisson.create(redissonConfig(connectionDetails, redisProperties));
    }

    static Config redissonConfig(DataRedisConnectionDetails connectionDetails, DataRedisProperties redisProperties) {
        DataRedisConnectionDetails.Standalone standalone = connectionDetails.getStandalone();
        boolean ssl = redisProperties.getSsl().isEnabled() || connectionDetails.getSslBundle() != null;
        Config config = new Config();
        config.useSingleServer()
            .setAddress((ssl ? "rediss://" : "redis://") + standalone.getHost() + ":" + standalone.getPort())
            .setDatabase(standalone.getDatabase())
            .setUsername(connectionDetails.getUsername())
            .setPassword(connectionDetails.getPassword())
            .setRetryAttempts(RETRY_ATTEMPTS);
        if (redisProperties.getTimeout() != null) {
            config.useSingleServer().setTimeout(Math.toIntExact(redisProperties.getTimeout().toMillis()));
        }
        if (redisProperties.getConnectTimeout() != null) {
            config.useSingleServer().setConnectTimeout(Math.toIntExact(redisProperties.getConnectTimeout().toMillis()));
        }
        return config;
    }
}
