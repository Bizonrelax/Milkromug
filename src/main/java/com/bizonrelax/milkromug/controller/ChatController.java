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
            .addTags("pre", "code", "kbd", "samp", "var", "del", "s", "strike")
            .addAttributes("pre", "class")
            .addAttributes("code", "class")
            .addAttributes("table", "class")
            .preserveRelativeLinks(false);

    private final ChatStore chatStore;
    private final com.bizonrelax.milkromug.service.ExportService exportService;

    public ChatController(ChatStore chatStore,
                          com.bizonrelax.milkromug.service.ExportService exportService) {
        this.chatStore = chatStore;
        this.exportService = exportService;
    }

    /** Safelist для экспорта (тот же, что у предпросмотра). */
    public static org.jsoup.safety.Safelist exportSafelist() {
        return CHAT_SAFELIST;
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

    /** Печатный предпросмотр ранее сохранённого чата; нет id — дружелюбная страница 404.
     *  С параметром ?format=md|html|txt|json отдаёт файл-экспорт с attachment. */
    @GetMapping("/preview/{id}")
    public Object preview(@PathVariable("id") String id,
                          @RequestParam(name = "font", defaultValue = "12") int fontSize,
                          @RequestParam(name = "format", required = false) String format,
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

        // Экспорт в текстовый формат: отдать файл с Content-Disposition: attachment.
        if (format != null && !format.isBlank()) {
            return switch (format.toLowerCase()) {
                case "md" -> exportService.toMarkdown(id, chat.title(), chat.request());
                case "txt" -> exportService.toPlainText(id, chat.title(), chat.request());
                case "html" -> exportService.toStandaloneHtml(id, chat.title(), chat.request());
                case "json" -> exportService.toJson(id, chat.request());
                default -> {
                    response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST);
                    yield null;
                }
            };
        }

        model.addAttribute("title", chat.title());
        model.addAttribute("messages", renderMessages(chat.request()));
        model.addAttribute("fontSize", clampFont(fontSize));
        model.addAttribute("chatId", id);
        return "chat-print";
    }

    /** Страница-инструкция с кнопкой-закладкой Milkromug Capture. */
    @GetMapping("/bookmarklet")
    public String bookmarklet(Model model) {
        // Промпт-13: перетаскиваемая кнопка и textarea — это ЛОАДЕР (fetch + eval
        // свежего кода с /bookmarklet.js), закладка переживает обновления программы.
        model.addAttribute("bookmarkletHref", BookmarkletSource.buildLoaderHref());
        model.addAttribute("bookmarkletMinified", BookmarkletSource.buildLoaderHref());
        // Аварийный вариант (полный старый однострочник) — в details на странице.
        model.addAttribute("bookmarkletLegacy", BookmarkletSource.buildLegacyOneLiner());
        return "bookmarklet";
    }

    /** Глоссарий и FAQ для новичков (промпт-13): RU+EN зеркала, ?lang=ru|en. */
    @GetMapping("/glossary")
    public String glossary(@RequestParam(name = "lang", required = false) String lang,
                           @org.springframework.web.bind.annotation.RequestHeader(
                                   name = "Accept-Language", required = false) String acceptLanguage,
                           Model model) {
        model.addAttribute("lang", AboutController.resolveLang(lang, acceptLanguage));
        return "glossary";
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

    /**
     * Микро-фикс (промпт-13, трассировка share.google/aimode): сообщения-«реплики»
     * чата AI Mode состоят из одного-двух плоских текстовых узлов (обычно это
     * innerText блоков {@code <style>}/{@code <script>} живого чата). Настоящий ответ
     * всегда содержит блочную разметку или markdown-блоки. Правило отсева: если после
     * удаления служебных тегов от html-сообщения остаётся только текст без каких-либо
     * сохранившихся тегов — это CSS/JS-мусор, сообщение выбрасывается из предпросмотра.
     * Возвращает "" для «мусорного» html; для plain-text сообщений метод не применяется.
     */
    static String dropMarkuplessHtmlMessage(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        Document doc = Jsoup.parseBodyFragment(text);
        doc.select("style, script, noscript, svg, link, meta").remove();
        // Если в исходном тексте нет ни одного тега (CSS-правила, JS-снаппеты),
        // сообщение не является HTML-разметкой реплики — это мусор.
        boolean hasTag = text.indexOf('<') >= 0 && !doc.body().children().isEmpty();
        if (!hasTag) {
            return "";
        }
        return doc.body().html();
    }

    /** true — если html-сообщение после зачистки не содержит никакой разметки (мусор). */
    private static boolean isJunkHtmlMessage(String text) {
        if (text == null || text.isBlank()) {
            return false; // пустоту лечит прежняя логика, не отсев
        }
        Document doc = Jsoup.parseBodyFragment(text);
        doc.select("style, script, noscript, svg, link, meta").remove();
        boolean hasTag = text.indexOf('<') >= 0 && !doc.body().children().isEmpty();
        return !hasTag;
    }

    /**
     * Санитизированный html для сообщения с {@code html=true}: сначала вырезание
     * служебных узлов и safelist, затем правило отсева мусора share.google/aimode
     * (плоский CSS/JS без единого тега). ВНИМАНИЕ: порядок важен — markdown-рендер
     * применяется только к НЕ мусорным сообщениям (иначе плоский JS-снаппет
     * превратится в абзац текста).
     */
    private static String sanitizeHtmlMessage(String text) {
        // Порядок важен: мусор отсекаем ДО Jsoup.clean — иначе плоский CSS
        // переживает safelist как текст и попадает в предпросмотр.
        if (isJunkHtmlMessage(text)) {
            return "";
        }
        String sanitized = Jsoup.clean(stripServiceNodes(text), CHAT_SAFELIST);
        return renderMarkdownIfPlain(sanitized, text);
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
                    // Промпт-13: html-мусор share.google/aimode (плоский CSS/JS) отсекается.
                    clean.setText(sanitizeHtmlMessage(text));
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
                    // Промпт-13: html-мусор share.google/aimode (плоский CSS/JS) отсекается.
                    safeHtml = sanitizeHtmlMessage(text);
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
            String mdHtml = renderMarkdown(text == null ? "" : text);
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
        String mdHtml = renderMarkdown(text == null ? "" : text);
        return Jsoup.clean(mdHtml, CHAT_SAFELIST);
    }

    /**
     * Markdown -> HTML с GFM-расширениями: таблицы, зачёркивания, авто-ссылки.
     * Код в блоках ``` получает class="language-<язык>" для подсветки hljs.
     */
    static String renderMarkdown(String markdown) {
        java.util.Collection<? extends org.commonmark.Extension> extensions = java.util.List.of(
                org.commonmark.ext.gfm.tables.TablesExtension.create(),
                org.commonmark.ext.gfm.strikethrough.StrikethroughExtension.create(),
                org.commonmark.ext.autolink.AutolinkExtension.create());
        org.commonmark.parser.Parser parser = org.commonmark.parser.Parser.builder()
                .extensions(extensions)
                .build();
        org.commonmark.renderer.html.HtmlRenderer renderer =
                org.commonmark.renderer.html.HtmlRenderer.builder()
                        .extensions(extensions)
                        .build();
        return renderer.render(parser.parse(markdown));
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
