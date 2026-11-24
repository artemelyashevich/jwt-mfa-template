package com.elyashevich.jwt_2fa_template.entity;

import java.util.List;

final class CheckersBoard {

    private static final int SIZE = 8;
    private static final int SQUARE_COUNT = 32;
    private static final int MAX_ROUTE_LENGTH = 13;
    private static final char EMPTY = '.';

    private final char[] squares;
    private final boolean[] captured = new boolean[SQUARE_COUNT];

    CheckersBoard(String position) {
        squares = position.toCharArray();
    }

    void move(CheckersSide side, List<Integer> route) {
        if (route == null || route.size() < 2 || route.size() > MAX_ROUTE_LENGTH || route.stream().anyMatch(square -> square == null || square < 0 || square >= SQUARE_COUNT)) {
            throw new IllegalArgumentException("Invalid checkers move route");
        }

        int from = route.getFirst();
        if (!belongsTo(squares[from], side)) {
            throw new IllegalArgumentException("The move must start on a piece of the current side");
        }

        if (!canCapture(side)) {
            int to = route.getLast();
            if (route.size() != 2 || !canSlide(from, to, side)) {
                throw new IllegalArgumentException("Invalid checkers move");
            }
            relocate(from, to, side);
            return;
        }

        for (int to : route.subList(1, route.size())) {
            int victim = captureBetween(from, to);
            if (victim < 0) {
                throw new IllegalArgumentException("Each step must capture an opposing piece");
            }
            captured[victim] = true;
            relocate(from, to, side);
            from = to;
        }

        if (canCaptureFrom(from)) {
            throw new IllegalArgumentException("The capture sequence must be completed");
        }
        for (int square = 0; square < SQUARE_COUNT; square++) {
            if (captured[square]) {
                squares[square] = EMPTY;
                captured[square] = false;
            }
        }
    }

    boolean hasMove(CheckersSide side) {
        if (canCapture(side)) {
            return true;
        }
        for (int from = 0; from < SQUARE_COUNT; from++) {
            if (belongsTo(squares[from], side)) {
                for (int to = 0; to < SQUARE_COUNT; to++) {
                    if (canSlide(from, to, side)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    String position() {
        return new String(squares);
    }

    private boolean canCapture(CheckersSide side) {
        for (int from = 0; from < SQUARE_COUNT; from++) {
            if (belongsTo(squares[from], side) && canCaptureFrom(from)) {
                return true;
            }
        }
        return false;
    }

    private boolean canCaptureFrom(int from) {
        for (int to = 0; to < SQUARE_COUNT; to++) {
            if (captureBetween(from, to) >= 0) {
                return true;
            }
        }
        return false;
    }

    private int captureBetween(int from, int to) {
        if (!isOpenDiagonal(from, to)) {
            return -1;
        }
        int distance = Math.abs(row(to) - row(from));
        char piece = squares[from];
        if (distance < 2 || (!Character.isUpperCase(piece) && distance != 2)) {
            return -1;
        }

        int victim = -1;
        for (int step = 1; step < distance; step++) {
            int square = intermediateSquare(from, to, step);
            char occupant = squares[square];
            if (occupant == EMPTY) {
                continue;
            }
            if (victim >= 0 || captured[square] || Character.toLowerCase(occupant) == Character.toLowerCase(piece)) {
                return -1;
            }
            victim = square;
        }
        return victim;
    }

    private boolean canSlide(int from, int to, CheckersSide side) {
        if (!isOpenDiagonal(from, to)) {
            return false;
        }
        int rowDelta = row(to) - row(from);
        if (!Character.isUpperCase(squares[from])) {
            return rowDelta == (side == CheckersSide.WHITE ? -1 : 1);
        }

        for (int step = 1; step < Math.abs(rowDelta); step++) {
            if (squares[intermediateSquare(from, to, step)] != EMPTY) {
                return false;
            }
        }
        return true;
    }

    private boolean isOpenDiagonal(int from, int to) {
        return from != to && squares[to] == EMPTY
                && Math.abs(row(to) - row(from)) == Math.abs(column(to) - column(from));
    }

    private void relocate(int from, int to, CheckersSide side) {
        int promotionRow = side == CheckersSide.WHITE ? 0 : SIZE - 1;
        squares[to] = row(to) == promotionRow ? Character.toUpperCase(squares[from]) : squares[from];
        squares[from] = EMPTY;
    }

    private static boolean belongsTo(char piece, CheckersSide side) {
        return Character.toLowerCase(piece) == (side == CheckersSide.WHITE ? 'w' : 'b');
    }

    private static int intermediateSquare(int from, int to, int step) {
        int row = row(from) + step * Integer.signum(row(to) - row(from));
        int column = column(from) + step * Integer.signum(column(to) - column(from));
        return row * (SIZE / 2) + column / 2;
    }

    private static int row(int square) {
        return square / (SIZE / 2);
    }

    private static int column(int square) {
        return 2 * (square % (SIZE / 2)) + (1 - row(square) % 2);
    }
}
