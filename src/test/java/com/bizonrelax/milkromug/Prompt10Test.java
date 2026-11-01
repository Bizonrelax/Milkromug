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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Промт-10: desktop-shortcut, markdown-расширения + hljs, регрессии поля
 * (aimode junk, qwen order).
 */
@SpringBootTest
@AutoConfigureMockMvc
class Prompt10Test {

    @Autowired
    private MockMvc mockMvc;

    /* ---------- Шаг 7: POST /desktop-shortcut ---------- */

    @Test
    void desktopShortcutReturnsFriendlyJsonOnLinux() throws Exception {
        // Linux/headless CI: дружелюбный ok:false без 500.
        mockMvc.perform(post("/desktop-shortcut"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.ok").exists())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.error").isString());
    }

    @Test
    void bookmarkletPageHasShortcutButton() throws Exception {
        mockMvc.perform(get("/bookmarklet"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("desktop-shortcut")))
                .andExpect(content().string(containsString("CTRL+SHIFT+G")));
    }

    /* ---------- Шаг 8: markdown-расширения и подсветка ---------- */

    private String postChat(String json) throws Exception {
        MvcResult result = mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        // извлекаем id из {"id":"...","url":"..."}
        return body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    @Test
    void markdownTablesAndCodeAreRendered() throws Exception {
        String md = """
                Привет, таблица:

                | Язык | Пример |
                | ---- | ------ |
                | php  | код    |

                ```php
                <?php echo "hi"; ?>
                ```

                ```python
                print("hello")
                ```
                """;
        String json = "{\"title\":\"md-ext\",\"messages\":[{\"author\":\"assistant\",\"text\":"
                + new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(md)
                + ",\"html\":false}]}";
        String id = postChat(json);
        String preview = mockMvc.perform(get("/preview/" + id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(preview).contains("<table");
        assertThat(preview).contains("language-php");
        assertThat(preview).contains("language-python");
        assertThat(preview).contains("highlight.min.js"); // офлайн hljs подключён
        assertThat(preview).contains("hljs.highlightAll");
        assertThat(preview).doesNotContain("cdn.");        // запрет CDN
    }

    /* ---------- Шаг 9: регрессии поля ---------- */

    @Test
    void aimodeJunkDoesNotLeakScriptText() throws Exception {
        // Фикстура aimode-junk: внутри html-сообщения затёк script со служебным текстом.
        String dirtyHtml = "<div><p>Полезный ответ про кружку.</p>"
                + "<script>window.google.sn='aimode'; var junk='google.sn=aimode';</script>"
                + "<style>.x{color:red}</style></div>";
        String json = "{\"title\":\"aimode-junk\",\"messages\":[{\"author\":\"assistant\",\"html\":true,\"text\":"
                + com.fasterxml.jackson.databind.ObjectMapper.class.getDeclaredConstructor().newInstance()
                        .writeValueAsString(dirtyHtml)
                + "}]}";
        String id = postChat(json);
        String preview = mockMvc.perform(get("/preview/" + id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        // Отсекаем служебные <script>/<style> страницы-каркаса preview
        // (идут после </main>), чтобы assert касался только захваченного контента.
        int contentStart = preview.indexOf("<main");
        int contentEnd = preview.indexOf("</main>");
        String content = (contentStart >= 0 && contentEnd > contentStart)
                ? preview.substring(contentStart, contentEnd) : preview;
        assertThat(content).doesNotContain("google.sn=");
        assertThat(content).doesNotContain("<script>");
        assertThat(preview).contains("Полезный ответ про кружку");
    }

    @Test
    void qwenRoleOrderIsPreserved() throws Exception {
        // Фикстура qwen-order: чередование user/assistant должно сохраниться.
        String json = """
                {"title":"qwen-order","messages":[
                  {"author":"user","text":"вопрос 1","html":false},
                  {"author":"assistant","text":"ответ 1","html":false},
                  {"author":"user","text":"вопрос 2","html":false},
                  {"author":"assistant","text":"ответ 2","html":false}
                ]}""";
        String id = postChat(json);
        String preview = mockMvc.perform(get("/preview/" + id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int i1 = preview.indexOf("вопрос 1");
        int i2 = preview.indexOf("ответ 1");
        int i3 = preview.indexOf("вопрос 2");
        int i4 = preview.indexOf("ответ 2");
        assertThat(i1).isGreaterThanOrEqualTo(0);
        assertThat(i1).isLessThan(i2);
        assertThat(i2).isLessThan(i3);
        assertThat(i3).isLessThan(i4);
    }
}
