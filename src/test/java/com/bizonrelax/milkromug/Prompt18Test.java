package com.bizonrelax.milkromug;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Промпт-18: трассировка 400 и гигиена.
 *  (а) побайтовая строка замены обратного слэша в sanitizeText закрыта регрессией;
 *  (б) живой payload с \r, управляющими символами, кавычками, слэшами и U+2028 — 200 OK;
 *  (в) битый JSON -> дружелюбный 400 «Первые 200 символов» + файл milkromug-bad-payload-*.txt
 *      в temp-каталоге (свойство milkromug.bad-payload-dir), путь продублирован в теле ответа.
 */
@SpringBootTest
@AutoConfigureMockMvc
class Prompt18Test {

    @Autowired
    private MockMvc mockMvc;

    /** Шаг 1а: в исходнике букмарклета строка слэша побайтово ровно одна. */
    @Test
    void sanitizeTextBackslashLineIsByteExact() throws Exception {
        String src = Files.readString(
                Path.of("src/main/resources/static/bookmarklet.js"), StandardCharsets.UTF_8);
        // Побайтовый эталон: два обратных слэша внутри regex, без съеденного комментария.
        String needle = "s = s.replace(/\\\\/g, String.fromCharCode(0xFF3C));";
        assertThat(src).contains(needle);
        long count = Pattern.compile(Pattern.quote(needle), Pattern.LITERAL)
                .matcher(src).results().count();
        assertThat(count).isEqualTo(1);
    }

    /** Шаг 1б: экранированный payload со спецсимволами проходит на /chat как 200 OK. */
    @Test
    void escapedLiveChatPayloadReturnsOk() throws Exception {
        String body = "{\"title\":\"Живой чат \\u2028тест\",\"messages\":["
                + "{\"author\":\"user\",\"text\":\"строка1\\r\\n\""
                + ",\"html\":false},"
                + "{\"author\":\"assistant\",\"text\":\"кавычки \\\"внутри\\\" и слеши \\\\\\\\ "
                + "\\u0001\\u0002\\u001F управление \\u2028 line-sep\","
                + "\"html\":false}]}";
        mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.url").exists());
    }

    /** Шаг 1в: битый JSON -> 400 с причиной, первыми 200 символами и путём к файлу дампа. */
    @Test
    void brokenJsonDumpsBadPayloadFileAndReportsPath() throws Exception {
        Path dumpDir = Path.of(System.getProperty("java.io.tmpdir"));
        List<Path> before;
        try (var s = Files.list(dumpDir)) {
            before = s.filter(p -> p.getFileName().toString().startsWith("milkromug-bad-payload"))
                      .toList();
        }

        StringBuilder truncated = new StringBuilder(
                "{\"title\":\"Обрывок\", \"messages\":[{\"author\":\"user\",\"text\":\"незакрытая");
        for (int i = 0; i < 60; i++) {
            truncated.append(" длинное сообщение для проверки обрезки дампа ").append(i);
        }
        String body = truncated.toString();

        String responseBody = mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(responseBody)
                .contains("Причина:")
                .contains("Первые 200 символов")
                .contains("Файл с полным payload:");

        List<Path> after;
        try (var s = Files.list(dumpDir)) {
            after = s.filter(p -> p.getFileName().toString().startsWith("milkromug-bad-payload"))
                     .toList();
        }
        assertThat(after).hasSizeGreaterThan(before.size());
        Path newest = after.stream()
                .max(java.util.Comparator.comparingLong(p -> {
                    try { return Files.getLastModifiedTime(p).toMillis(); }
                    catch (Exception e) { return 0L; }
                }))
                .orElseThrow();
        String dumped = Files.readString(newest, StandardCharsets.UTF_8);
        assertThat(dumped).contains("Обрывок");
    }
}
