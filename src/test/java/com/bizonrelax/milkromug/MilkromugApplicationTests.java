package com.bizonrelax.milkromug;

import com.bizonrelax.milkromug.controller.BookmarkletSource;
import com.bizonrelax.milkromug.model.ChatRequest;
import com.bizonrelax.milkromug.service.ChatStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MilkromugApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ChatStore chatStore;

    @Test
    void contextLoads() {
        // Проверяет, что Spring-контекст собирается без ошибок
    }

    @Test
    void indexShowsHowToUse() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Как пользоваться")))
                .andExpect(content().string(containsString("Milkromug Capture")));
    }

    @Test
    void bookmarkletPageShowsDraggableButtonAndMinifiedCode() throws Exception {
        MvcResult result = mockMvc.perform(get("/bookmarklet"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("javascript:")))
                .andReturn();
        String page = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(page).contains("Milkromug Capture");
        // textarea с минифицированной однострочной версией
        assertThat(page).contains("<textarea");
    }

    @Test
    void postChatReturnsJsonWithIdAndPreviewUrl() throws Exception {
        String body = """
                {"title":"Тестовый чат","messages":[
                  {"author":"user","text":"Привет!","html":false},
                  {"author":"assistant","text":"<p>Здравствуйте!</p><script>alert(1)</script>","html":true}
                ]}
                """;
        MvcResult result = mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .header("Origin", "https://chat.qwen.ai")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        assertThat(json).contains("\"id\"").contains("http://localhost:8080/preview/");
        String id = json.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        // Предпросмотр сохранённого чата рендерит сообщения и кнопку печати
        mockMvc.perform(get("/preview/" + id).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Тестовый чат")))
                .andExpect(content().string(containsString("Привет!")))
                .andExpect(content().string(containsString("Сохранить в PDF")));

        // script из html-сообщения вырезан санитайзером jsoup
        mockMvc.perform(get("/preview/" + id))
                .andExpect(content().string(not(containsString("alert(1)"))));

        // ?font=N работает на предпросмотре
        mockMvc.perform(get("/preview/" + id).param("font", "18"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("font-size:18pt")));
    }

    @Test
    void readableExportNoCssLeakAndMarkdownRendered() throws Exception {
        // Фикстура docs/prompts/fixtures/chat-dirty.json: <style>@media...</style> в html-сообщении
        // и чистый markdown (#, **, -) в текстовом сообщении.
        String fixture;
        try (java.io.InputStream in = new java.io.FileInputStream("docs/prompts/fixtures/chat-dirty.json")) {
            fixture = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        MvcResult result = mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(fixture))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"id\":")))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        String id = json.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        String page = mockMvc.perform(get("/preview/" + id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        // Служебный CSS страницы не попадает в предпросмотр как видимый текст
        assertThat(countOccurrences(page, "@media")).isZero();
        assertThat(countOccurrences(page, "var(--")).isZero();
        // Markdown сообщений отрендерен в заголовки и жирность
        assertThat(countOccurrences(page, "<h2")).isGreaterThanOrEqualTo(1);
        assertThat(countOccurrences(page, "<strong>")).isGreaterThanOrEqualTo(1);
        // Тело сообщения из фикстуры на месте
        assertThat(page).contains("Обычный текст сообщения со служебным стилем");
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0, idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    @Test
    void previewUnknownIdReturnsFriendlyNotFound() throws Exception {
        mockMvc.perform(get("/preview/no-such-id"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("не найден")));
    }

    @Test
    void postChatAcceptsTextPlainContentTypeAndReturnsJson() throws Exception {
        // Репродукция бага букмарклета: some clients send the JSON body with
        // Content-Type: text/plain and must still get a 200 JSON answer.
        String body = "{\"title\":\"T\",\"messages\":[{\"author\":\"user\",\"text\":\"Привет\"}]}";
        mockMvc.perform(post("/chat")
                        .contentType(MediaType.TEXT_PLAIN)
                        .accept(MediaType.APPLICATION_JSON)
                        .header("Origin", "https://chat.qwen.ai")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(containsString("\"id\"")));
    }

    @Test
    void legacyHtmlModeStillWorks() throws Exception {
        String body = """
                {"title":"Легаси","messages":[{"author":"user","text":"Старый режим","html":false}]}
                """;
        // Accept: text/html — прежний HTML-предпросмотр
        mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_HTML)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Старый режим")));

        // ?legacy=html — то же самое даже с JSON Accept
        mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .param("legacy", "html")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    @Test
    void corsPreflightForChatIsAllowed() throws Exception {
        mockMvc.perform(options("/chat")
                        .header("Origin", "https://chat.qwen.ai")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }

    @Test
    void chatStoreKeepsOnlyLatestTwentyChats() {
        assertThat(chatStore.find("none")).isEmpty();

        java.util.List<String> ids = new java.util.ArrayList<>();
        for (int i = 0; i < 25; i++) {
            ChatRequest req = new ChatRequest();
            req.setTitle("Чат " + i);
            ChatRequest.Message m = new ChatRequest.Message();
            m.setAuthor("user");
            m.setText("Сообщение " + i);
            req.setMessages(List.of(m));
            ids.add(chatStore.save(req.getTitle(), req));
        }
        // Пять самых старых эвакуированы
        assertThat(chatStore.find(ids.get(0))).isEmpty();
        assertThat(chatStore.find(ids.get(4))).isEmpty();
        // Новые живы
        assertThat(chatStore.find(ids.get(24))).isPresent();
    }

    @Test
    void bookmarkletSourceGeneratesValidJavascriptHref() {
        String href = BookmarkletSource.buildBookmarkletHref();
        assertThat(href).startsWith("javascript:");
        assertThat(href).contains("window.open");
        // адрес сервера задаётся один раз в константе SERVER, запрос — через SERVER + '/chat'
        assertThat(href).contains("http://localhost:8080");
        assertThat(href).contains("/chat");
        assertThat(href).doesNotContain("%SERVER_URL%");
        assertThat(href).doesNotContain("\n");
        // Селекторы известных сайтов присутствуют
        assertThat(href).contains("markdown");
        assertThat(href).contains("data-message-author-role");
        assertThat(href).contains("message-content");
    }
}
