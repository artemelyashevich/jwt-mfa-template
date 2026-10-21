package com.elyashevich.jwt_2fa_template.configuration.security;

import com.elyashevich.jwt_2fa_template.dto.exception.ExceptionBody;
import com.elyashevich.jwt_2fa_template.exception.AppExceptionCode;
import com.elyashevich.jwt_2fa_template.service.I18nService;
import com.elyashevich.jwt_2fa_template.util.JsonHelper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecurityExceptionResponseWriter {

    private final I18nService i18nService;

    public void write(HttpServletResponse response, AppExceptionCode code) throws IOException {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("errorCode", String.valueOf(code.getCode()));
        details.put("error", code.getLabel());

        ExceptionBody body = ExceptionBody.builder()
            .message(i18nService.getErrorMessage(code.getLabel()))
            .details(details)
            .build();

        response.setStatus(code.getCode());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(JsonHelper.mapper.writeValueAsString(body));
    }
}
