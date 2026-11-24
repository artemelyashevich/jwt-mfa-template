package com.elyashevich.jwt_2fa_template.api.graphql;

import static com.elyashevich.jwt_2fa_template.dto.cache.CacheType.CHECKERS_GAME_SNAPSHOT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.elyashevich.jwt_2fa_template.dto.checkers.CheckersGameSnapshot;
import com.elyashevich.jwt_2fa_template.entity.CheckersGameStatus;
import com.elyashevich.jwt_2fa_template.entity.CheckersSide;
import com.elyashevich.jwt_2fa_template.service.CacheService;
import com.elyashevich.jwt_2fa_template.service.CheckersGameService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.access.AccessDeniedException;

class CheckersGameUpdatesTest {

    private static final long WHITE_PLAYER = 1L;
    private static final long BLACK_PLAYER = 2L;
    private static final long VERSION = 5L;

    private final UUID gameId = UUID.randomUUID();
    private final String cacheKey = gameId + ":" + VERSION;
    private final CacheService cacheService = mock(CacheService.class);
    private final CheckersGameService checkersGameService = mock(CheckersGameService.class);
    private final CheckersGameUpdates updates = new CheckersGameUpdates(
            cacheService, checkersGameService, mock(RedissonClient.class));
    private final CheckersGameSnapshot snapshot = new CheckersGameSnapshot(gameId, WHITE_PLAYER, BLACK_PLAYER,
            "b".repeat(12) + ".".repeat(8) + "w".repeat(12), CheckersSide.WHITE, CheckersGameStatus.IN_PROGRESS, null, VERSION);

    @Test
    void servesCachedVersionOnlyToParticipantsWithoutReadingDatabase() {
        when(cacheService.<CheckersGameSnapshot>get(CHECKERS_GAME_SNAPSHOT, cacheKey)).thenReturn(Optional.of(snapshot));

        assertThat(updates.load(gameId, VERSION, WHITE_PLAYER)).isEqualTo(snapshot);
        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> updates.load(gameId, VERSION, 3L));
        verifyNoInteractions(checkersGameService);
    }

    @Test
    void loadsAuthoritativePositionWhenTheSnapshotIsMissingOrRedisFails() {
        when(cacheService.<CheckersGameSnapshot>get(CHECKERS_GAME_SNAPSHOT, cacheKey))
                .thenReturn(Optional.empty())
                .thenThrow(new DataAccessResourceFailureException("Redis unavailable"));
        when(checkersGameService.get(gameId, WHITE_PLAYER)).thenReturn(snapshot);

        assertThat(updates.load(gameId, VERSION, WHITE_PLAYER)).isEqualTo(snapshot);
        assertThat(updates.load(gameId, VERSION, WHITE_PLAYER)).isEqualTo(snapshot);
    }
}
