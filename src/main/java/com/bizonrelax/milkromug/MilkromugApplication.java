package com.bizonrelax.milkromug;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Точка входа Milkromug.
 * Запускает локальное Spring Boot приложение (порт 8080 по умолчанию).
 */
@SpringBootApplication
public class MilkromugApplication {

    public static void main(String[] args) {
        // Если порт уже занят (второй экземпляр), печатаем понятную подсказку
        // и выходим с кодом 0 — до старта Spring Boot и без stacktrace.
        PortGuard.ensurePortAvailable(PortGuard.resolvePort(args));
        SpringApplication app = new SpringApplication(MilkromugApplication.class);
        // Четыре ASCII-экспоната коллекции подряд при старте (banner.txt + Gwen's).
        app.setBanner(new MilkromugBanner());
        app.run(args);
    }
}
