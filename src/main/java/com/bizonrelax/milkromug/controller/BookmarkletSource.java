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

    /**
     * Аварийный полный однострочник (старый вариант): используется в textarea
     * страницы /bookmarklet внутри details «Аварийный вариант».
     */
    public static String buildLegacyOneLiner() {
        return buildBookmarkletHref();
    }

    /**
     * Лоадер-букмарклет (промпт-13): крошечная javascript:-строка, которая при
     * клике тянет СВЕЖИЙ код захвата с сервера (/bookmarklet.js) через fetch и
     * выполняет его через eval. Закладка переживает обновления программы без
     * переустановки: меняется только код на сервере, однострочник остаётся.
     * Кэш принудительно обходится параметром ?v= (Cache-Control: no-store на
     * /bookmarklet.js действует только для браузеров, а eval-скрипт всё равно
     * должен прийти живьём).
     */
    public static String buildLoaderHref() {
        return "javascript:(function(){var s='" + SERVER_URL + "';var u=s.indexOf('/bookmarklet.js')>=0?s:s+'/bookmarklet.js';"
                + "fetch(\"' + u + '?v=' + Date.now(),{cache:'no-store'})"
                + ".then(function(r){if(!r.ok)throw new Error('HTTP '+r.status);return r.text()})"
                + ".then(function(c){(new Function(c))()})"
                + ".catch(function(e){alert('Milkromug: ' + e.message + ' \\u2014 \\u0417\\u0430\\u043F\\u0443\\u0441\\u0442\\u0438\\u0442\\u0435 Milkromug \\u0438 \\u043F\\u043E\\u0432\\u0442\\u043E\\u0440\\u0438\\u0442\\u0435 \\u043A\\u043B\\u0438\\u043A.')})();";
    }
}
