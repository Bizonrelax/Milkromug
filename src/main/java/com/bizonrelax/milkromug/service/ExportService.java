package com.bizonrelax.milkromug.service;

import com.bizonrelax.milkromug.model.ChatRequest;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

/**
 * Промпт-12: экспорт сохранённого чата в четыре текстовых формата
 * (md, html, txt, json). Каждый метод возвращает ResponseEntity с
 * Content-Disposition: attachment и верным типом содержимого.
 */
@Service
public class ExportService {

    /** Экспорт в markdown: text сообщения, если он markdown; иначе обратная конвертация html. */
    public ResponseEntity<String> toMarkdown(String id, String title, ChatRequest request) {
        StringBuilder sb = new StringBuilder();
        if (title != null && !title.isBlank()) {
            sb.append("# ").append(title).append("\n\n");
        }
        if (request.getMessages() != null) {
            for (ChatRequest.Message msg : request.getMessages()) {
                String author = msg.getAuthor() == null ? "?" : msg.getAuthor();
                sb.append("## ").append(author).append("\n\n");
                sb.append(messageToMarkdown(msg)).append("\n\n");
            }
        }
        return attachment(sb.toString(), "chat-" + id + ".md", MediaType.parseMediaType("text/markdown"));
    }

    /** Экспорт в plain text: jsoup-текст сообщений с переносами строк, без символов разметки. */
    public ResponseEntity<String> toPlainText(String id, String title, ChatRequest request) {
        StringBuilder sb = new StringBuilder();
        if (title != null && !title.isBlank()) {
            sb.append(title).append("\n\n");
        }
        if (request.getMessages() != null) {
            for (ChatRequest.Message msg : request.getMessages()) {
                String author = msg.getAuthor() == null ? "?" : msg.getAuthor();
                sb.append("[").append(author).append("]\n");
                String text = msg.getText() == null ? "" : msg.getText();
                String plain = msg.isHtml()
                        ? Jsoup.parseBodyFragment(text).body().wholeText()
                        : stripMarkdownSymbols(text);
                sb.append(plain.strip()).append("\n\n");
            }
        }
        return attachment(sb.toString(), "chat-" + id + ".txt", MediaType.TEXT_PLAIN);
    }

