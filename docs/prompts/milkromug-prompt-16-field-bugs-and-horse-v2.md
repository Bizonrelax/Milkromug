ЗАЧЕМ: полевое тестирование релиза после PR #18 владельцем на живой Windows 11 выявило: ярлык не создаётся, потому что контроллер гейтит запись файла через PowerShell проверкой AWT-headless, которая к записи файла отношения не имеет; букмарклет-лоадер молча умирает на всех чатах, хотя CORS сервера по аудиту в порядке, значит подозреваются экранированный javascript:href и отсутствие видимой диагностики у лоадера; две формулировки режут слух владельца; архитектор нарисовал лошадь v2 по ASCII-мануалам, её надо принять в коллекцию и на баннер.

ЦЕЛЬ: на Windows владельца POST /desktop-shortcut возвращает ok:true и создаёт Milkromug.lnk; клик по закладке-лоадеру либо захватывает чат, либо показывает видимую красную плашку с причиной, тишины больше нет; формулировки исправлены по правкам владельца; horse-gwen-v2.txt лежит в коллекции и пятой панелью на баннере; /debug/env даёт факты среды для будущих диагнозов.

ГОТОВНОСТЬ:
- curl -s http://localhost:8080/debug/env -> 200 и JSON содержит ключи headless, headlessProperty, osName, desktopSupported, desktopPath
- curl -s http://localhost:8080/bookmarklet | grep -c "loader-test" -> 1 или больше
- grep -o "javascript:[^\"']*" src/main/resources/templates/bookmarklet.html | grep -c "&amp;\|&#39;\|&quot;" -> 0
- grep -rn "Об программе" src/main/resources README.md -> пусто
- grep -rn "выполните памятку\|исполните памятку" src/main/resources README.md -> пусто
- test -f docs/ascii-collection/horse-gwen-v2.txt && grep -c "G.W." docs/ascii-collection/horse-gwen-v2.txt -> 1
- запуск jar: в консоли пять панелей, пятая озаглавлена Gwen's Horse v2
- mvn -q test -> Failures: 0, Errors: 0

КОНТЕКСТ: аудит main владельцем-архитектором: CorsConfig уже отдаёт /bookmarklet.js с Access-Control-Allow-Origin: * и Cache-Control no-store — НЕ трогать. DesktopShortcutController: убрать гейт по GraphicsEnvironment.isHeadless(); новый гейт: os.name содержит win И папка рабочего стола (USERPROFILE/Desktop, фолбэк user.home/Desktop) существует и доступна на запись; тексты ошибок только английские. BrowserAutoOpen: вместо предпроверки isHeadless() обернуть Desktop.browse в try/catch и логировать фактический класс и сообщение исключения; строку skip печатать только при истинном headless. Лоадер в bookmarklet.html: href выводить без HTML-экранирования JS-полезной нагрузки (блок th:inline="none" или серверная константа, проверенная тестом); тело лоадера обернуть в try/catch: при ошибке рисовать фиксированную красную плашку с текстом ошибки и подсказкой открыть консоль F12, плюс console.error; при старте console.log("milkromug loader start"). Кнопка самотеста id loader-test на /bookmarklet запускает тело лоадера на собственной странице и показывает зелёный или красный результат прямо на странице. Формулировки: все вхождения "Об программе" заменить на "О программе"; фразу "выполните памятку" заменить на "отправьте команду в командную строку" по правке владельца, а там, где памятка является документом, на "откройте памятку". Точный текст лошади v2 от архитектора (вставить байт-в-байт, первая строка файла — шапка "Gwen's running horse v2, lineart by ASCII manual, Unlicense, 2026"):
```
                  /\
                 /  \
                | o  \
                 \    \
                  \    \
                   \    .--------.--.
                    \  /          \   \
                     |/            \   \
                     /'--------'    \   \
                    / |          |    \    ~
                   /  |          |     \
                  /   |          |      \
                 ^    ^          ^       ^
            G.W.
```
В banner.txt добавить пятую панель "Gwen's Horse v2" после существующих четырёх; существующие четыре панели оставить байт-в-байт.

