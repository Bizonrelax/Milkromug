package com.bizonrelax.milkromug.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Locale;

/**
 * Страница «Об программе» (/about): преамбула, функционал, преимущества,
 * как скачать и запустить, ссылка на репозиторий и таблица кредитов.
 * Шаблоны RU и EN зеркальны; язык выбирается параметром ?lang=ru|en,
 * иначе — по заголовку Accept-Language (по умолчанию русский).
 */
@Controller
@RequestMapping("/about")
public class AboutController {

    /** Строка таблицы кредитов: библиотека, версия, лицензия (по официальным pom/сайтам). */
    public record Credit(String name, String version, String license, String url) {
    }

    private static final List<Credit> CREDITS = List.of(
            new Credit("Spring Boot", "3.3.5", "Apache License 2.0",
                    "https://spring.io/projects/spring-boot"),
            new Credit("Thymeleaf", "3.1.2.RELEASE", "Apache License 2.0",
                    "https://www.thymeleaf.org"),
            new Credit("jsoup", "1.18.1", "MIT License",
                    "https://jsoup.org"),
            new Credit("Jackson", "2.17.2", "Apache License 2.0",
                    "https://github.com/FasterXML/jackson"),
            new Credit("openhtmltopdf", "1.0.10", "LGPL v2.1 / Apache License 2.0",
                    "https://openhtmltopdf.com"),
            new Credit("Eclipse Temurin JDK", "21", "Eclipse Public License 2.0",
                    "https://adoptium.net"),
            new Credit("Apache Maven", "3.9+", "Apache License 2.0",
                    "https://maven.apache.org"),
            new Credit("jpackage (OpenJDK)", "21", "GPLv2 + Classpath Exception",
                    "https://openjdk.org")
    );

    @GetMapping
    public String about(@RequestParam(name = "lang", required = false) String lang,
                        @RequestHeader(name = "Accept-Language", required = false) String acceptLanguage,
                        Model model) {
        model.addAttribute("lang", resolveLang(lang, acceptLanguage));
        model.addAttribute("credits", CREDITS);
        return "about";
    }

    /** ru по умолчанию; ?lang=en или явный en-* в Accept-Language — английская версия. */
    static String resolveLang(String lang, String acceptLanguage) {
        if (lang != null && !lang.isBlank()) {
            return lang.toLowerCase(Locale.ROOT).startsWith("en") ? "en" : "ru";
        }
        if (acceptLanguage != null) {
            String first = acceptLanguage.split(",")[0].trim().toLowerCase(Locale.ROOT);
            if (first.startsWith("en")) {
                return "en";
            }
        }
        return "ru";
    }
}
