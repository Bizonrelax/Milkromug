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
        SpringApplication.run(MilkromugApplication.class, args);
    }
}
