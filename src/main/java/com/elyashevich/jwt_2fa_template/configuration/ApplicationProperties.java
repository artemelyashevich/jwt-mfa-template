package com.elyashevich.jwt_2fa_template.configuration;

import com.elyashevich.jwt_2fa_template.dto.otp.OtpType;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class ApplicationProperties {

    private Auth auth = new Auth();
    private Cache cache = new Cache();
    private Cors cors = new Cors();
    private Notification notification = new Notification();

    @Getter
    @Setter
    public static class Auth {

        private String accessSecret;
        private String refreshSecret;
        private long accessTokenExpirationSeconds = 3600;
        private long refreshTokenExpirationSeconds = 3600;
    }

    @Getter
    @Setter
    public static class OtpLimit {

        private boolean enabled = false;
        private int dailyRequestLimit = 10;
        private int dailyFailureLimit = 20;
        private Duration window = Duration.ofDays(1);
    }

    @Getter
    @Setter
    public static class Cache {

        private int otpTtlSeconds = 60;
        private int otpResendCooldownSeconds = 60;
        private int otpMaxAttempts = 5;
        private Duration otpConfirmationTtl = Duration.ofMinutes(10);
        private Duration localSessionTtl = Duration.ofSeconds(5);
        private Map<OtpType, OtpLimit> otpLimits = new EnumMap<>(OtpType.class);

        public OtpLimit getOtpLimit(OtpType type) {
            return otpLimits.getOrDefault(type, new OtpLimit());
        }

        public Duration getOtpSeconds() {
            return Duration.ofSeconds(otpTtlSeconds);
        }

        public Duration getOtpResendCooldown() {
            return Duration.ofSeconds(otpResendCooldownSeconds);
        }

    }

    /**
     * Browser origins (scheme://host[:port]) allowed to call the API cross-origin, e.g. a frontend served
     * from another port or domain. Credentials are not allowed: the API authenticates via the Authorization header.
     */
    @Getter
    @Setter
    public static class Cors {

        private List<String> allowedOrigins = List.of("http://localhost:3000");
    }

    @Getter
    @Setter
    public static class Notification {

        private String emailFrom;
        private Duration dispatchDelay = Duration.ofSeconds(2);
        private Duration cleanupDelay = Duration.ofHours(1);
        private int batchSize = 20;
        private int maxAttempts = 5;
        private Duration retryDelay = Duration.ofSeconds(30);
        private Duration leaseDuration = Duration.ofMinutes(5);
        private Duration retention = Duration.ofDays(30);
        private String templatesLocation = "classpath:templates/notification/";
        private Duration templatesRefresh = Duration.ofMinutes(5);
    }
}
