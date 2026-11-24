package com.elyashevich.jwt_2fa_template.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "checkers_games")
public class CheckersGame {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "white_player_id", nullable = false, updatable = false)
    private Long whitePlayerId;

    @Column(name = "black_player_id")
    private Long blackPlayerId;

    @Column(nullable = false, length = 32)
    private String board = "bbbbbbbbbbbb........wwwwwwwwwwww";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private CheckersSide turn = CheckersSide.WHITE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CheckersGameStatus status = CheckersGameStatus.WAITING;

    @Enumerated(EnumType.STRING)
    @Column(length = 5)
    private CheckersSide winner;

    @Version
    @Column(nullable = false)
    private Long version;

    public CheckersGame(long whitePlayerId) {
        this.whitePlayerId = whitePlayerId;
    }

    public void join(long playerId) {
        if (whitePlayerId == playerId) {
            throw new IllegalArgumentException("A player cannot join their own game");
        }
        if (status != CheckersGameStatus.WAITING) {
            throw new IllegalStateException("The game is no longer waiting for an opponent");
        }
        blackPlayerId = playerId;
        status = CheckersGameStatus.IN_PROGRESS;
    }

    public void makeMove(long playerId, List<Integer> route) {
        if (status != CheckersGameStatus.IN_PROGRESS) {
            throw new IllegalStateException("The game is not in progress");
        }
        long currentPlayerId = turn == CheckersSide.WHITE ? whitePlayerId : blackPlayerId;
        if (currentPlayerId != playerId) {
            throw new IllegalArgumentException("It is not this player's turn");
        }

        var position = new CheckersBoard(board);
        position.move(turn, route);
        board = position.position();

        CheckersSide nextSide = turn == CheckersSide.WHITE ? CheckersSide.BLACK : CheckersSide.WHITE;
        if (!position.hasMove(nextSide)) {
            winner = turn;
            status = CheckersGameStatus.FINISHED;
        }
        turn = nextSide;
    }
}
