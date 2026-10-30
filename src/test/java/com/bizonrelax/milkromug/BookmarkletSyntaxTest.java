package com.bizonrelax.milkromug;

import com.bizonrelax.milkromug.controller.BookmarkletSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверка синтаксиса букмарклета через `node --check`, если Node.js доступен
 * в окружении сборки (локально / CI с Node). В Windows-CI Node может отсутствовать —
 * тогда тест просто пропускается.
 */
class BookmarkletSyntaxTest {

    @Test
    void minifiedBookmarkletIsSingleLineAndBalanced() {
        String href = BookmarkletSource.buildBookmarkletHref();
        assertThat(href).startsWith("javascript:");
        assertThat(href).doesNotContain("\n");
        // грубая проверка баланса скобок/кавычек однострочника
        long open = href.chars().filter(c -> c == '(' || c == '{' || c == '[').count();
        long close = href.chars().filter(c -> c == ')' || c == '}' || c == ']').count();
        assertThat(open - close).isBetween(-2L, 2L); // строковые литералы могут давать небольшой перекос
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "NODE_AVAILABLE", matches = "1")
    void nodeCheckPassesForReadableAndMinifiedSources() throws Exception {
        Path readable = Path.of("src/main/resources/static/bookmarklet.js");
        assertThat(Files.exists(readable)).isTrue();
        runNodeCheck(readable.toString());

        Path tmp = Files.createTempFile("bookmarklet-min", ".js");
        Files.writeString(tmp, BookmarkletSource.buildMinifiedSource(), java.nio.charset.StandardCharsets.UTF_8);
        try {
            runNodeCheck(tmp.toString());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static void runNodeCheck(String file) throws Exception {
        Process p = new ProcessBuilder("node", "--check", file)
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        int code = p.waitFor();
        assertThat(code).as("node --check %s: %s", file, out).isZero();
    }
}
