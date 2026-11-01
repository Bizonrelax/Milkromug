package com.bizonrelax.milkromug.config;

import java.awt.Desktop;
import java.net.URI;
import java.util.List;

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
        if (event.getArgs().containsOption("no-open")
                || List.of(event.getArgs().getSourceArgs()).contains("--no-open")) {
            log.info("Автооткрытие браузера пропущено: передан аргумент --no-open.");
            return;
        }
        int port = environment.getProperty("server.port", Integer.class, 8080);
        String url = "http://localhost:" + port + "/";
        try {
            Desktop.getDesktop().browse(new URI(url));
            log.info("Дашборд открыт в браузере: {}", url);
        } catch (Exception e) {
            log.warn("Не удалось открыть браузер автоматически ({}). Откройте {} вручную.",
                    e.getMessage(), url);
        }
    }
}
