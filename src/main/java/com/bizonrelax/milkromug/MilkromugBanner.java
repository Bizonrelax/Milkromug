package com.bizonrelax.milkromug;

import org.springframework.boot.Banner;
import org.springframework.core.env.Environment;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Баннер Milkromug: печатает src/main/resources/banner.txt (таглайн и два
 * первых экспоната коллекции — кружка и лошадь кодера), затем догружает
 * третью и четвёртую картинки из docs/ascii-collection (mug-gwen.txt и
 * horse-gwen.txt) с разделителями и заголовками-комментариями.
 * Все четыре экспоната идут подряд при старте приложения.
 */
public class MilkromugBanner implements Banner {

    private static final String SEPARATOR =
            "\n    ------------------------------------------------------------\n";

    @Override
    public void printBanner(Environment environment, Class<?> sourceClass, PrintStream out) {
        // 1-2: таглайн + Original Mug + Original Horse из classpath banner.txt
        ResourceBannerSupport.printClasspathBanner("banner.txt", out);
        // 3: Gwen's Mug из коллекции docs/ascii-collection
        printCollectionPanel(out, "Gwen's Mug", "mug-gwen.txt");
        // 4: Gwen's Horse из коллекции docs/ascii-collection
        printCollectionPanel(out, "Gwen's Horse", "horse-gwen.txt");
    }

    private void printCollectionPanel(PrintStream out, String title, String fileName) {
        List<String> lines = loadCollectionFile(fileName);
        if (lines.isEmpty()) {
            return;
        }
        out.print(SEPARATOR);
        out.println("<!-- " + title + " -->");
        for (String line : lines) {
            out.println(line);
        }
    }

    /**
     * Ищет файл коллекции рядом с jar (релизная раскладка) или в рабочем
     * каталоге (запуск из исходников). Если файла нет — молча пропускает
     * панель, старт не должен падать из-за баннера.
     */
    private List<String> loadCollectionFile(String fileName) {
        Path location = ResourceBannerSupport.locateCollectionFile(fileName);
        if (location == null) {
            return List.of();
        }
        try {
            return Files.readAllLines(location, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return List.of();
        }
    }
}
