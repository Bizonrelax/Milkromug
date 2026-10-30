package com.bizonrelax.milkromug.controller;

import com.bizonrelax.milkromug.model.ChatRequest;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;

/**
 * Контроллер чата:
 *  - GET  /            — пустая форма-заглушка (UI со спиннером шрифта);
 *  - POST /chat        — принимает JSON с сообщениями чата и отдаёт HTML-шаблон для печати;
 *  - POST /chat?font=14— то же самое, но с выбранным размером шрифта (8..24 pt).
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

    /** Стартовая страница: минимальный UI со спиннером размера шрифта. */
    @GetMapping
    public String index(@RequestParam(name = "font", defaultValue = "12") int fontSize,
                        Model model) {
        model.addAttribute("fontSize", clampFont(fontSize));
        return "index";
    }

    /**
     * Принимает POST-запрос с JSON-данными чата и отдаёт готовый HTML-шаблон
     * для просмотра/печати (кнопка "Сохранить в PDF" вызывает window.print()).
     */
    @PostMapping(value = "/chat", consumes = "application/json")
    public String renderChat(@RequestBody ChatRequest request,
                             @RequestParam(name = "font", defaultValue = "12") int fontSize,
                             Model model) {

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

        model.addAttribute("title",
                request.getTitle() == null || request.getTitle().isBlank()
                        ? "Milkromug Chat Print"
                        : request.getTitle());
        model.addAttribute("messages", rendered);
        model.addAttribute("fontSize", clampFont(fontSize));
        return "chat-print";
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
