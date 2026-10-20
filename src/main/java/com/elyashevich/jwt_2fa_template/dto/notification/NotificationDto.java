package com.elyashevich.jwt_2fa_template.dto.notification;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationDto {
    @Builder.Default
    private UUID id = UUID.randomUUID();
    private NotificationEventType eventType;
    private Long userId;
    private String receiver;
    private UUID targetId;
    @Builder.Default
    private Map<String, Object> params = Map.of();
    private Set<NotificationChannel> channels;
}
