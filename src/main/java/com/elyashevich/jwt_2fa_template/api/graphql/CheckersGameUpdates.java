package com.elyashevich.jwt_2fa_template.api.graphql;

import com.elyashevich.jwt_2fa_template.service.CheckersGameService.GameChanged;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Component
@RequiredArgsConstructor
@Slf4j
class CheckersGameUpdates {

    private final RedissonClient redissonClient;
    private final Sinks.Many<String> changes = Sinks.many().multicast().directBestEffort();

    private RTopic topic;
    private int listenerId;

    @PostConstruct
    void subscribe() {
        topic = redissonClient.getTopic("checkers:game-updated", StringCodec.INSTANCE);
        listenerId = topic.addListener(String.class, (channel, gameId) -> receive(gameId));
    }

    @PreDestroy
    void unsubscribe() {
        topic.removeListener(listenerId);
    }

    @TransactionalEventListener
    void publish(GameChanged event) {
        topic.publishAsync(event.gameId().toString()).whenComplete((listeners, error) -> {
            if (error != null) {
                log.warn("Could not publish checkers update for game {}", event.gameId(), error);
            }
        });
    }

    Flux<String> forGame(UUID gameId) {
        String id = gameId.toString();
        return changes.asFlux().filter(id::equals).onBackpressureLatest();
    }

    private synchronized void receive(String gameId) {
        changes.tryEmitNext(gameId);
    }
}
