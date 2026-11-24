package com.elyashevich.jwt_2fa_template.dto.checkers;

import com.elyashevich.jwt_2fa_template.entity.CheckersGame;
import com.elyashevich.jwt_2fa_template.entity.CheckersGameStatus;
import com.elyashevich.jwt_2fa_template.entity.CheckersSide;
import java.util.UUID;

public record CheckersGameSnapshot(UUID id, long whitePlayerId, Long blackPlayerId, String board,
                                   CheckersSide turn, CheckersGameStatus status, CheckersSide winner, long version) {

    public static CheckersGameSnapshot from(CheckersGame game) {
        return new CheckersGameSnapshot(game.getId(), game.getWhitePlayerId(), game.getBlackPlayerId(),
                game.getBoard(), game.getTurn(), game.getStatus(), game.getWinner(), game.getVersion());
    }

    public boolean hasParticipant(long playerId) {
        return whitePlayerId == playerId || Long.valueOf(playerId).equals(blackPlayerId);
    }
}
