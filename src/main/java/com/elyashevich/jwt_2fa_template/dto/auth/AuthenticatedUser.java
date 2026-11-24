package com.elyashevich.jwt_2fa_template.dto.auth;

import com.elyashevich.jwt_2fa_template.dto.cache.UserSessionCache;
import java.util.Collection;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

@Getter
public class AuthenticatedUser extends User {

    private final transient UserSessionCache session;

    public AuthenticatedUser(UserSessionCache session, Collection<? extends GrantedAuthority> authorities) {
        super(session.getEmail(), "", authorities);
        this.session = session;
    }
}