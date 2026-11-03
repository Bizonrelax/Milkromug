package com.bizonrelax.milkromug;

import com.bizonrelax.milkromug.controller.BookmarkletSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Промпт-17: починить HTTP 400 при захвате чатов DeepSeek, Google AI и длинных чатов.
 *  - огромный JSON (~5 МБ) с кучей спецсимволов и кавычек проходит на /chat как 200 OK;
 *  - битый JSON больше не отдаёт стектрейс: ответ — 400 с понятным текстом ошибки;
 *  - лимиты в application.properties подняты (Tomcat 10MB, Jackson max-string-length);
 *  - букмарклет содержит эвристику повторяющихся блоков и клиентскую санитизацию.
 */
@SpringBootTest
@AutoConfigureMockMvc
class Prompt17Http400Test {

    @Autowired
    private MockMvc mockMvc;

    /** Собираем payload примерно в 5 МБ: много \n, \r, ", \, unicode-мусор. */
    private static String hugePayload() {
        StringBuilder chunk = new StringBuilder();
        // Спецсимволы в JSON-строковом виде: \n \r \" \\ и юникод-эскейпы.
        chunk.append("Привет!\\n\"Кавычки\" и \\\\ слеши \\r\\n ");
        chunk.append("\\u003Cscript\\u003E <b>html</b> \\t таб `code` # ** ~~ ").append('™');
        for (int i = 0; i < 60; i++) {
            chunk.append("слово").append(i).append(' ');
        }
        String unit = chunk.toString();
        StringBuilder sb = new StringBuilder(6_000_000);
        sb.append("{\"title\":\"Огромный чат \\u2116 1\",\"messages\":[");
        int perMessage = 5000;              // ~5k символов на сообщение
        int messages = unit.isEmpty() ? 0 : (5 * 1024 * 1024) / perMessage + 1; // ~5 МБ
        for (int m = 0; m < messages; m++) {
            if (m > 0) sb.append(',');
            sb.append("{\"author\":\"").append(m % 2 == 0 ? "user" : "assistant")
              .append("\",\"text\":\"");
            for (int r = 0; r < perMessage / unit.length() + 1 && sb.length() < 5 * 1024 * 1024 + perMessage; r++) {
                sb.append(unit);
            }
            sb.setLength(Math.min(sb.length(), sb.length())); // no-op, читаемость
            // обрезаем текст до аккуратной длины сообщения
            int start = sb.lastIndexOf("\"text\":\"") + "\"text\":\"".length();
            int targetLen = start + perMessage;
            if (sb.length() > targetLen) sb.setLength(targetLen);
            sb.append("\\n\",\"html\":false}");
        }
        sb.append("]}");
        return sb.toString();
    }

    /** Шаг 5: огромный JSON (5 МБ) со спецсимволами и кавычками — ожидание 200 OK. */
    @Test
    void hugeJsonWithSpecialCharsReturnsOk() throws Exception {
        String body = hugePayload();
        assertThat(body.length()).isGreaterThan(4 * 1024 * 1024); // гарантируем «огромность»
        // sanity: сам JSON разборный
        new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);

        mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.url").exists());
    }

    /** Шаг 1: битый JSON -> 400 с понятным текстом, без стектрейса. */
    @Test
    void brokenJsonReturnsFriendly400() throws Exception {
        mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"messages\":[{\"author\":\"user\",\"text\":\"обрыв"))
                .andExpect(status().isBadRequest())
                // text/plain без charset — браузер/клиент читает как UTF-8
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(responseBody).contains("повреждённый JSON").contains("Первые 200 символов");
    }

    /** internal helper placeholder */
    private static String unused(MockMvc m) {
        return "";
    }

    /** Шаг 1: невалидный символ в JSON -> тоже дружелюбный 400 (не 500 и не стектрейс). */
    @Test
    void malformedJsonWithoutStackTrace() throws Exception {
        mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{oops not json}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Exception"))));
    }

    /** Шаг 2: лимиты добавлены в application.properties. */
    @Test
    void raisedLimitsAreConfigured() throws Exception {
        String props = new String(java.nio.file.Files.readAllBytes(
                        java.nio.file.Path.of("src/main/resources/application.properties")),
                java.nio.charset.StandardCharsets.UTF_8);
        assertThat(props).contains("server.tomcat.max-http-form-post-size=10MB");
        assertThat(props).contains("spring.jackson.parser.max-string-length=10000000");
    }

    /** Шаги 3-4: букмарклет содержит эвристику чередующихся блоков и sanitizeText. */
    @Test
    void bookmarkletHasHeuristicAndSanitizer() {
        String src = BookmarkletSource.buildSource();
        assertThat(src).contains("sanitizeText");
        assertThat(src).contains("Эвристика");
        assertThat(src).contains("deepseek");
        // минифицированная однострочная версия остаётся синтаксически целой
        String minified = BookmarkletSource.buildMinifiedSource();
        assertThat(minified).doesNotContain("\n");
        assertThat(minified).contains("sanitizeText");
    }
}
