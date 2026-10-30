package com.bizonrelax.milkromug.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS для эндпоинтов, которые вызывает букмарклет со сторонних https-сайтов
 * (chat.qwen.ai, chatgpt.com, gemini.google.com и т.п.):
 *   - POST /chat       — захват чата из браузера;
 *   - GET  /preview/** — предпросмотр сохранённого чата.
 * Разрешаем любой источник (Access-Control-Allow-Origin: *) — сервер локальный,
 * секретов не хранит, а букмарклету нужен простой доступ без preflight-сложностей.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/chat")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*");
        registry.addMapping("/preview/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "OPTIONS")
                .allowedHeaders("*");
    }
}
