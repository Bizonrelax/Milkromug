package com.bizonrelax.milkromug;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Промпт-11, шаг 3: воспроизведение дубликатов блоков от вложенных контейнеров
 * chat.qwen.ai. Фикстура docs/prompts/fixtures/chat-dupes.json содержит три
 * подряд идущих сообщения с одинаковым текстом DUPE-MARKER (имитация внешнего
 * и внутреннего узлов, которые матчит селектор букмарклета). Ожидаемое
 * поведение после микро-фикса C: preview содержит DUPE-MARKER ровно один раз.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ChatDupesTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void previewContainsDupeMarkerExactlyOnce() throws Exception {
        String json = Files.readString(Path.of("docs/prompts/fixtures/chat-dupes.json"));
        MvcResult result = mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        String id = body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
        String preview = mockMvc.perform(get("/preview/" + id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsetsSupport.UTF8());
        int count = preview.split("DUPE-MARKER", -1).length - 1;
        assertThat(count).isEqualTo(1);
    }

    /** Небольшая обёртка, чтобы contentAsString использовал UTF-8. */
    private static final class StandardCharsetsSupport {
        static java.nio.charset.Charset UTF8() {
            return java.nio.charset.StandardCharsets.UTF_8;
        }
    }
}
