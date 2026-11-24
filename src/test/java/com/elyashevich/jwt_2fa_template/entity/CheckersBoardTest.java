package com.elyashevich.jwt_2fa_template.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.Test;

class CheckersBoardTest {

    @Test
    void rejectsQuietMoveWhenAnotherPieceCanCaptureBackwards() {
        var board = boardWith(9, 'w', 13, 'b', 20, 'w');

        assertThatIllegalArgumentException()
                .isThrownBy(() -> board.move(CheckersSide.WHITE, List.of(20, 16)));
        var capturingBoard = boardWith(9, 'w', 13, 'b', 20, 'w');
        capturingBoard.move(CheckersSide.WHITE, List.of(9, 16));

        assertThat(capturingBoard.position()).isEqualTo(boardWith(16, 'w', 20, 'w').position());
    }

    @Test
    void promotesDuringCaptureAndRequiresContinuationAsKing() {
        var board = boardWith(10, 'w', 6, 'b', 5, 'b');

        assertThatIllegalArgumentException()
                .isThrownBy(() -> board.move(CheckersSide.WHITE, List.of(10, 1)));
        assertThat(board.position()).isEqualTo(boardWith(10, 'w', 6, 'b', 5, 'b').position());
        board.move(CheckersSide.WHITE, List.of(10, 1, 12));

        assertThat(board.position()).isEqualTo(boardWith(12, 'W').position());
        assertThat(board.hasMove(CheckersSide.BLACK)).isFalse();
    }

    @Test
    void capturedPieceBlocksKingUntilEndOfTurn() {
        var board = boardWith(9, 'W', 5, 'b', 14, 'b');

        board.move(CheckersSide.WHITE, List.of(9, 0));

        assertThat(board.position()).isEqualTo(boardWith(0, 'W', 14, 'b').position());
    }

    @Test
    void acceptsShorterCaptureRouteWhenAnotherRouteTakesMorePieces() {
        char[] position = boardWith(10, 'w', 6, 'b', 5, 'b').position().toCharArray();
        position[7] = 'b';
        var board = new CheckersBoard(new String(position));

        board.move(CheckersSide.WHITE, List.of(10, 3));

        assertThat(board.position()).isEqualTo(boardWith(3, 'W', 6, 'b', 5, 'b').position());
    }

    @Test
    void findsNoMoveForAManBlockedAtTheEdge() {
        var board = boardWith(4, 'w', 0, 'b');

        assertThat(board.hasMove(CheckersSide.WHITE)).isFalse();
    }

    @Test
    void allowsKingToSlideAcrossSeveralEmptySquares() {
        var board = boardWith(9, 'W');

        assertThat(board.hasMove(CheckersSide.WHITE)).isTrue();
        board.move(CheckersSide.WHITE, List.of(9, 23));

        assertThat(board.position()).isEqualTo(boardWith(23, 'W').position());
    }

    private static CheckersBoard boardWith(int square, char piece) {
        return boardWith(square, piece, square, piece);
    }

    private static CheckersBoard boardWith(int firstSquare, char firstPiece, int secondSquare, char secondPiece) {
        char[] position = ".".repeat(32).toCharArray();
        position[firstSquare] = firstPiece;
        position[secondSquare] = secondPiece;
        return new CheckersBoard(new String(position));
    }

    private static CheckersBoard boardWith(int firstSquare, char firstPiece, int secondSquare, char secondPiece,
                                           int thirdSquare, char thirdPiece) {
        char[] position = boardWith(firstSquare, firstPiece, secondSquare, secondPiece).position().toCharArray();
        position[thirdSquare] = thirdPiece;
        return new CheckersBoard(new String(position));
    }
}
