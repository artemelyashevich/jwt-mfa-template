package com.elyashevich.jwt_2fa_template.service.notification.template;

public record TemplateLocation(boolean file, String path) {

    private static final String FILE_PREFIX = "file:";
    private static final String CLASSPATH_ALL_PREFIX = "classpath*:";
    private static final String CLASSPATH_PREFIX = "classpath:";

    public static TemplateLocation parse(String location) {
        boolean file = location.startsWith(FILE_PREFIX);
        String path = file ? location.substring(FILE_PREFIX.length())
            : location.startsWith(CLASSPATH_ALL_PREFIX) ? location.substring(CLASSPATH_ALL_PREFIX.length())
            : location.startsWith(CLASSPATH_PREFIX) ? location.substring(CLASSPATH_PREFIX.length())
            : location;
        if (!file && path.startsWith("/")) {
            path = path.substring(1);
        }
        return new TemplateLocation(file, path.endsWith("/") ? path : path + "/");
    }

    public String scanPattern() {
        return (file ? FILE_PREFIX : CLASSPATH_ALL_PREFIX) + path + "*/*.html";
    }
}
