package com.elyashevich.jwt_2fa_template.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.List;
import org.junit.jupiter.api.Test;

class CheckersGameTest {

    @Test
    void newGameStartsWithWhiteToMoveAndWaitsForAnOpponent() {
        var game = new CheckersGame(42L);

        assertThat(game.getWhitePlayerId()).isEqualTo(42L);
        assertThat(game.getBlackPlayerId()).isNull();
        assertThat(game.getStatus()).isEqualTo(CheckersGameStatus.WAITING);
        assertThat(game.getTurn()).isEqualTo(CheckersSide.WHITE);
        assertThat(game.getWinner()).isNull();
        assertThat(game.getBoard()).isEqualTo("bbbb".repeat(3) + "....".repeat(2) + "wwww".repeat(3));
    }

    @Test
    void changesTurnOnlyAfterAValidMoveByTheCurrentPlayer() {
        var game = new CheckersGame(42L);
        var openingMove = List.of(20, 16);

        assertThatIllegalStateException().isThrownBy(() -> game.makeMove(42L, openingMove));
        assertThatIllegalArgumentException().isThrownBy(() -> game.join(42L));

        game.join(43L);

        assertThatIllegalStateException().isThrownBy(() -> game.join(44L));
        assertThatIllegalArgumentException().isThrownBy(() -> game.makeMove(43L, openingMove));
        assertThat(game.getTurn()).isEqualTo(CheckersSide.WHITE);

        game.makeMove(42L, openingMove);

        assertThat(game.getTurn()).isEqualTo(CheckersSide.BLACK);
        assertThat(game.getBoard().charAt(20)).isEqualTo('.');
        assertThat(game.getBoard().charAt(16)).isEqualTo('w');
    }
}
