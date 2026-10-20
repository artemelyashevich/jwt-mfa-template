package com.elyashevich.jwt_2fa_template.service.notification.template;

import com.elyashevich.jwt_2fa_template.configuration.ApplicationProperties;
import com.elyashevich.jwt_2fa_template.dto.notification.NotificationChannel;
import com.elyashevich.jwt_2fa_template.dto.notification.NotificationEventType;
import com.elyashevich.jwt_2fa_template.entity.UserLocale;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NotificationTemplateRegistry {

    private static final Pattern FILE_NAME = Pattern.compile("/([a-z-]+)/([A-Z_]+)\\.([a-z]+)\\.html$");

    private final TemplateLocation location;
    private final ResourcePatternResolver resolver;
    private volatile Map<NotificationEventType, Map<NotificationChannel, Set<UserLocale>>> templates;

    @Autowired
    public NotificationTemplateRegistry(ApplicationProperties applicationProperties) {
        this(TemplateLocation.parse(applicationProperties.getNotification().getTemplatesLocation()),
            new PathMatchingResourcePatternResolver());
    }

    NotificationTemplateRegistry(TemplateLocation location, ResourcePatternResolver resolver) {
        this.location = location;
        this.resolver = resolver;
        this.templates = scan();
        log.info("Loaded notification templates from {}: {}", location.scanPattern(), templates);
    }

    public void refresh() {
        try {
            Map<NotificationEventType, Map<NotificationChannel, Set<UserLocale>>> scanned = scan();
            if (!scanned.equals(templates)) {
                log.info("Notification templates changed: {}", scanned);
            }
            templates = scanned;
        } catch (RuntimeException e) {
            log.error("Failed to refresh notification templates from {}, keeping previous set", location.scanPattern(), e);
        }
    }

    public static String directory(NotificationChannel channel) {
        return channel.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static String templateName(NotificationEventType eventType, NotificationChannel channel, UserLocale locale) {
        return directory(channel) + "/" + eventType.name() + "." + locale.getCode() + ".html";
    }

    public Map<NotificationEventType, Map<NotificationChannel, Set<UserLocale>>> findAll() {
        return templates;
    }

    public Set<NotificationChannel> findChannels(NotificationEventType eventType) {
        return Optional.ofNullable(templates.get(eventType))
            .map(Map::keySet)
            .map(Set::copyOf)
            .orElse(Set.of());
    }

    public boolean exists(NotificationEventType eventType, NotificationChannel channel) {
        return findChannels(eventType).contains(channel);
    }

    public Optional<String> findTemplateName(NotificationEventType eventType, NotificationChannel channel, UserLocale locale) {
        Set<UserLocale> locales = Optional.ofNullable(templates.get(eventType))
            .map(byChannel -> byChannel.get(channel))
            .orElse(Set.of());
        return Optional.of(locale).filter(locales::contains)
            .or(() -> Optional.of(UserLocale.RU).filter(locales::contains))
            .or(() -> locales.stream().findFirst())
            .map(chosen -> templateName(eventType, channel, chosen));
    }

    private Map<NotificationEventType, Map<NotificationChannel, Set<UserLocale>>> scan() {
        Map<NotificationEventType, Map<NotificationChannel, Set<UserLocale>>> scanned = new EnumMap<>(NotificationEventType.class);
        try {
            for (Resource resource : resolver.getResources(location.scanPattern())) {
                register(scanned, resource.getURL().getPath());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to scan notification templates in " + location.scanPattern(), e);
        }
        Map<NotificationEventType, Map<NotificationChannel, Set<UserLocale>>> snapshot = new EnumMap<>(NotificationEventType.class);
        scanned.forEach((eventType, byChannel) -> {
            Map<NotificationChannel, Set<UserLocale>> channels = new EnumMap<>(NotificationChannel.class);
            byChannel.forEach((channel, locales) -> channels.put(channel, Collections.unmodifiableSet(locales)));
            snapshot.put(eventType, Collections.unmodifiableMap(channels));
        });
        return Collections.unmodifiableMap(snapshot);
    }

    private static void register(Map<NotificationEventType, Map<NotificationChannel, Set<UserLocale>>> scanned, String path) {
        Matcher matcher = FILE_NAME.matcher(path);
        if (!matcher.find()) {
            throw new IllegalStateException("Unexpected notification template file name: " + path);
        }
        NotificationChannel channel = Arrays.stream(NotificationChannel.values())
            .filter(candidate -> directory(candidate).equals(matcher.group(1)))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Unknown notification channel in template path: " + path));
        NotificationEventType eventType = Arrays.stream(NotificationEventType.values())
            .filter(candidate -> candidate.name().equals(matcher.group(2)))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Unknown notification event in template path: " + path));
        UserLocale locale = Arrays.stream(UserLocale.values())
            .filter(candidate -> candidate.getCode().equals(matcher.group(3)))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Unknown locale in template path: " + path));
        scanned.computeIfAbsent(eventType, key -> new EnumMap<>(NotificationChannel.class))
            .computeIfAbsent(channel, key -> EnumSet.noneOf(UserLocale.class))
            .add(locale);
    }
}
