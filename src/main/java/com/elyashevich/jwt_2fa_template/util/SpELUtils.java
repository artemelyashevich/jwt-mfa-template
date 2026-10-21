package com.elyashevich.jwt_2fa_template.util;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SpELUtils {

    private static final Set<String> SPEL_EXPRESSIONS = Set.of(
        "#", "T(", "'", "."
    );

    private final BeanFactory beanFactory;
    private final ExpressionParser parser = new SpelExpressionParser();

    public StandardEvaluationContext createContext(ProceedingJoinPoint pjp) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        StandardEvaluationContext context = new StandardEvaluationContext();
        String[] paramNames = signature.getParameterNames();
        Object[] args = pjp.getArgs();
        for (int i = 0; i < paramNames.length; i++) {
            context.setVariable(paramNames[i], args[i]);
        }
        context.setBeanResolver(new BeanFactoryResolver(beanFactory));
        return context;
    }

    public <T> T evaluate(String expression, EvaluationContext context, Class<T> type) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        if (!isSpelExpression(expression)) {
            return convertLiteral(expression, type);
        }
        return parser.parseExpression(expression).getValue(context, type);
    }

    private boolean isSpelExpression(String expression) {
        return SPEL_EXPRESSIONS.stream().anyMatch(expression::contains);
    }

    @SuppressWarnings("unchecked")
    private <T> T convertLiteral(String value, Class<T> type) {
        if (type == String.class) {
            return (T) value;
        }
        if (type == Boolean.class) {
            return (T) Boolean.valueOf(value);
        }
        if (type == Long.class) {
            return (T) Long.valueOf(value);
        }
        throw new IllegalArgumentException("Unsupported literal type: " + type);
    }
}