    /** Экспорт в standalone html: без панели инструментов, css встроен, внешних ссылок нет. */
    public ResponseEntity<String> toStandaloneHtml(String id, String title, ChatRequest request) {
        StringBuilder body = new StringBuilder();
        body.append("<h2>").append(HtmlUtils.htmlEscape(title == null ? "" : title)).append("</h2>\n");
        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            for (ChatRequest.Message msg : request.getMessages()) {
                String author = msg.getAuthor() == null ? "?" : msg.getAuthor();
                String text = msg.getText() == null ? "" : msg.getText();
                // Санитизированный html сообщения (jsoup safelist — тот же путь, что у предпросмотра)
                String safe = Jsoup.clean(text, com.bizonrelax.milkromug.controller.ChatController.exportSafelist());
                if (!msg.isHtml()) {
                    safe = escapeNonHtml(safe);
                }
                body.append("<section class=\"message message-")
                        .append("user".equals(author) ? "user" : "assistant")
                        .append("\">\n<h3>")
                        .append(HtmlUtils.htmlEscape(author))
                        .append("</h3>\n<div class=\"content\">").append(safe).append("</div>\n</section>\n");
            }
        } else {
            body.append("<p>Сообщений нет.</p>\n");
        }
        String html = """
                <!DOCTYPE html>
                <html lang="ru">
                <head>
                <meta charset="UTF-8"/>
                <title>%TITLE%</title>
                <style>
                body { font-family: Georgia, 'Times New Roman', serif; margin: 2em auto; max-width: 50em; color: #222; background: #fff; }
                h2.chat-title { border-bottom: 2px solid #444; padding-bottom: .3em; }
                section.message { margin: 1.2em 0; padding: .6em 1em; border-left: 4px solid #bbb; background: #fafafa; }
                section.message-user { border-left-color: #2e7d32; }
                section.message-assistant { border-left-color: #1565c0; }
                h3.author { margin: 0 0 .4em; font-size: .9em; text-transform: uppercase; color: #555; }
                pre, code { font-family: 'Courier New', monospace; background: #f0f0f0; border-radius: 3px; }
                pre { padding: .6em; overflow-x: auto; }
                blockquote { margin: .5em 0; padding-left: 1em; border-left: 3px solid #ccc; color: #555; }
                table { border-collapse: collapse; }
                th, td { border: 1px solid #ccc; padding: .3em .6em; }
                </style>
                </head>
                <body>
                %BODY%
                </body>
                </html>
                """.replace("%TITLE%", "<h2/>")
                .replace("<h2/>", HtmlUtils.htmlEscape(title == null ? "Milkromug Chat" : title))
                .replace("%BODY%", body.toString());
        return attachment(html, "chat-" + id + ".html", MediaType.TEXT_HTML);
    }

    /** Экспорт в json: исходный payload сохранённого чата. */
    public ResponseEntity<String> toJson(String id, ChatRequest request) {
        String json;
        try {
            json = new com.fasterxml.jackson.databind.ObjectMapper()
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(request);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сериализовать чат в json", e);
        }
        return attachment(json, "chat-" + id + ".json", MediaType.APPLICATION_JSON);
    }

    /* ---------- вспомогательное ---------- */

    private static ResponseEntity<String> attachment(String content, String filename, MediaType mediaType) {
        // UTF-8 в явном виде: иначе Tomcat отдаёт text/* с charset=ISO-8859-1 и кириллица «сломается».
        MediaType utf8 = new MediaType(mediaType.getType(), mediaType.getSubtype(), java.nio.charset.StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(utf8)
                .body(content);
    }

    /**
     * Markdown-представление одного сообщения: если поле text непустое и похоже
     * на markdown — используем его как есть; иначе обратная конвертация html
     * только для базовых узлов (заголовки, списки, жирный, код-блоки с языком).
     */
    private static String messageToMarkdown(ChatRequest.Message msg) {
        String text = msg.getText() == null ? "" : msg.getText();
        if (!msg.isHtml() && looksLikeMarkdown(text)) {
            return text.strip();
        }
        if (!msg.isHtml()) {
            return text.strip();
        }
        // html-сообщение: если внутри нет блочых тегов — текст и есть markdown
        Element root = Jsoup.parseBodyFragment(text).body();
        boolean hasBlock = root.getAllElements().stream()
                .anyMatch(el -> switch (el.tagName()) {
                    case "p", "h1", "h2", "h3", "h4", "h5", "h6", "pre", "ul", "ol", "table", "blockquote" -> true;
                    default -> false;
                });
        if (!hasBlock) {
            return text.strip();
        }
        return htmlToMarkdown(root).strip();
    }

    /** Признаки markdown в plain-text сообщении. */
    private static boolean looksLikeMarkdown(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return text.matches("(?s)^\\s*#{1,6} .*")                 // заголовок
                || text.contains("**")                             // жирный
                || text.matches("(?s).*```.*")                     // код-блок
                || text.matches("(?s).*\\n\\s*[-*+] .*")           // маркированный список
                || text.matches("(?s).*\\n\\s*\\d+\\. .*");        // нумерованный список
    }

    /** Обратная конвертация ограниченного набора html-узлов в markdown. */
    private static String htmlToMarkdown(Element container) {
        StringBuilder sb = new StringBuilder();
        for (Element el : container.children()) {
            switch (el.tagName()) {
                case "h1" -> sb.append("# ").append(el.text()).append("\n\n");
                case "h2" -> sb.append("## ").append(el.text()).append("\n\n");
                case "h3" -> sb.append("### ").append(el.text()).append("\n\n");
                case "h4" -> sb.append("#### ").append(el.text()).append("\n\n");
                case "h5" -> sb.append("##### ").append(el.text()).append("\n\n");
                case "h6" -> sb.append("###### ").append(el.text()).append("\n\n");
                case "pre" -> {
                    String lang = langFromClass(el);
                    sb.append("```").append(lang).append('\n')
                            .append(el.wholeText().strip()).append("\n```\n\n");
                }
                case "ul" -> {
                    for (Element li : el.select("> li")) {
                        sb.append("- ").append(inlineToMarkdown(li)).append('\n');
                    }
                    sb.append('\n');
                }
                case "ol" -> {
                    int n = 1;
                    for (Element li : el.select("> li")) {
                        sb.append(n++).append(". ").append(inlineToMarkdown(li)).append('\n');
                    }
                    sb.append('\n');
                }
                case "blockquote" -> sb.append("> ").append(el.text().strip()).append("\n\n");
                case "p" -> sb.append(inlineToMarkdown(el)).append("\n\n");
                default -> sb.append(el.text().strip()).append("\n\n");
            }
        }
        return sb.toString();
    }

    /** Инлайновые преобразования: <strong> -> **, <em> -> *, <code> -> `...`. */
    private static String inlineToMarkdown(Element el) {
        StringBuilder sb = new StringBuilder();
        walkInline(el, sb);
        return sb.toString().strip();
    }

    private static void walkInline(Element el, StringBuilder sb) {
        el.childNodes().forEach(node -> {
            if (node instanceof org.jsoup.nodes.TextNode tn) {
                sb.append(tn.text());
            } else if (node instanceof Element child) {
                switch (child.tagName()) {
                    case "strong", "b" -> sb.append("**").append(child.text()).append("**");
                    case "em", "i" -> sb.append("*").append(child.text()).append("*");
                    case "code" -> sb.append('`').append(child.text()).append('`');
                    case "a" -> sb.append('[').append(child.text()).append("](")
                            .append(child.attr("href")).append(')');
                    default -> sb.append(child.text());
                }
            }
        });
    }

    private static String langFromClass(Element pre) {
        Element code = pre.selectFirst("code");
        String classes = (code != null ? code.className() : pre.className());
        for (String cls : classes.split("\\s+")) {
            if (cls.startsWith("language-")) {
                return cls.substring("language-".length());
            }
            if (cls.startsWith("lang-")) {
                return cls.substring("lang-".length());
            }
        }
        return "";
    }

    /** plain-text сообщение: экранируем, сохраняя переносы строк через <br/>. */
    private static String escapeNonHtml(String sanitized) {
        String escaped = HtmlUtils.htmlEscape(sanitized);
        return escaped.replace("\n", "<br/>\n");
    }

    /**
     * Снятие markdown-разметки с plain-text сообщения для txt-экспорта:
     * решётки заголовков и звёздочки жирного/списков не должны попадать в txt.
     */
    private static String stripMarkdownSymbols(String text) {
        String[] lines = text.split("\n", -1);
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            String s = line;
            s = s.replaceAll("^\\s*#{1,6}\\s+", "");   // заголовки #..######
            s = s.replaceAll("^\\s*[-*+]\\s+", "");    // маркированный список
            s = s.replace("**", "").replace("__", ""); // жирный
            s = s.replace("`", "");                    // inline code / fences
            out.append(s).append('\n');
        }
        return out.toString();
    }
}
