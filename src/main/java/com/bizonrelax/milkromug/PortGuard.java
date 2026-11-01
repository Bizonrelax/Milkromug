package com.bizonrelax.milkromug;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

/**
 * Проверка доступности порта перед запуском Spring Boot.
 *
 * <p>Если порт уже занят (обычно это второй запущенный экземпляр Milkromug.exe),
 * печатаем дружелюбное сообщение на английском (Prompt-14: no mojibake)
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
     * If the port is busy - prints a hint and halts the JVM with exit code 0.
     * If the port is free - closes the probe socket and returns control.
     *
     * <p>Prompt-13 (Console-wait): in an environment with a console
     * (System.console() != null, e.g. double click on Milkromug.exe on Windows)
     * we wait for Enter after the message - otherwise the console window closes
     * instantly and looks like "no reaction". In headless mode (console == null)
     * there is no waiting, exit code 0.</p>
     */
    static void ensurePortAvailable(int port) {
        try (ServerSocket probe = new ServerSocket()) {
            probe.setReuseAddress(false);
            probe.bind(new InetSocketAddress(port));
            return; // port is free - all good
        } catch (IOException busy) {
            System.out.println(alreadyRunningMessage(port));
            waitForEnterIfConsole();
            System.out.flush();
            // Exit code 0: a second launch is not an error, just a normal situation.
            Runtime.getRuntime().halt(0);
        }
    }

    /**
     * Prompt-13: wait for Enter only if there is a live console (System.console() != null);
     * in headless/CI the wait is skipped.
     */
    static void waitForEnterIfConsole() {
        if (System.console() != null) {
            // Prompt-13/Prompt-14: readable console - separator and hint in English only.
            System.out.println("------------------------------------------------------------");
            System.out.println("Milkromug is running. To stop it, press Enter.");
            System.out.flush();
            System.console().readLine();
        }
    }

    /**
     * Message about a busy port: English only (Prompt-14: no mojibake on Windows consoles).
     */
    static String alreadyRunningMessage(int port) {
        return "Milkromug is already running. Open http://localhost:" + port
                + " in your browser. To restart it, close the old process: "
                + "taskkill /IM Milkromug.exe /F";
    }
}
