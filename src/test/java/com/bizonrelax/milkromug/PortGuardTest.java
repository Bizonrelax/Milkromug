package com.bizonrelax.milkromug;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты PortGuard: определение порта и тексты сообщений.
 * Сам halt(0) не тестируется — он завершает JVM.
 */
class PortGuardTest {

    @Test
    void defaultPortIs8080() {
        assertThat(PortGuard.resolvePort(new String[]{})).isEqualTo(8080);
    }

    @Test
    void resolvesPortFromCommandLineArg() {
        assertThat(PortGuard.resolvePort(new String[]{"--server.port=9090"})).isEqualTo(9090);
    }

    @Test
    void ignoresMalformedPortArg() {
        assertThat(PortGuard.resolvePort(new String[]{"--server.port=abc"})).isEqualTo(8080);
    }

    @Test
    void russianMessageForRussianLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("ru-RU"));
            String message = PortGuard.alreadyRunningMessage(8080);
            assertThat(message)
                    .contains("Milkromug уже запущен")
                    .contains("http://localhost:8080")
                    .contains("taskkill /IM Milkromug.exe /F");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void englishMessageForOtherLocales() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.ENGLISH);
            String message = PortGuard.alreadyRunningMessage(8080);
            assertThat(message)
                    .contains("Milkromug is already running")
                    .contains("http://localhost:8080")
                    .contains("taskkill /IM Milkromug.exe /F");
        } finally {
            Locale.setDefault(previous);
        }
    }
}
