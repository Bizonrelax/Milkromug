package com.bizonrelax.milkromug.service;

import com.bizonrelax.milkromug.model.ChatRequest;
import org.springframework.stereotype.Service;

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
        chats.put(id, new StoredChat(id, title, request, sequenceCounter.incrementAndGet()));
        evictOldestIfNeeded();
        return id;
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
