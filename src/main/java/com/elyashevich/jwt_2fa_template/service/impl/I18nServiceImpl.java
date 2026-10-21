package com.elyashevich.jwt_2fa_template.service.impl;

import com.elyashevich.jwt_2fa_template.dto.cache.UserSessionCache;
import com.elyashevich.jwt_2fa_template.entity.UserLocale;
import com.elyashevich.jwt_2fa_template.service.I18nService;
import com.elyashevich.jwt_2fa_template.service.UserSessionService;
import com.elyashevich.jwt_2fa_template.util.HttpHeaderUtil;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class I18nServiceImpl implements I18nService {

    private final HttpServletRequest httpServletRequest;
    private final MessageSource messageSource;
    private final UserSessionService userSessionService;

    @Override
    public String getErrorMessage(String errorKey) {
        return messageSource.getMessage("ERROR_" + errorKey, null, "Internal server error", resolveLocale());
    }

    private Locale resolveLocale() {
        return userSessionService.getCurrentUserSafety()
            .map(UserSessionCache::getLocale)
            .map(UserLocale::toLocale)
            .orElseGet(() -> HttpHeaderUtil.getLocale(httpServletRequest));
    }
}
