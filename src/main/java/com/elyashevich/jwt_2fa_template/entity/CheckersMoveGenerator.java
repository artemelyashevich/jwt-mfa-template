package com.elyashevich.jwt_2fa_template.entity;

final class CheckersMoveGenerator {

    static final int SIZE = 8;
    static final int SQUARE_COUNT = SIZE * SIZE / 2;
    static final int NO_STEP = -2;
    static final char EMPTY = '.';
    static final char CAPTURED = 'x';

    private static final int SQUARES_PER_ROW = SIZE / 2;
    private static final int ANY_DESTINATION = -1;
    private static final int NO_CAPTURE = -1;

    private CheckersMoveGenerator() {
    }

    static boolean hasStep(char[] squares, CheckersSide side, boolean capturesOnly) {
        for (int from = 0; from < SQUARE_COUNT; from++) {
            if (belongsTo(squares[from], side) && findStep(squares, from, side, ANY_DESTINATION, capturesOnly) != NO_STEP) {
                return true;
            }
        }
        return false;
    }

    static boolean canSlide(char[] squares, int from, int to, CheckersSide side) {
        return findStep(squares, from, side, to, false) == NO_CAPTURE;
    }

    static int capturedSquare(char[] squares, int from, int to, CheckersSide side) {
        return findStep(squares, from, side, to, true);
    }

    static boolean canContinueCapture(char[] squares, int from, CheckersSide side) {
        return capturedSquare(squares, from, ANY_DESTINATION, side) != NO_STEP;
    }

    private static int findStep(char[] squares, int from, CheckersSide side, int destination, boolean capturesOnly) {
        boolean king = Character.isUpperCase(squares[from]);
        int fromRow = row(from);
        int fromColumn = column(from);
        int forward = side == CheckersSide.WHITE ? -1 : 1;
        int maxDistance = king ? SIZE - 1 : 2;

        for (int rowDelta = -1; rowDelta <= 1; rowDelta += 2) {
            for (int columnDelta = -1; columnDelta <= 1; columnDelta += 2) {
                int victim = NO_CAPTURE;
                for (int distance = 1; distance <= maxDistance; distance++) {
                    int row = fromRow + rowDelta * distance;
                    int column = fromColumn + columnDelta * distance;
                    if (row < 0 || row >= SIZE || column < 0 || column >= SIZE) {
                        break;
                    }

                    int square = row * SQUARES_PER_ROW + column / 2;
                    char occupant = squares[square];
                    if (occupant == EMPTY) {
                        boolean canLand = victim != NO_CAPTURE || (!capturesOnly && (king || rowDelta == forward));
                        if (canLand && (destination == ANY_DESTINATION || destination == square)) {
                            return victim;
                        }
                        if (!king || destination == square) {
                            break;
                        }
                    } else {
                        if (victim != NO_CAPTURE || occupant == CAPTURED || belongsTo(occupant, side)) {
                            break;
                        }
                        victim = square;
                    }
                }
            }
        }
        return NO_STEP;
    }

    static boolean belongsTo(char piece, CheckersSide side) {
        return Character.toLowerCase(piece) == (side == CheckersSide.WHITE ? 'w' : 'b');
    }

    static int row(int square) {
        return square / SQUARES_PER_ROW;
    }

    private static int column(int square) {
        return 2 * (square % SQUARES_PER_ROW) + (1 - row(square) % 2);
    }
}
