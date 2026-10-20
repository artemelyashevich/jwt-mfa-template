package com.elyashevich.jwt_2fa_template.service;

import com.elyashevich.jwt_2fa_template.dto.auth.JwtType;
import com.elyashevich.jwt_2fa_template.entity.UserProfile;
import java.util.Date;
import java.util.UUID;

public interface JwtService {
    String generate(UserProfile user, JwtType jwtType);

    Long extractUserId(String token, JwtType jwtType);

    Date extractExpiration(String token, JwtType jwtType);
}
