package com.elyashevich.jwt_2fa_template.service;

import com.elyashevich.jwt_2fa_template.entity.CheckersGame;
import com.elyashevich.jwt_2fa_template.repository.CheckersGameRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CheckersGameService {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final CheckersGameRepository checkersGameRepository;

    @Transactional
    public CheckersGame create(long playerId) {
        return checkersGameRepository.saveAndFlush(new CheckersGame(playerId));
    }

    @Transactional(readOnly = true)
    public CheckersGame get(UUID gameId, long playerId) {
        var game = findGame(gameId);
        requireParticipant(game, playerId);
        return game;
    }

    @Transactional
    public CheckersGame join(UUID gameId, long playerId) {
        var game = findGame(gameId);
        if (Long.valueOf(playerId).equals(game.getBlackPlayerId())) {
            return game;
        }
        game.join(playerId);
        checkersGameRepository.flush();
        applicationEventPublisher.publishEvent(new GameChanged(gameId));
        return game;
    }

    @Transactional
    public CheckersGame makeMove(UUID gameId, long playerId, long expectedVersion, List<Integer> route) {
        var game = findGame(gameId);
        requireParticipant(game, playerId);
        if (game.getVersion() != expectedVersion) {
            throw new ObjectOptimisticLockingFailureException(CheckersGame.class, gameId);
        }
        game.makeMove(playerId, route);
        checkersGameRepository.flush();
        applicationEventPublisher.publishEvent(new GameChanged(gameId));
        return game;
    }

    private CheckersGame findGame(UUID gameId) {
        return checkersGameRepository.findById(gameId)
                .orElseThrow(() -> new EntityNotFoundException("Checkers game not found"));
    }

    private static void requireParticipant(CheckersGame game, long playerId) {
        if (game.getWhitePlayerId() != playerId && !Long.valueOf(playerId).equals(game.getBlackPlayerId())) {
            throw new AccessDeniedException("Only participants can access the game");
        }
    }

    public record GameChanged(UUID gameId) {
    }
}
