package com.bizonrelax.milkromug.config;

import java.awt.Desktop;
import java.net.URI;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * «Запустил и пользуешься»: после готовности сервера автоматически
 * открывает дашборд http://localhost:PORT/ в браузере по умолчанию.
 *
 * Охрана (guard):
 *  - headless-режим (нет GUI / java.awt.headless=true) — пропускаем;
 *  - аргумент командной строки --no-open — пропускаем;
 *  - любое исключение при открытии логируется, приложение НЕ падает.
 */
@Component
public class BrowserAutoOpen implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(BrowserAutoOpen.class);

    private final Environment environment;

    /**
     * Аргументы командной строки JVM недоступны напрямую из Spring-события,
     * поэтому проверяем системное свойство no-open (Spring Boot mapToSystemProperties)
     * и переменную окружения MILKROMUG_NO_OPEN как резервный вариант.
     */
    private boolean isNoOpenRequested() {
        if (Boolean.parseBoolean(System.getProperty("no-open", "false"))) {
            return true;
        }
        String env = System.getenv("MILKROMUG_NO_OPEN");
        return env != null && !env.isBlank() && !"0".equals(env);
    }

    public BrowserAutoOpen(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (Boolean.getBoolean("java.awt.headless")
                || !Desktop.isDesktopSupported()
                || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            log.info("Автооткрытие браузера пропущено: headless или нет Desktop.Browse.");
            return;
        }
        if (isNoOpenRequested()) {
            log.info("Автооткрытие браузера пропущено: передан аргумент --no-open.");
            return;
        }
        int port = environment.getProperty("server.port", Integer.class, 8080);
        String url = "http://localhost:" + port + "/";
        try {
            Desktop.getDesktop().browse(new URI(url));
            // Промпт-13: диагностика вместо молчания — печатаем результат в консоль.
            System.out.println("Browser opened: " + url);
            log.info("Дашборд открыт в браузере: {}", url);
        } catch (Exception e) {
            System.out.println("Could not open browser automatically - go to http://localhost:" + port);
            log.warn("Не удалось открыть браузер автоматически ({}). Откройте {} вручную.",
                    e.getMessage(), url);
        }
    }
}
