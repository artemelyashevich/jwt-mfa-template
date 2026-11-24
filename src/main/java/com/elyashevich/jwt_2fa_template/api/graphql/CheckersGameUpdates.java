package com.elyashevich.jwt_2fa_template.api.graphql;

import static com.elyashevich.jwt_2fa_template.dto.cache.CacheType.CHECKERS_GAME_SNAPSHOT;

import com.elyashevich.jwt_2fa_template.dto.checkers.CheckersGameChanged;
import com.elyashevich.jwt_2fa_template.dto.checkers.CheckersGameSnapshot;
import com.elyashevich.jwt_2fa_template.service.CacheService;
import com.elyashevich.jwt_2fa_template.service.CheckersGameService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Component
@RequiredArgsConstructor
@Slf4j
class CheckersGameUpdates {

    static final long CURRENT_VERSION = -1;

    private static final Duration SNAPSHOT_TTL = Duration.ofSeconds(30);

    private final CacheService cacheService;
    private final CheckersGameService checkersGameService;
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
    void publish(CheckersGameChanged event) {
        var snapshot = event.snapshot();
        try {
            cacheService.put(CHECKERS_GAME_SNAPSHOT, cacheKey(snapshot.id(), snapshot.version()), snapshot, SNAPSHOT_TTL);
        } catch (DataAccessException ex) {
            log.warn("Could not cache checkers update for game {}", snapshot.id(), ex);
        }

        topic.publishAsync(snapshot.id() + ":" + snapshot.version()).whenComplete((listeners, error) -> {
            if (error != null) {
                log.warn("Could not publish checkers update for game {}", snapshot.id(), error);
            }
        });
    }

    Flux<Long> forGame(UUID gameId) {
        String id = gameId.toString();
        String prefix = id + ":";
        return changes.asFlux()
                .filter(message -> message.equals(id) || message.startsWith(prefix))
                .map(message -> message.startsWith(prefix) ? Long.parseLong(message.substring(prefix.length())) : CURRENT_VERSION);
    }

    CheckersGameSnapshot load(UUID gameId, long version, long playerId) {
        if (version >= 0) {
            var snapshot = cachedSnapshot(gameId, version);
            if (snapshot.isPresent()) {
                var game = snapshot.get();
                if (!game.hasParticipant(playerId)) {
                    throw new AccessDeniedException("Only participants can access the game");
                }
                return game;
            }
        }
        return checkersGameService.get(gameId, playerId);
    }

    private Optional<CheckersGameSnapshot> cachedSnapshot(UUID gameId, long version) {
        try {
            return cacheService.get(CHECKERS_GAME_SNAPSHOT, cacheKey(gameId, version));
        } catch (DataAccessException ex) {
            log.warn("Could not read cached checkers update for game {}", gameId, ex);
            return Optional.empty();
        }
    }

    private static String cacheKey(UUID gameId, long version) {
        return gameId + ":" + version;
    }

    private synchronized void receive(String gameId) {
        changes.tryEmitNext(gameId);
    }
}
