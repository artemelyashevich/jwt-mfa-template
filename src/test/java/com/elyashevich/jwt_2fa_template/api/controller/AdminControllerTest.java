package com.elyashevich.jwt_2fa_template.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.elyashevich.jwt_2fa_template.dto.user.CurrentUserResponse;
import com.elyashevich.jwt_2fa_template.entity.UserProfile;
import com.elyashevich.jwt_2fa_template.service.UserService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(AdminControllerTest.Config.class)
class AdminControllerTest {

    @Autowired
    private AdminController adminController;
    @MockitoBean
    private UserService userService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanSearchUsersByEmail() {
        when(userService.searchByEmail("example")).thenReturn(List.of(
            UserProfile.builder().id(42L).email("user@example.com").build()));

        assertThat(adminController.searchUsers(" example "))
            .extracting(CurrentUserResponse::email)
            .containsExactly("user@example.com");
    }

    @Test
    @WithMockUser(roles = {"USER", "MODERATOR"})
    void nonAdminIsDenied() {
        assertThatThrownBy(() -> adminController.searchUsers("example")).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(userService);
    }

    @Test
    void anonymousIsDenied() {
        assertThatThrownBy(() -> adminController.searchUsers("example"))
            .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        verifyNoInteractions(userService);
    }

    @Configuration
    @EnableMethodSecurity
    @Import(AdminController.class)
    static class Config {
    }
}
