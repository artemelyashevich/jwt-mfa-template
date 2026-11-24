package com.elyashevich.jwt_2fa_template.repository;

import com.elyashevich.jwt_2fa_template.entity.CheckersGame;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CheckersGameRepository extends JpaRepository<CheckersGame, UUID> {

    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(1930918221, 1)", nativeQuery = true)
    int lockQuickMatchQueue();

    @Query(value = """
            SELECT * FROM checkers_games
            WHERE quick_match AND status IN ('WAITING', 'IN_PROGRESS')
              AND (white_player_id = :playerId OR black_player_id = :playerId)
            LIMIT 1
            """, nativeQuery = true)
    Optional<CheckersGame> findOpenQuickMatch(@Param("playerId") long playerId);

    @Query(value = """
            SELECT * FROM checkers_games WHERE quick_match AND status = 'WAITING'
            """, nativeQuery = true)
    Optional<CheckersGame> findWaitingQuickMatch();
}
