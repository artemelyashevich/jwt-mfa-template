package com.elyashevich.jwt_2fa_template.entity;

import static com.elyashevich.jwt_2fa_template.entity.CheckersMoveGenerator.CAPTURED;
import static com.elyashevich.jwt_2fa_template.entity.CheckersMoveGenerator.EMPTY;
import static com.elyashevich.jwt_2fa_template.entity.CheckersMoveGenerator.NO_STEP;
import static com.elyashevich.jwt_2fa_template.entity.CheckersMoveGenerator.SIZE;
import static com.elyashevich.jwt_2fa_template.entity.CheckersMoveGenerator.SQUARE_COUNT;

import java.util.List;

final class CheckersBoard {

    private static final int MAX_ROUTE_LENGTH = 13;

    private char[] squares;

    CheckersBoard(String position) {
        squares = position.toCharArray();
    }

    void move(CheckersSide side, List<Integer> route) {
        if (route == null || route.size() < 2 || route.size() > MAX_ROUTE_LENGTH
                || route.stream().anyMatch(square -> square == null || square < 0 || square >= SQUARE_COUNT)) {
            throw new IllegalArgumentException("Invalid checkers move route");
        }

        int from = route.getFirst();
        if (!CheckersMoveGenerator.belongsTo(squares[from], side)) {
            throw new IllegalArgumentException("The move must start on a piece of the current side");
        }

        if (CheckersMoveGenerator.hasStep(squares, side, true)) {
            char[] next = squares.clone();
            capture(next, side, route);
            squares = next;
            return;
        }

        int to = route.getLast();
        if (route.size() != 2 || !CheckersMoveGenerator.canSlide(squares, from, to, side)) {
            throw new IllegalArgumentException("Invalid checkers move");
        }
        relocate(squares, from, to, side);
    }

    boolean hasMove(CheckersSide side) {
        return CheckersMoveGenerator.hasStep(squares, side, false);
    }

    String position() {
        return new String(squares);
    }

    private static void capture(char[] position, CheckersSide side, List<Integer> route) {
        int from = route.getFirst();
        for (int to : route.subList(1, route.size())) {
            int victim = CheckersMoveGenerator.capturedSquare(position, from, to, side);
            if (victim == NO_STEP) {
                throw new IllegalArgumentException("Each step must capture an opposing piece");
            }
            position[victim] = CAPTURED;
            relocate(position, from, to, side);
            from = to;
        }

        if (CheckersMoveGenerator.canContinueCapture(position, from, side)) {
            throw new IllegalArgumentException("The capture sequence must be completed");
        }
        for (int square = 0; square < SQUARE_COUNT; square++) {
            if (position[square] == CAPTURED) {
                position[square] = EMPTY;
            }
        }
    }

    private static void relocate(char[] position, int from, int to, CheckersSide side) {
        int promotionRow = side == CheckersSide.WHITE ? 0 : SIZE - 1;
        position[to] = CheckersMoveGenerator.row(to) == promotionRow ? Character.toUpperCase(position[from]) : position[from];
        position[from] = EMPTY;
    }
}