ОКРУЖЕНИЕ: [ -f /workspace/env.sh ] || cat > /workspace/env.sh <<'EOF'
export JAVA_HOME=/opt/jdk-21.0.13+9
export PATH=$JAVA_HOME/bin:/opt/apache-maven-3.9.6/bin:$PATH
EOF
source /workspace/env.sh; java -version -> 21; mvn -v -> 3.9.x

ШАГИ:
0. Канарейка: grep -q "CTRL+ALT+M" src/main/resources/templates/bookmarklet.html && grep -q "Gwen's Horse" src/main/resources/banner.txt -> оба найдены. Иначе STOP и доклад.
0.5. git fetch origin; git checkout -b ptabc-16 origin/main.
0.6. Наследование: git status --porcelain и git log --oneline @{upstream}..HEAD; если дерево грязное или есть непушнутые коммиты прошлой сессии — сначала WIP-коммит "wip: resume handoff" и пуш, затем новые шаги.
1. Тест раньше фикса: MockMvc GET /bookmarklet утверждает, что сырой href содержит fetch( и не содержит &amp; и не содержит &#39;; и что JS лоадера содержит try{ и id плашки. mvn -q test -Dtest=Prompt16Test -> КРАСНЫЙ. Чек-поинт: git commit -m "test: reproduce escaped loader href and silent errors" и СРАЗУ пуш.
2. Микро-фикс лоадера: неэкранированный вывод href, try/catch с красной плашкой и console.error, console.log старта, кнопка loader-test. Повтор теста -> зелёный. Чек-поинт: git commit -m "fix: loader visible diagnostics and unescaped href" и СРАЗУ пуш.
3. Микро-фикс гейтов: DesktopShortcutController без headless-гейта (win + writable desktop); BrowserAutoOpen с try/catch и фактической причиной; новый GET /debug/env без секретов (headless, headlessProperty, osName, desktopSupported, desktopPath). Проверки: на linux curl -X POST /desktop-shortcut -> ok:false с причиной про windows, не про headless; curl /debug/env -> 200. Чек-поинт: git commit -m "fix: capability gates and debug env endpoint" и СРАЗУ пуш.
4. Микро-фикс формулировок по grep-ам ГОТОВНОСТИ. Чек-поинт: git commit -m "docs: wording per owner notes" и СРАЗУ пуш.
5. Лошадь v2: файл docs/ascii-collection/horse-gwen-v2.txt байт-в-байт из КОНТЕКСТА; пятая панель баннера. Проверки ГОТОВНОСТИ. Чек-поинт: git commit -m "feat: gwen horse v2 lineart in collection and banner" и СРАЗУ пуш.
6. mvn clean install -> BUILD SUCCESS; все команды ГОТОВНОСТИ зелёные.
7. Публикация: push ptabc-16, PR в main через API, мерж (владелец разрешил), git push origin --delete ptabc-16. Дождаться: CI на main green, ассет Release перезаписан.

СТОП-ПРАВИЛА: красный после микро-фикса -> трассировка с печатью стадии в отчёт, архитектуру не менять; девиация от схемы -> стоп и доклад; около 3 вызовов до лимита -> WIP-коммит и СРАЗУ пуш + REMAINING.md в коммите.

ЗАПРЕТЫ: не трогать CorsConfig (аудит: корректен), print.css, export-форматы, логику порта PortGuard, workflow maven.yml, LICENSE; не удалять и не перерисовывать существующие четыре панели баннера; не создавать no-op PR; не коммитить артефакты сборки.

ПРОТОКОЛ КОНЦА: отчёт не более 30 строк: таблица шаг->статус->хвост вывода, хэши коммитов, долги. Последние две строки строго:
СОСТОЯНИЕ: ЗЕЛЕНО|КРАСНО (tests: пройдено/всего, skipped Z)
ВЕРДИКТ: ПУБЛИКОВАТЬ|НЕ ПУБЛИКОВАТЬ — причина не длиннее 10 слов.
Плюс фраза чистого финиша: "Ветка удалена, PR смержен. Любые кнопки слияния или синхронизации, которые может показывать UI GitHub, недействительны и нажимать их не нужно".
