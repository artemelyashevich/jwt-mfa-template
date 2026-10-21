package com.elyashevich.jwt_2fa_template.repository;

import com.elyashevich.jwt_2fa_template.dto.notification.DeliveryStatus;
import com.elyashevich.jwt_2fa_template.dto.notification.NotificationChannel;
import com.elyashevich.jwt_2fa_template.entity.NotificationDelivery;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {

    boolean existsByEventIdAndChannel(UUID eventId, NotificationChannel channel);

    @Query("SELECT d.eventId AS eventId, d.channel AS channel FROM NotificationDelivery d WHERE d.eventId IN :eventIds")
    List<EventChannelProjection> findEventChannelsByEventIdIn(@Param("eventIds") Collection<UUID> eventIds);

    @Query(value = """
        SELECT * FROM notification_delivery
        WHERE status = 'PENDING' AND next_attempt_at <= now()
        ORDER BY next_attempt_at
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<NotificationDelivery> lockDueDeliveries(int limit);

    @Modifying
    @Query("""
        DELETE FROM NotificationDelivery d
        WHERE d.status <> DeliveryStatus.PENDING
          AND d.createdAt < :before
        """)
    int deleteProcessedBefore(Instant before);

    @Modifying
    @Query("""
        UPDATE NotificationDelivery d
        SET d.status = :status, d.nextAttemptAt = :nextAttemptAt, d.lastError = :lastError, d.sentAt = :sentAt
        WHERE d.id = :id
        """)
    int updateResult(@Param("id") Long id,
        @Param("status") DeliveryStatus status,
        @Param("nextAttemptAt") Instant nextAttemptAt,
        @Param("lastError") String lastError,
        @Param("sentAt") Instant sentAt);
}
