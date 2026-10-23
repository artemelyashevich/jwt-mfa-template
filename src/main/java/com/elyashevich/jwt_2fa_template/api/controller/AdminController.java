package com.elyashevich.jwt_2fa_template.api.controller;

import com.elyashevich.jwt_2fa_template.configuration.security.PublicUrls;
import com.elyashevich.jwt_2fa_template.dto.user.CurrentUserResponse;
import com.elyashevich.jwt_2fa_template.service.UserService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrative endpoints. Every method requires the {@code ADMIN} role; other authenticated users get 403.
 */
@RestController
@RequestMapping(PublicUrls.ADMIN_TAG)
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserService userService;

    /**
     * Searches users by a case-insensitive part of their email; results are sorted by email.
     */
    @GetMapping("/users")
    public List<CurrentUserResponse> searchUsers(
        @RequestParam
        @NotBlank(message = "Email не должен быть пустым")
        @Size(max = 320, message = "Email не должен превышать 320 символов")
        String email
    ) {
        return userService.searchByEmail(email.trim()).stream().map(CurrentUserResponse::from).toList();
    }
}
