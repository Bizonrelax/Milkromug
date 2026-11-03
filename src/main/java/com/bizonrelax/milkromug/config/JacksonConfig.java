package com.bizonrelax.milkromug.config;

import com.fasterxml.jackson.core.StreamReadConstraints;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Промпт-17: лимит длины строки Jackson (StreamReadConstraints.maxStringLength).
 * Значение задаётся в application.properties (milkromug.jackson.max-string-length,
 * по умолчанию 10 000 000 символов), чтобы огромные чаты (5+ МБ) с длинными
 * сообщениями не отклонялись парсером.
 */
@Configuration
public class JacksonConfig {

    @Value("${milkromug.jackson.max-string-length:10000000}")
    private int maxStringLength;

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer milkromugStreamReadConstraints() {
        return builder -> builder.postConfigurer(mapper -> mapper
                .getFactory()
                .setStreamReadConstraints(StreamReadConstraints.builder()
                        .maxStringLength(maxStringLength)
                        .build()));
    }
}
