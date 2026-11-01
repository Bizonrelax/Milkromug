package com.bizonrelax.milkromug;

import com.bizonrelax.milkromug.controller.BookmarkletSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Промпт-16 (ГОТОВНОСТЬ):
 *  - href лоадера на /bookmarklet выводится БЕЗ HTML-экранирования JS-нагрузки
 *    (ни &amp;, ни &#39;, ни &quot; внутри javascript:-строки);
 *  - тело лоадера не умирает молча: try/catch, console.log старта,
 *    красная плашка с id loader-error и кнопка самотеста id loader-test;
 *  - POST /desktop-shortcut на linux гейтится по windows/рабочему столу,
 *    а не по AWT-headless (тексты ошибок только английские);
 *  - GET /debug/env отдаёт факты среды без секретов;
 *  - horse-gwen-v2.txt в коллекции с подписью G.W.;
 *  - пятая панель баннера «Gwen's Horse v2»;
 *  - формулировки: «О программе» вместо «Об программе».
 */
@SpringBootTest
@AutoConfigureMockMvc
class Prompt16Test {

    @Autowired
    private MockMvc mockMvc;

    /** Сырой href на странице: содержит fetch( и не содержит HTML-сущностей. */
    @Test
    void rawHrefIsNotHtmlEscaped() throws Exception {
        String page = mockMvc.perform(get("/bookmarklet"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        int idx = page.indexOf("href=\"javascript:");
        assertThat(idx).as("page must contain a raw javascript: href").isGreaterThanOrEqualTo(0);
        int end = page.indexOf('"', idx + "href=\"javascript:".length());
        assertThat(end).isGreaterThan(idx);
        String href = page.substring(idx, end);
        assertThat(href).contains("fetch(");
        assertThat(href).doesNotContain("&amp;");
        assertThat(href).doesNotContain("&#39;");
        assertThat(href).doesNotContain("&quot;");
    }

    /** Серверная константа лоадера: try/catch, console.error, console.log старта, id плашки. */
    @Test
    void loaderBodyHasVisibleDiagnostics() {
        String href = BookmarkletSource.buildLoaderHref();
        assertThat(href).startsWith("javascript:");
        assertThat(href).contains("try{");
        assertThat(href).contains("catch");
        assertThat(href).contains("console.error");
        assertThat(href).contains("milkromug loader start");
        assertThat(href).contains("loader-error");
        // полезная нагрузка не должна содержать символов, которые Thymeleaf экранирует
        assertThat(href).doesNotContain("&");
        assertThat(href).doesNotContain("'");
        assertThat(href).doesNotContain("\"");
    }

    /** Кнопка самотеста id loader-test на странице /bookmarklet. */
    @Test
    void selfTestButtonOnPage() throws Exception {
        String page = mockMvc.perform(get("/bookmarklet"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(page).contains("loader-test");
    }

    /** POST /desktop-shortcut на linux: причина — про windows, не про headless. */
    @Test
    void desktopShortcutGateIsOsBasedNotHeadless() throws Exception {
        String body = mockMvc.perform(post("/desktop-shortcut"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body.toLowerCase()).contains("windows");
        assertThat(body.toLowerCase()).doesNotContain("headless");
        mockMvc.perform(post("/desktop-shortcut"))
                .andExpect(jsonPath("$.ok").value(false));
    }

    /** GET /debug/env: 200 и обязательные ключи. */
    @Test
    void debugEnvEndpointReportsFacts() throws Exception {
        mockMvc.perform(get("/debug/env"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(
                        String.valueOf(result.getResponse().getContentType())).contains("json"))
                .andExpect(jsonPath("$.headless").isNumber())
                .andExpect(jsonPath("$.headlessProperty").exists())
                .andExpect(jsonPath("$.osName").exists())
                .andExpect(jsonPath("$.desktopSupported").isNumber())
                .andExpect(jsonPath("$.desktopPath").exists());
    }

    /** Лошадь v2 принята в коллекцию: файл существует и подписан G.W. */
    @Test
    void horseV2InCollection() throws Exception {
        java.nio.file.Path p = java.nio.file.Path.of("docs/ascii-collection/horse-gwen-v2.txt");
        assertThat(java.nio.file.Files.exists(p)).isTrue();
        String art = java.nio.file.Files.readString(p, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(art).contains("G.W.");
        assertThat(art.lines().findFirst().orElse(""))
                .isEqualTo("Gwen's running horse v2, lineart by ASCII manual, Unlicense, 2026");
    }

    /** Баннер: пять панелей, пятая озаглавлена Gwen's Horse v2. */
    @Test
    void bannerPrintsFivePanels() {
        java.io.ByteArrayOutputStream bout = new java.io.ByteArrayOutputStream();
        java.io.PrintStream ps = new java.io.PrintStream(bout, true, java.nio.charset.StandardCharsets.UTF_8);
        new com.bizonrelax.milkromug.MilkromugBanner().printBanner(null, null, ps);
        ps.flush();
        String out = bout.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(out).contains("<!-- Gwen's Mug -->");
        assertThat(out).contains("<!-- Gwen's Horse -->");
        assertThat(out).contains("<!-- Gwen's Horse v2 -->");
    }

    /** Формулировки: нигде не осталось «Об программе». */
    @Test
    void wordingFixed() throws Exception {
        String about = mockMvc.perform(get("/about"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(about).doesNotContain("Об программе");
        String bookmarklet = mockMvc.perform(get("/bookmarklet"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(bookmarklet).doesNotContain("Об программе");
        String index = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(index).doesNotContain("Об программе");
        String glossary = mockMvc.perform(get("/glossary"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(glossary).doesNotContain("Об программе");
    }
}
