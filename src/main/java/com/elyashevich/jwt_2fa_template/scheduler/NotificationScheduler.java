package com.elyashevich.jwt_2fa_template.scheduler;

import com.elyashevich.jwt_2fa_template.service.notification.dispatch.NotificationDispatcher;
import com.elyashevich.jwt_2fa_template.service.notification.template.NotificationTemplateRegistry;
import com.elyashevich.jwt_2fa_template.service.notification.template.NotificationTemplateRenderer;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private final NotificationDispatcher notificationDispatcher;
    private final NotificationTemplateRegistry notificationTemplateRegistry;
    private final NotificationTemplateRenderer notificationTemplateRenderer;

    @Scheduled(fixedDelayString = "${app.notification.dispatch-delay:2s}")
    public void dispatch() {
        notificationDispatcher.dispatch();
    }

    @Scheduled(fixedDelayString = "${app.notification.cleanup-delay:1h}")
    public void cleanup() {
        notificationDispatcher.cleanup();
    }

    @Scheduled(fixedDelayString = "${app.notification.templates-refresh:5m}", initialDelayString = "${app.notification.templates-refresh:5m}")
    public void refreshTemplates() {
        notificationTemplateRegistry.refresh();
        notificationTemplateRenderer.clearCache();
    }
}
