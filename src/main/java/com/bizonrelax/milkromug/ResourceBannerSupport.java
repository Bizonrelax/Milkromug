package com.bizonrelax.milkromug;

import org.springframework.core.io.ClassPathResource;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Утилиты баннера: печать classpath-ресурса и поиск файлов ASCII-коллекции
 * docs/ascii-collection рядом с jar или в рабочем каталоге.
 */
final class ResourceBannerSupport {

    private ResourceBannerSupport() {
    }

    /** Печатает текстовый ресурс из classpath (например banner.txt). */
    static void printClasspathBanner(String resourceName, PrintStream out) {
        try {
            ClassPathResource resource = new ClassPathResource(resourceName);
            if (resource.exists()) {
                String text = new String(
                        resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                out.println(text.stripTrailing());
            }
        } catch (Exception e) {
            // старт не должен падать из-за баннера
        }
    }

    /**
     * Ищет файл коллекции docs/ascii-collection/<fileName>:
     * 1) рядом с исполняемым jar (релизная раскладка),
     * 2) вверх от рабочего каталога (запуск из исходников).
     * Возвращает null, если файл не найден.
     */
    static Path locateCollectionFile(String fileName) {
        Path fromJar = jarDirectory().resolve("docs").resolve("ascii-collection").resolve(fileName);
        if (Files.isReadable(fromJar)) {
            return fromJar;
        }
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 5 && dir != null; i++, dir = dir.getParent()) {
            Path candidate = dir.resolve("docs").resolve("ascii-collection").resolve(fileName);
            if (Files.isReadable(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static Path jarDirectory() {
        try {
            Path codeSource = Path.of(
                    ResourceBannerSupport.class.getProtectionDomain()
                            .getCodeSource().getLocation().toURI());
            return Files.isDirectory(codeSource) ? codeSource : codeSource.getParent();
        } catch (Exception e) {
            return Path.of("");
        }
    }
}
