ЗАЧЕМ: пользователь получает нечитаемый экспорт: служебный CSS страницы попадает в предпросмотр и PDF как видимый текст, а markdown сообщений остаётся сырыми маркерами (#, **) и строки слипаются. Обещание Milkromug — чистый читаемый документ; конвейер захвата и рендера обязан оставлять только тело сообщений и превращать markdown в заголовки и жирность.

ЦЕЛЬ: POST /chat и GET /preview/{id} дают страницу, где (а) нет ни одного куска CSS/JS страницы, (б) markdown сообщений отрендерен в заголовки/жирность/списки, (в) переносы строк сохранены.

ГОТОВНОСТЬ:
- curl -s -X POST -H "Content-Type: application/json" --data @docs/prompts/fixtures/chat-dirty.json http://localhost:8080/chat -> 200 и тело начинается с {"id":
- curl -s http://localhost:8080/preview/<id из ответа> -> 200; в этом HTML: grep -c "@media" = 0; grep -c "var(--" = 0; grep -c "<h2" >= 1; grep -c "<strong>" >= 1
- mvn -q test -> Failures: 0, Errors: 0
- grep -n "style, script" src/main/resources/static/bookmarklet.js -> непусто
- в запущенном jar на демо-чате переносы строк внутри сообщения видны, текст не слит в одну строку

КОНТЕКСТ: две первопричины. Первая: букмарклет и сервер не удаляют узлы style/script/noscript, и санитайзер превращает их содержимое в видимый текст. Вторая: предпросмотр печатает текст сообщения как plain text, markdown и переносы погибают. Точки правки: bookmarklet.js (вырезать служебные узлы из клона ДО снятия innerHTML/innerText); ChatController/ChatStore (удалять служебные узлы ДО jsoup-санитизации; если у санитизированного html нет блочных тегов p, h1-h6, pre, ul, ol, table, blockquote — считать text сообщения markdown и рендерить через commonmark, результат санитизировать); chat-print.html (тело сообщения через th:utext, фолбэк-текст с white-space: pre-wrap). Новая зависимость: org.commonmark:commonmark (актуальная стабильная с Maven Central). НЕ трогать: workflow maven.yml, PortGuard, спиннеры, правила переноса страниц в print.css, LICENSE, /about, секции README кроме одной строки про markdown.

ОКРУЖЕНИЕ: [ -f /workspace/env.sh ] || cat > /workspace/env.sh <<'EOF'
export JAVA_HOME=<фактический путь к Temurin 21>
export PATH=$JAVA_HOME/bin:<путь к maven>/bin:$PATH
EOF
source /workspace/env.sh; java -version -> 21

ШАГИ:
0. Канарейка: grep -q "win-console" .github/workflows/maven.yml && test -f src/main/resources/banner.txt && grep -q "PortGuard" src/main/java/com/bizonrelax/milkromug/MilkromugApplication.java -> всё найдено. Иначе STOP и доклад.
0.5. git fetch origin; git checkout -b ptabc-9 origin/main.
1. Фикстура: создать docs/prompts/fixtures/chat-dirty.json: сообщение 1 с html, содержащим <style>.x{color:red}@media screen{.y{left:1px}}</style> плюс текст; сообщение 2 только с text: строка "# Заголовок", строка "**жирное**", строка "- пункт". Чек-поинт: git commit -m "test: add dirty chat fixture" и пуш.
2. Тест раньше фикса: кейс в MilkromugApplicationTests: POST фикстуры, GET preview, утверждения из ГОТОВНОСТИ (0 вхождений @media, >=1 h2, >=1 strong). mvn -q test -Dtest=MilkromugApplicationTests -> новый кейс КРАСНЫЙ. Чек-поинт: git commit -m "test: reproduce css-leak and raw markdown in preview" и пуш.
3. Микро-фикс A: в серверном коде перед jsoup-санитизацией: Jsoup.parse(html), затем doc.select("style, script, noscript, svg, link, meta").remove(), затем санитизация. Повтор кейса: css-утечка зелёная, markdown ещё красный. Чек-поинт: git commit -m "fix: strip service nodes before sanitize" и пуш.
4. Микро-фикс B: если у санитизированного html нет блочных тегов из списка ГОТОВНОСТИ — html = санитизированный результат рендера text через commonmark; зависимость commonmark в pom. Повтор кейса -> полностью зелёный. Чек-поинт: git commit -m "fix: render plain-text markdown via commonmark" и пуш.
5. Микро-фикс C: в bookmarklet.js вырезать те же служебные узлы из клона узла сообщения до снятия innerHTML и innerText. Проверка: grep из ГОТОВНОСТИ. Чек-поинт: git commit -m "fix: bookmarklet strips style/script before capture" и пуш.
6. Микро-фикс D: в chat-print.html тело сообщения выводить через th:utext из html; фолбэк-блок для чистого text с white-space: pre-wrap. Проверка: демо-предпросмотр в браузере, переносы видны. Чек-поинт: git commit -m "fix: preserve line breaks in preview" и пуш.
7. mvn clean install -> BUILD SUCCESS; все команды ГОТОВНОСТИ зелёные.
8. Публикация: push ptabc-9, PR в main через API, мерж (владелец разрешил), git push origin --delete ptabc-9. Дождаться: CI на main green, ассет Release перезаписан.

СТОП-ПРАВИЛА: красный после микро-фикса -> трассировка: напечатать в отчёт фрагмент санитизированного html (первые 300 символов), архитектуру не менять; девиация от схемы -> стоп и доклад; около 3 вызовов до лимита -> WIP-коммит + REMAINING.md в коммите.

ЗАПРЕТЫ: не трогать maven.yml, PortGuard, спиннеры, print.css, LICENSE, /about; не переписывать букмарклет целиком; не добавлять клиентский рендер markdown (только серверный); не коммитить артефакты сборки.

ПРОТОКОЛ КОНЦА: отчёт не более 30 строк: таблица шаг->статус->хвост вывода, хэши коммитов, долги. Последние две строки строго:
СОСТОЯНИЕ: ЗЕЛЕНО|КРАСНО (tests: пройдено/всего, skipped Z)
ВЕРДИКТ: ПУБЛИКОВАТЬ|НЕ ПУБЛИКОВАТЬ — причина не длиннее 10 слов.
Плюс фраза чистого финиша: "Ветка удалена, PR смержен. Любые кнопки слияния или синхронизации, которые может показывать UI GitHub, недействительны и нажимать их не нужно".
