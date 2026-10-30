package com.bizonrelax.milkromug.model;

import java.util.List;

/**
 * Модель входящего JSON с чатом.
 * Ожидается структура вида:
 * <pre>
 * {
 *   "title": "Название диалога",
 *   "messages": [
 *     { "author": "user",      "text": "Привет!",        "html": false },
 *     { "author": "assistant", "text": "&lt;pre&gt;&lt;code&gt;...&lt;/code&gt;&lt;/pre&gt;", "html": true }
 *   ]
 * }
 * </pre>
 */
public class ChatRequest {

    private String title;
    private List<Message> messages;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }

    /** Одно сообщение чата. */
    public static class Message {

        private String author;
        private String text;
        /** Если true — текст содержит HTML и вставляется как есть (через jsoup-санитайзинг). */
        private boolean html;

        public String getAuthor() {
            return author;
        }

        public void setAuthor(String author) {
            this.author = author;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public boolean isHtml() {
            return html;
        }

        public void setHtml(boolean html) {
            this.html = html;
        }
    }
}
