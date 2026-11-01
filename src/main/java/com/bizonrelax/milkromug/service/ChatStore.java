package com.bizonrelax.milkromug.service;

import com.bizonrelax.milkromug.model.ChatRequest;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Хранилище захваченных чатов в памяти процесса.
 * Ключ — UUID, значение — снимок чата. Потокобезопасно (ConcurrentHashMap).
 * Ограничение — последние 20 чатов: при переполнении эвакуируется самый старый
 * по порядку сохранения.
 *
 * Для порядка используется монотонный счётчик (AtomicLong), а не системные часы:
 * на Windows разрешение часов может быть ~16 мс, из-за чего несколько чатов,
 * сохранённых подряд, получают одинаковый timestamp и тесты на эвакуацию
 * становятся нестабильными (flaky). Счётчик строго уникален для каждой записи.
 */
@Service
public class ChatStore {

    /** Максимум хранимых чатов. */
    public static final int MAX_CHATS = 20;

    /**
     * @param sequence монотонный номер сохранения (используется для эвакуации
     *                 самого старого чата вместо системного времени)
     */
    public record StoredChat(String id, String title, ChatRequest request, long sequence) {
    }

    private final Map<String, StoredChat> chats = new ConcurrentHashMap<>();
    private final AtomicLong sequenceCounter = new AtomicLong();

    /** Сохраняет чат и возвращает назначенный ему UUID-ключ. */
    public String save(String title, ChatRequest request) {
        String id = UUID.randomUUID().toString();
        chats.put(id, new StoredChat(id, title, dedupeConsecutive(request), sequenceCounter.incrementAndGet()));
        evictOldestIfNeeded();
        return id;
    }

    /**
     * Микро-фикс C (промпт-11): серверная страховка от дубликатов — DOM
     * chat.qwen.ai вкладывает контейнеры сообщений друг в друга, из-за чего
     * селектор букмарклета матчит один и тот же текст несколько раз подряд.
     * Отбрасываем сообщение, равное предыдущему по нормализованному тексту
     * (HTML вырезается до plain-text, пробельные символы схлопываются).
     * Возвращается копия запроса — входной объект не мутируется.
     */
    static ChatRequest dedupeConsecutive(ChatRequest request) {
        if (request == null || request.getMessages() == null) {
            return request;
        }
        List<ChatRequest.Message> kept = new ArrayList<>();
        String previousNormalized = null;
        for (ChatRequest.Message msg : request.getMessages()) {
            String normalized = normalizeText(msg == null ? null : msg.getText());
            if (previousNormalized != null && previousNormalized.equals(normalized)) {
                continue; // подряд идущий дубликат — пропускаем
            }
            kept.add(msg);
            previousNormalized = normalized;
        }
        ChatRequest copy = new ChatRequest();
        copy.setTitle(request.getTitle());
        copy.setMessages(kept);
        return copy;
    }

    /** Нормализация текста сообщения для сравнения подряд идущих дубликатов. */
    private static String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        String plain = Jsoup.parseBodyFragment(text).body().text();
        return plain.replaceAll("\\s+", " ").trim();
    }

    public Optional<StoredChat> find(String id) {
        return Optional.ofNullable(chats.get(id));
    }

    /** Убирает самые старые записи, пока их не станет не больше MAX_CHATS. */
    private void evictOldestIfNeeded() {
        while (chats.size() > MAX_CHATS) {
            String oldestId = null;
            long oldest = Long.MAX_VALUE;
            for (StoredChat c : chats.values()) {
                if (c.sequence() < oldest) {
                    oldest = c.sequence();
                    oldestId = c.id();
                }
            }
            if (oldestId == null) {
                return;
            }
            chats.remove(oldestId);
        }
    }
}
