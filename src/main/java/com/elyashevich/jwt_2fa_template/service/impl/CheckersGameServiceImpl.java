package com.elyashevich.jwt_2fa_template.service.impl;

import com.elyashevich.jwt_2fa_template.dto.checkers.CheckersGameChanged;
import com.elyashevich.jwt_2fa_template.dto.checkers.CheckersGameSnapshot;
import com.elyashevich.jwt_2fa_template.entity.CheckersGame;
import com.elyashevich.jwt_2fa_template.entity.CheckersGameStatus;
import com.elyashevich.jwt_2fa_template.repository.CheckersGameRepository;
import com.elyashevich.jwt_2fa_template.service.CheckersGameService;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CheckersGameServiceImpl implements CheckersGameService {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final CheckersGameRepository checkersGameRepository;

    @Override
    @Transactional
    public CheckersGameSnapshot create(long playerId) {
        return CheckersGameSnapshot.from(checkersGameRepository.saveAndFlush(new CheckersGame(playerId)));
    }

    @Override
    @Transactional(readOnly = true)
    public CheckersGameSnapshot get(UUID gameId, long playerId) {
        var game = findGame(gameId);
        requireParticipant(game, playerId);
        return CheckersGameSnapshot.from(game);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CheckersGameSnapshot quickMatch(long playerId) {
        checkersGameRepository.lockQuickMatchQueue();
        var existing = checkersGameRepository.findOpenQuickMatch(playerId);
        if (existing.isPresent()) {
            return CheckersGameSnapshot.from(existing.get());
        }

        var waiting = checkersGameRepository.findWaitingQuickMatch();
        if (waiting.isPresent()) {
            var game = waiting.get();
            game.join(playerId);
            return publishChange(game);
        }

        var game = CheckersGame.waitingForQuickMatch(playerId);
        return CheckersGameSnapshot.from(checkersGameRepository.saveAndFlush(game));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CheckersGameSnapshot cancelQuickMatch(UUID gameId, long playerId) {
        checkersGameRepository.lockQuickMatchQueue();
        var game = findGame(gameId);
        if (game.getWhitePlayerId() != playerId) {
            throw new AccessDeniedException("Only the waiting player can cancel the search");
        }
        if (game.getStatus() == CheckersGameStatus.CANCELLED) {
            return CheckersGameSnapshot.from(game);
        }
        game.cancelQuickMatch();
        return publishChange(game);
    }

    @Override
    @Transactional
    public CheckersGameSnapshot join(UUID gameId, long playerId) {
        var game = findGame(gameId);
        if (Long.valueOf(playerId).equals(game.getBlackPlayerId())) {
            return CheckersGameSnapshot.from(game);
        }
        if (game.isQuickMatch()) {
            throw new AccessDeniedException("Quick matches are joined through matchmaking");
        }
        game.join(playerId);
        return publishChange(game);
    }

    @Override
    @Transactional
    public CheckersGameSnapshot makeMove(UUID gameId, long playerId, long expectedVersion, List<Integer> route) {
        var game = findGame(gameId);
        requireParticipant(game, playerId);
        if (game.getVersion() != expectedVersion) {
            throw new ObjectOptimisticLockingFailureException(CheckersGame.class, gameId);
        }
        game.makeMove(playerId, route);
        return publishChange(game);
    }

    private CheckersGameSnapshot publishChange(CheckersGame game) {
        checkersGameRepository.flush();
        var snapshot = CheckersGameSnapshot.from(game);
        applicationEventPublisher.publishEvent(new CheckersGameChanged(snapshot));
        return snapshot;
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
}
