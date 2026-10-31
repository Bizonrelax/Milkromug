package com.bizonrelax.milkromug.controller;

import com.bizonrelax.milkromug.model.ChatRequest;
import com.bizonrelax.milkromug.service.ChatStore;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;

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
    @PostMapping(value = "/chat",
                 consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_PLAIN_VALUE},
                 produces = {MediaType.TEXT_HTML_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public Object renderChat(@RequestBody String rawBody,
                             @RequestParam(name = "font", defaultValue = "12") int fontSize,
                             @RequestParam(name = "legacy", required = false) String legacy,
                             @RequestHeader(name = HttpHeaders.ACCEPT, required = false) String acceptHeader,
                             Model model) throws Exception {

        // Тело всегда JSON, но Content-Type у клиентов бывает text/plain — парсим явно.
        ChatRequest request = new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(rawBody, ChatRequest.class);

        boolean wantsHtml = "html".equalsIgnoreCase(legacy) || acceptsHtml(acceptHeader);

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
        // Возвращаем готовую JSON-строку: контроллер смешивает HTML-шаблоны и JSON,
        // поэтому сериализуем явно (Jackson в classpath).
        return org.springframework.http.ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(new com.fasterxml.jackson.databind.ObjectMapper()
                        .writeValueAsString(new ChatResponse(id, "http://localhost:8080/preview/" + id)));
    }

    /** Печатный предпросмотр ранее сохранённого чата; нет id — дружелюбная страница 404. */
    @GetMapping("/preview/{id}")
    public String preview(@PathVariable("id") String id,
                          @RequestParam(name = "font", defaultValue = "12") int fontSize,
                          Model model,
                          jakarta.servlet.http.HttpServletResponse response) {
        var found = chatStore.find(id);
        if (found.isEmpty()) {
            response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND);
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

    /**
     * Запускает html через jsoup и вырезает служебные узлы (стили, скрипты, мета)
     * ДО санитизации: иначе их содержимое превращается в видимый текст документа.
     */
    static String stripServiceNodes(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        Document doc = Jsoup.parse(html);
        doc.select("style, script, noscript, svg, link, meta").remove();
        return doc.body().html();
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
                    String sanitized = Jsoup.clean(stripServiceNodes(text), CHAT_SAFELIST);
                    // Микро-фикс B: html без блочных тегов — на самом деле markdown.
                    clean.setText(renderMarkdownIfPlain(sanitized, text));
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
                String safeHtml;
                boolean rawHtml;
                if (msg.isHtml()) {
                    safeHtml = Jsoup.clean(stripServiceNodes(text), CHAT_SAFELIST);
                    // Микро-фикс B: html без блочных тегов — на самом деле markdown.
                    safeHtml = renderMarkdownIfPlain(safeHtml, text);
                    rawHtml = true;
                } else {
                    // plain-text: рендерим markdown через commonmark (серверный рендер).
                    String md = renderMarkdownIfPlain("", text);
                    if (md.isBlank()) {
                        safeHtml = escapePlainText(text);
                        rawHtml = false;
                    } else {
                        safeHtml = md;
                        rawHtml = true;
                    }
                }
                rendered.add(new RenderedMessage(author, safeHtml, rawHtml));
            }
        }
        return rendered;
    }

    /**
     * Блочные теги, наличие которых в уже отсанитизированном html означает,
     * что сообщение — настоящий HTML, а не plain-text с markdown.
     */
    private static final java.util.Set<String> BLOCK_TAGS = java.util.Set.of(
            "p", "h1", "h2", "h3", "h4", "h5", "h6", "pre", "ul", "ol", "table", "blockquote");

    /**
     * Микро-фикс B: если у санитизированного html нет блочных тегов — считаем
     * текст сообщения markdown и рендерим его через commonmark, результат
     * дополнительно санитизируем тем же safelist. Пустой sanitizedHtml означает
     * «plain-text сообщение»: markdown рендерится всегда.
     */
    static String renderMarkdownIfPlain(String sanitizedHtml, String text) {
        if (sanitizedHtml == null || sanitizedHtml.isBlank()) {
            org.commonmark.parser.Parser parser = org.commonmark.parser.Parser.builder().build();
            org.commonmark.renderer.html.HtmlRenderer renderer =
                    org.commonmark.renderer.html.HtmlRenderer.builder().build();
            String mdHtml = renderer.render(parser.parse(text == null ? "" : text));
            return Jsoup.clean(mdHtml, CHAT_SAFELIST);
        }
        Document parsed = Jsoup.parseBodyFragment(sanitizedHtml);
        boolean hasBlock = !parsed.body().getAllElements().stream()
                .filter(el -> BLOCK_TAGS.contains(el.tagName()))
                .findAny()
                .isPresent();
        if (hasBlock) {
            return sanitizedHtml;
        }
        org.commonmark.parser.Parser parser = org.commonmark.parser.Parser.builder().build();
        org.commonmark.renderer.html.HtmlRenderer renderer =
                org.commonmark.renderer.html.HtmlRenderer.builder().build();
        String mdHtml = renderer.render(parser.parse(text == null ? "" : text));
        return Jsoup.clean(mdHtml, CHAT_SAFELIST);
    }

    /**
     * Запрос хочет HTML-ответ? (браузерная форма / классический curl).
     * Legacy-режим включается ТОЛЬКО явно: пустой/отсутствующий Accept
     * (классический curl из старых инструкций) или явное упоминание text/html.
     * Wildcard (*&#47;*, telco/* и т.п.) явным не считается и сам по себе
     * в HTML-режим не отправляет.
     */
    private static boolean acceptsHtml(String acceptHeader) {
        if (acceptHeader == null || acceptHeader.isBlank()) {
            return true; // без Accept — считаем браузерным запросом (старое поведение)
        }
        List<MediaType> accepted;
        try {
            accepted = MediaType.parseMediaTypes(acceptHeader);
        } catch (RuntimeException e) {
            return false;
        }
        MediaType.sortBySpecificityAndQuality(accepted);
        for (MediaType mt : accepted) {
            if (mt.isWildcardType() || mt.isWildcardSubtype()) {
                continue; // wildcard — неявный запрос, не legacy
            }
            if (mt.includes(MediaType.TEXT_HTML)) {
                return true;
            }
            if (mt.includes(MediaType.APPLICATION_JSON)) {
                return false;
            }
        }
        return false;
    }

    /** Ответ нового режима POST /chat: id сохранённого чата и ссылка на предпросмотр. */
    public record ChatResponse(String id, String url) {
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
