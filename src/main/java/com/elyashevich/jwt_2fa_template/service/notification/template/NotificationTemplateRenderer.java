package com.elyashevich.jwt_2fa_template.service.notification.template;

import com.elyashevich.jwt_2fa_template.configuration.ApplicationProperties;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.AbstractConfigurableTemplateResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.FileTemplateResolver;

@Component
public class NotificationTemplateRenderer {

    public static final String SUBJECT_FRAGMENT = "subject";
    public static final String BODY_FRAGMENT = "body";

    private static final String EMAIL_LAYOUT = "email-layout.html";

    private final SpringTemplateEngine engine;

    public NotificationTemplateRenderer(MessageSource messageSource, ApplicationProperties applicationProperties) {
        TemplateLocation location = TemplateLocation.parse(applicationProperties.getNotification().getTemplatesLocation());
        AbstractConfigurableTemplateResolver resolver = location.file()
            ? new FileTemplateResolver()
            : new ClassLoaderTemplateResolver();
        resolver.setPrefix(location.path());
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setCacheable(true);
        engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        engine.setTemplateEngineMessageSource(messageSource);
    }

    public void clearCache() {
        engine.clearTemplateCache();
    }

    public String renderFragment(String templateName, String fragment, Map<String, Object> variables, Locale locale) {
        return engine.process(templateName, Set.of(fragment), new Context(locale, variables)).strip();
    }

    public String renderEmail(String templateName, String subject, Map<String, Object> variables, Locale locale) {
        Map<String, Object> layoutVariables = new HashMap<>(variables);
        layoutVariables.put("content", renderFragment(templateName, BODY_FRAGMENT, variables, locale));
        layoutVariables.put("subject", subject);
        return engine.process(EMAIL_LAYOUT, new Context(locale, layoutVariables));
    }
}
