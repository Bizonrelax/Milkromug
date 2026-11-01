package com.bizonrelax.milkromug;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Промпт-12: экспорт сохранённого чата в четыре текстовых формата.
 * GET /preview/{id}?format=md|html|txt|json — файл с Content-Disposition: attachment.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ExportFormatsTest {

    /** Фикстура демо-чата: markdown, html-сообщение, plain-text. */
    private static final String DEMO_CHAT = """
            {
              "title": "Экспорт Демо",
              "messages": [
                { "author": "user", "text": "# Заголовок\\n\\nПривет **мир**!", "html": false },
                { "author": "assistant", "text": "<h2>Ответ</h2><ul><li>раз</li><li>два</li></ul>", "html": true },
                { "author": "user", "text": "Обычный текст без разметки.", "html": false }
              ]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    private String saveDemoChat() throws Exception {
        MvcResult result = mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(DEMO_CHAT))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    /* ---------- общий контракт для всех четырёх форматов ---------- */

    @Test
    void allFormatsReturnAttachmentWithNonEmptyBody() throws Exception {
        String id = saveDemoChat();
        for (String fmt : new String[]{"md", "html", "txt", "json"}) {
            MvcResult r = mockMvc.perform(get("/preview/" + id).param("format", fmt))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Disposition", containsString("attachment")))
                    .andReturn();
            assertThat(r.getResponse().getContentAsString())
                    .as("тело экспорта %s непустое", fmt)
                    .isNotBlank();
        }
    }

    /* ---------- markdown ---------- */

    @Test
    void markdownHasHashHeadersAndNoHtmlTags() throws Exception {
        String id = saveDemoChat();
        mockMvc.perform(get("/preview/" + id).param("format", "md"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(content().string(containsString("#")))
                .andExpect(content().string(containsString("Привет")))
                .andExpect(content().string(not(containsString("<"))));
    }

    /* ---------- standalone html ---------- */

    @Test
    void standaloneHtmlIsOfflineSelfContained() throws Exception {
        String id = saveDemoChat();
        MvcResult r = mockMvc.perform(get("/preview/" + id).param("format", "html"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(content().string(containsString("<!DOCTYPE html>")))
                .andReturn();
        String html = r.getResponse().getContentAsString();
        // весь css внутри файла, внешних сетевых ссылок нет
        assertThat(html).doesNotContain("http://").doesNotContain("https://");
        assertThat(html).doesNotContain("<link");
        assertThat(html).contains("<style");
        // контент чата на месте
        assertThat(html).contains("Обычный текст без разметки.");
    }

    /* ---------- plain text ---------- */

    @Test
    void txtHasNoMarkdownSymbols() throws Exception {
        String id = saveDemoChat();
        MvcResult r = mockMvc.perform(get("/preview/" + id).param("format", "txt"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andReturn();
        String txt = r.getResponse().getContentAsString();
        assertThat(txt).doesNotContain("#");
        assertThat(txt).doesNotContain("*");
        assertThat(txt).contains("Привет мир!");
        assertThat(txt).contains("Обычный текст без разметки.");
    }

    /* ---------- raw json ---------- */

    @Test
    void jsonContainsMessagesKey() throws Exception {
        String id = saveDemoChat();
        mockMvc.perform(get("/preview/" + id).param("format", "json"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(content().string(containsString("\"messages\"")))
                .andExpect(content().string(containsString("Экспорт Демо")));
    }

    /* ---------- кнопки на странице предпросмотра ---------- */

    @Test
    void previewPageOffersFourDownloadButtons() throws Exception {
        String id = saveDemoChat();
        MvcResult r = mockMvc.perform(get("/preview/" + id))
                .andExpect(status().isOk())
                .andReturn();
        long count = r.getResponse().getContentAsString().lines()
                .filter(line -> line.contains("format="))
                .count();
        assertThat(count).as("на странице не меньше четырёх ссылок format=").isGreaterThanOrEqualTo(4);
    }

    /* ---------- регрессия: без параметра format поведение прежнее ---------- */

    @Test
    void previewWithoutFormatStillRendersPage() throws Exception {
        String id = saveDemoChat();
        mockMvc.perform(get("/preview/" + id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("chatPreview")));
    }
}
