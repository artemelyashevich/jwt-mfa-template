package com.elyashevich.jwt_2fa_template.repository;

import com.elyashevich.jwt_2fa_template.entity.CheckersGame;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckersGameRepository extends JpaRepository<CheckersGame, UUID> {
}
