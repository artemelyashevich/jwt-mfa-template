package com.elyashevich.jwt_2fa_template.service;

import com.elyashevich.jwt_2fa_template.dto.checkers.CheckersGameSnapshot;
import java.util.List;
import java.util.UUID;

public interface CheckersGameService {

    CheckersGameSnapshot create(long playerId);

    CheckersGameSnapshot get(UUID gameId, long playerId);

    CheckersGameSnapshot quickMatch(long playerId);

    CheckersGameSnapshot cancelQuickMatch(UUID gameId, long playerId);

    CheckersGameSnapshot join(UUID gameId, long playerId);

    CheckersGameSnapshot makeMove(UUID gameId, long playerId, long expectedVersion, List<Integer> route);
}
