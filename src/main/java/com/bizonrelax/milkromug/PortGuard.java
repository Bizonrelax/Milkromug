package com.bizonrelax.milkromug;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Locale;

/**
 * Проверка доступности порта перед запуском Spring Boot.
 *
 * <p>Если порт уже занят (обычно это второй запущенный экземпляр Milkromug.exe),
 * печатаем дружелюбное сообщение на русском (или английском для нерусской локали)
 * и завершаем процесс с кодом 0 — вместо непонятного "Failed to launch JVM"
 * и stacktrace от BindException.</p>
 */
final class PortGuard {

    private static final int DEFAULT_PORT = 8080;

    private PortGuard() {
    }

    /**
     * Возвращает порт, который приложение будет использовать: значение из
     * {@code --server.port=...} / переменной окружения {@code SERVER_PORT},
     * иначе 8080.
     */
    static int resolvePort(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--server.port=")) {
                int port = parsePort(arg.substring("--server.port=".length()));
                if (port > 0) {
                    return port;
                }
            }
        }
        String env = System.getenv("SERVER_PORT");
        if (env != null && !env.isBlank()) {
            int port = parsePort(env.trim());
            if (port > 0) {
                return port;
            }
        }
        return DEFAULT_PORT;
    }

    private static int parsePort(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Если порт занят — печатает подсказку и завершает JVM с кодом 0.
     * Если порт свободен — сразу освобождает тестовый сокет и возвращает управление.
     *
     * <p>Промпт-13 (Console-wait): в среде с консолью (System.console() != null,
     * например двойной клик по Milkromug.exe на Windows) после сообщения ждём
     * нажатие Enter — иначе окно консоли мгновенно закрывается и выглядит как
     * «нет реакции». В headless-режиме (console == null) ожидания нет, код выхода 0.</p>
     */
    static void ensurePortAvailable(int port) {
        try (ServerSocket probe = new ServerSocket()) {
            probe.setReuseAddress(false);
            probe.bind(new InetSocketAddress(port));
            return; // порт свободен — всё в порядке
        } catch (IOException busy) {
            System.out.println(alreadyRunningMessage(port));
            waitForEnterIfConsole();
            System.out.flush();
            // Код 0: повторный запуск — не ошибка, а штатная ситуация.
            Runtime.getRuntime().halt(0);
        }
    }

    /**
     * Промпт-13: ждать Enter только если есть живая консоль (System.console() != null);
     * в headless/CI ожидание пропускаем. Вынесено из PortGuard-логики связывания порта —
     * трогается только печать и ожидание.
     */
    static void waitForEnterIfConsole() {
        if (System.console() != null) {
            System.out.println("Нажмите Enter для выхода / Press Enter to exit...");
            System.out.flush();
            System.console().readLine();
        }
    }

    /**
     * Сообщение о занятом порте: по-русски для русской локали, иначе по-английски.
     * Пакет jpackage собирается под en-US, но ОС пользователя может быть любой.
     */
    static String alreadyRunningMessage(int port) {
        Locale locale = Locale.getDefault();
        if ("ru".equalsIgnoreCase(locale.getLanguage())) {
            return "Milkromug уже запущен. Откройте http://localhost:" + port
                    + " в браузере. Для перезапуска закройте старый процесс: "
                    + "taskkill /IM Milkromug.exe /F";
        }
        return "Milkromug is already running. Open http://localhost:" + port
                + " in your browser. To restart it, close the old process: "
                + "taskkill /IM Milkromug.exe /F";
    }
}
