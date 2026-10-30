package com.bizonrelax.milkromug.controller;

/**
 * Генератор букмарклета «Milkromug Capture».
 * Читаемый исходник лежит в src/main/resources/static/bookmarklet.js —
 * он же является единственным источником истины: здесь мы читаем его из
 * classpath, разворачиваем шаблоны (адрес сервера) и формируем
 * минифицированную однострочную javascript:-ссылку.
 */
public final class BookmarkletSource {

    /** Адрес локального сервера Milkromug. */
    public static final String SERVER_URL = "http://localhost:8080";

    private static final String RESOURCE = "static/bookmarklet.js";

    private BookmarkletSource() {
    }

    /** Исходник букмарклета с подставленным адресом сервера. */
    public static String buildSource() {
        try (var in = BookmarkletSource.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Не найден ресурс " + RESOURCE);
            }
            String template = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            return template.replace("%SERVER_URL%", SERVER_URL);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Не удалось прочитать " + RESOURCE, e);
        }
    }

    /**
     * Минифицированная однострочная версия: убираем комментарии и лишние
     * переводы строк. Код в bookmarklet.js пишется без многострочных
     * строковых литералов и регулярных выражений, поэтому такая «минификация»
     * безопасна.
     */
    public static String buildMinifiedSource() {
        String[] lines = buildSource().split("\r?\n");
        StringBuilder sb = new StringBuilder();
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty()
                    || line.startsWith("//")
                    || line.startsWith("/*")
                    || line.startsWith("*")) {
                continue;
            }
            // инлайновые хвостовые комментарии " ... // comment" (без '//' внутри строк
            // в нашем коде нет — следим за этим в исходнике)
            sb.append(line).append(' ');
        }
        return sb.toString().strip();
    }

    /** href для ссылки-кнопки на странице /bookmarklet. */
    public static String buildBookmarkletHref() {
        return "javascript:" + buildMinifiedSource() + "void(0);";
    }
}
