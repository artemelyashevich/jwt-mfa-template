package com.elyashevich.jwt_2fa_template.entity;

import com.elyashevich.jwt_2fa_template.dto.notification.DeliveryStatus;
import com.elyashevich.jwt_2fa_template.dto.notification.NotificationChannel;
import com.elyashevich.jwt_2fa_template.dto.notification.NotificationEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "notification_delivery")
public class NotificationDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private NotificationEventType event;

    private Long userId;

    private UUID eventId;

    private String recipient;

    @Column(name = "subject", columnDefinition = "text")
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 16)
    private NotificationChannel channel;

    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private DeliveryStatus status = DeliveryStatus.PENDING;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Builder.Default
    private int attempts = 0;

    @Builder.Default
    private Instant nextAttemptAt = Instant.now();

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}
