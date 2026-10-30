package com.bizonrelax.milkromug.controller;

import com.bizonrelax.milkromug.model.ChatRequest;
import com.bizonrelax.milkromug.service.ChatStore;
import jakarta.servlet.http.HttpServletRequest;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Контроллер чата:
 *  - GET  /              — пустая форма-заглушка (UI со спиннером шрифта);
 *  - POST /chat          — принимает JSON с сообщениями чата, сохраняет чат в памяти
 *                          и возвращает JSON {id, url} со ссылкой на предпросмотр;
 *                          для Accept: text/html или ?legacy=html сохраняет старое
 *                          поведение — сразу отдаёт HTML-шаблон для печати;
 *  - GET  /preview/{id}  — печатный предпросмотр сохранённого чата (?font=N);
 *  - GET  /bookmarklet   — страница-инструкция с кнопкой-закладкой для захвата чатов.
 */
@Controller
@RequestMapping("/")
public class ChatController {

    private static final int MIN_FONT = 8;
    private static final int MAX_FONT = 24;

    /** Whitelist HTML-тегов, которые мы пропускаем в шаблон из входящего JSON. */
    private static final Safelist CHAT_SAFELIST = Safelist.relaxed()
            .addTags("pre", "code", "kbd", "samp", "var")
            .addAttributes("pre", "class")
            .addAttributes("code", "class")
            .preserveRelativeLinks(false);

    private final ChatStore chatStore;

    public ChatController(ChatStore chatStore) {
        this.chatStore = chatStore;
    }

    /** Стартовая страница: минимальный UI со спиннером размера шрифта. */
    @GetMapping
    public String index(@RequestParam(name = "font", defaultValue = "12") int fontSize,
                        Model model) {
        model.addAttribute("fontSize", clampFont(fontSize));
        return "index";
    }

    /**
     * Принимает POST-запрос с JSON-данными чата.
     * По умолчанию: санитизирует, сохраняет в памяти и отвечает JSON {id, url}.
     * Старое поведение (HTML-предпросмотр) сохраняется для запросов с
     * Accept: text/html или параметром ?legacy=html.
     */
    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Object renderChat(@RequestBody ChatRequest request,
                             @RequestParam(name = "font", defaultValue = "12") int fontSize,
                             @RequestParam(name = "legacy", required = false) String legacy,
                             HttpHeaders headers,
                             Model model) {

        boolean wantsHtml = "html".equalsIgnoreCase(legacy) || acceptsHtml(headers);

        if (wantsHtml) {
            // Прежний режим: ничего не сохраняем, сразу рендерим шаблон.
            model.addAttribute("title", resolveTitle(request));
            model.addAttribute("messages", renderMessages(request));
            model.addAttribute("fontSize", clampFont(fontSize));
            return "chat-print";
        }

        // Новый режим: sanitize -> сохранить -> JSON {id, url}.
        ChatRequest sanitized = sanitize(request);
        String title = resolveTitle(sanitized);
        String id = chatStore.save(title, sanitized);
        return Map.of(
                "id", id,
                "url", "http://localhost:8080/preview/" + id
        );
    }

    /** Печатный предпросмотр ранее сохранённого чата; нет id — дружелюбная 404. */
    @GetMapping("/preview/{id}")
    public String preview(@PathVariable("id") String id,
                          @RequestParam(name = "font", defaultValue = "12") int fontSize,
                          Model model) {
        var found = chatStore.find(id);
        if (found.isEmpty()) {
            model.addAttribute("missingId", id);
            model.addAttribute("fontSize", clampFont(fontSize));
            return "chat-not-found";
        }
        ChatStore.StoredChat chat = found.get();
        model.addAttribute("title", chat.title());
        model.addAttribute("messages", renderMessages(chat.request()));
        model.addAttribute("fontSize", clampFont(fontSize));
        return "chat-print";
    }

    /** Страница-инструкция с кнопкой-закладкой Milkromug Capture. */
    @GetMapping("/bookmarklet")
    public String bookmarklet(Model model) {
        model.addAttribute("bookmarkletHref", BookmarkletSource.buildBookmarkletHref());
        model.addAttribute("bookmarkletMinified", BookmarkletSource.buildMinifiedSource());
        return "bookmarklet";
    }

    /** Прогоняем все сообщения через jsoup-санитайзинг / экранирование. */
    private static ChatRequest sanitize(ChatRequest request) {
        ChatRequest copy = new ChatRequest();
        copy.setTitle(request.getTitle());
        List<ChatRequest.Message> messages = new ArrayList<>();
        if (request.getMessages() != null) {
            for (ChatRequest.Message msg : request.getMessages()) {
                ChatRequest.Message clean = new ChatRequest.Message();
                clean.setAuthor(msg.getAuthor());
                String text = msg.getText() == null ? "" : msg.getText();
                if (msg.isHtml()) {
                    clean.setText(Jsoup.clean(text, CHAT_SAFELIST));
                    clean.setHtml(true);
                } else {
                    clean.setText(text);
                    clean.setHtml(false);
                }
                messages.add(clean);
            }
        }
        copy.setMessages(messages);
        return copy;
    }

    private static String resolveTitle(ChatRequest request) {
        return request.getTitle() == null || request.getTitle().isBlank()
                ? "Milkromug Chat Print"
                : request.getTitle();
    }

    /** Готовит список сообщений для Thymeleaf (санитайзинг при выводе). */
    private List<RenderedMessage> renderMessages(ChatRequest request) {
        List<RenderedMessage> rendered = new ArrayList<>();
        if (request.getMessages() != null) {
            for (ChatRequest.Message msg : request.getMessages()) {
                String author = msg.getAuthor() == null ? "?" : msg.getAuthor();
                String text = msg.getText() == null ? "" : msg.getText();
                String safeHtml = msg.isHtml()
                        ? Jsoup.clean(text, CHAT_SAFELIST)
                        : escapePlainText(text);
                rendered.add(new RenderedMessage(author, safeHtml, msg.isHtml()));
            }
        }
        return rendered;
    }

    /** Запрос хочет HTML-ответ? (браузерная форма / классический curl) */
    private static boolean acceptsHtml(HttpHeaders headers) {
        List<MediaType> accepted = headers.getAccept();
        if (accepted == null || accepted.isEmpty()) {
            return true; // без Accept — считаем браузерным запросом (старое поведение)
        }
        for (MediaType mt : accepted) {
            if (mt.includes(MediaType.TEXT_HTML) || mt.equals(MediaType.ALL)) {
                return true;
            }
            if (mt.includes(MediaType.APPLICATION_JSON)) {
                return false;
            }
        }
        return false;
    }

    /** Простой DTO для передачи отфильтрованного сообщения в Thymeleaf. */
    public record RenderedMessage(String author, String html, boolean rawHtml) {
    }

    /**
     * Текстовые сообщения: экранируем HTML и сохраняем переносы строк,
     * абзацы разбиваются по двойному переводу строки.
     */
    private static String escapePlainText(String text) {
        String escaped = org.springframework.web.util.HtmlUtils.htmlEscape(text);
        String[] paragraphs = escaped.split("\\n{2,}");
        StringBuilder sb = new StringBuilder();
        for (String p : paragraphs) {
            sb.append("<p>").append(p.replace("\n", "<br/>")).append("</p>\n");
        }
        return sb.toString();
    }

    private static int clampFont(int fontSize) {
        return Math.max(MIN_FONT, Math.min(MAX_FONT, fontSize));
    }
}
