package com.bizonrelax.milkromug;

import com.bizonrelax.milkromug.controller.BookmarkletSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Промпт-13: ГОТОВНОСТЬ — aimode-фикстура без мусора в предпросмотре,
 * лоадер-букмарклет на /bookmarklet, CORS * и no-store на /bookmarklet.js,
 * страница /glossary с обязательными словами.
 */
@SpringBootTest
@AutoConfigureMockMvc
class Prompt13Test {

    @Autowired
    private MockMvc mockMvc;

    /** POST фикстуры aimode-share.json -> preview без CSS/JS-мусора, с первой репликой. */
    @Test
    void aimodeFixturePreviewIsClean() throws Exception {
        String fixture = new String(
                java.nio.file.Files.readAllBytes(
                        java.nio.file.Path.of("docs/prompts/fixtures/aimode-share.json")),
                java.nio.charset.StandardCharsets.UTF_8);
        String body = mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String id = body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
        // id может содержать [[...]] — для MockMvc это синтаксис uri template
        // variables, поэтому экранируем квадратные скобки percent-encoding'ом.
        String encodedId = java.net.URLEncoder.encode(id, java.nio.charset.StandardCharsets.UTF_8)
                .replace("[", "%5B").replace("]", "%5D");
        String preview = mockMvc.perform(get("/preview/" + encodedId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(preview).doesNotContain("window._hst");
        assertThat(preview).doesNotContain(".fd1gae{");
        assertThat(preview).contains("DELL Laptop Warframe Performance - Google Search");
    }

    /** /bookmarklet отдаёт лоадер: href кнопки начинается с javascript: и содержит fetch(...bookmarklet.js...). */
    @Test
    void bookmarkletPageServesLoader() throws Exception {
        String page = mockMvc.perform(get("/bookmarklet"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(page).contains("loader");
        // href проходит через Thymeleaf-экранирование, поэтому " -> &quot;
        assertThat(page).contains("fetch(&quot;http://localhost:8080/bookmarklet.js?v=");
        // кнопка — javascript:-ссылка
        assertThat(page).contains("javascript:");
        // аварийный полный однострочник лежит в details
        assertThat(page).contains("Аварийный вариант");
    }

    /** Лоадерная строка: javascript:, fetch на bookmarklet.js, без eval-инъекций лишних. */
    @Test
    void loaderHrefShape() {
        String href = BookmarkletSource.buildLoaderHref();
        assertThat(href).startsWith("javascript:");
        assertThat(href).contains("fetch(\"http://localhost:8080/bookmarklet.js?v=");
    }

    /** /bookmarklet.js: Access-Control-Allow-Origin: * и Cache-Control: no-store. */
    @Test
    void bookmarkletJsHasCorsAndNoStore() throws Exception {
        mockMvc.perform(get("/bookmarklet.js")
                        .header("Origin", "https://share.google"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    }

    /** /glossary: обязательные термины и браузеры. */
    @Test
    void glossaryCoversTermsAndBrowsers() throws Exception {
        String ru = mockMvc.perform(get("/glossary").param("lang", "ru"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String lower = ru.toLowerCase();
        assertThat(count(lower, "букмарклет")).isGreaterThanOrEqualTo(3);
        assertThat(count(lower, "санитиз")).isGreaterThanOrEqualTo(2);
        assertThat(count(lower, "репозитор")).isGreaterThanOrEqualTo(2);
        assertThat(count(lower, "этимологи")).isGreaterThanOrEqualTo(1);
        for (String browser : new String[]{"Chrome", "Edge", "Firefox", "Safari"}) {
            assertThat(ru).contains(browser);
        }
        // EN-зеркало тоже живое
        mockMvc.perform(get("/glossary").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsStringIgnoringCase("bookmarklet")));
    }

    private static int count(String haystack, String needle) {
        int c = 0, i = 0;
        while ((i = haystack.indexOf(needle, i)) >= 0) {
            c++;
            i += needle.length();
        }
        return c;
    }
}
