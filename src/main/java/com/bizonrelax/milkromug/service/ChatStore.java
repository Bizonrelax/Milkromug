package com.bizonrelax.milkromug.service;

import com.bizonrelax.milkromug.model.ChatRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Хранилище захваченных чатов в памяти процесса.
 * Ключ — UUID, значение — снимок чата. Потокобезопасно (ConcurrentHashMap).
 * Ограничение — последние 20 чатов: при переполнении эвакуируется самый старый
 * по времени сохранения.
 */
@Service
public class ChatStore {

    /** Максимум хранимых чатов. */
    public static final int MAX_CHATS = 20;

    public record StoredChat(String id, String title, ChatRequest request, Instant createdAt) {
    }

    private final Map<String, StoredChat> chats = new ConcurrentHashMap<>();

    /** Сохраняет чат и возвращает назначенный ему UUID-ключ. */
    public String save(String title, ChatRequest request) {
        String id = UUID.randomUUID().toString();
        chats.put(id, new StoredChat(id, title, request, Instant.now()));
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
            Instant oldest = null;
            for (StoredChat c : chats.values()) {
                if (oldest == null || c.createdAt().isBefore(oldest)) {
                    oldest = c.createdAt();
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
