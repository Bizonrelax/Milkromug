ЗАЧЕМ: полевое тестирование владельцем выявило четыре дефекта готовой сборки: браузер не открывается сам, кнопка Ярлык отдаёт ошибку headless, подсказку о перетаскивании нельзя убрать, захват чата qwen даёт повторяющиеся блоки, а печать в PDF начинает с пустой страницы. Плюс в репозитории остались два снимка рабочих папок умерших сессий. Чиним по трассируемым первопричинам и убираем мусор.

ЦЕЛЬ: двойной клик по Milkromug.exe открывает дашборд в браузере по умолчанию; кнопка Создать ярлык на /bookmarklet создаёт .lnk с CTRL+SHIFT+G на реальном рабочем столе Windows; подсказка закрываема и запоминает закрытие; захват chat.qwen.ai не даёт подряд-дубликатов блоков; предпросмотр печатается без пустой первой страницы; в репозитории после финиша только main.

ГОТОВНОСТЬ:
- grep -n "spring.main.headless" src/main/resources/application.properties -> spring.main.headless=false
- curl -s http://localhost:8080/bookmarklet | grep -c "hint-close" -> 1 или больше
- curl -s -X POST http://localhost:8080/desktop-shortcut -> HTTP 200 и тело содержит "ok": (true на машине с рабочим столом, false с дружелюбным текстом в headless CI, но не 500)
- curl -s -X POST -H "Content-Type: application/json" --data @docs/prompts/fixtures/chat-dupes.json http://localhost:8080/chat затем curl -s http://localhost:8080/preview/<id> | grep -c "DUPE-MARKER" -> ровно 1
- grep -n "min-height\|min-block-size" src/main/resources/static/css/print.css внутри блока media print -> пусто; владелец вручную: первая страница печати не пустая
- git ls-remote origin | grep -c "milkromug-ux-launch-prompt-cdd50\|github-repository-milkromug-file-42d" -> 0
- mvn -q test -> Failures: 0, Errors: 0

КОНТЕКСТ: первопричина пунктов 1 и 2 одна — Spring Boot ставит java.awt.headless=true по умолчанию (spring.main.headless), поэтому GraphicsEnvironment.isHeadless() истинно и ветки Desktop пропускаются; лечить свойством, логику BrowserAutoOpen и DesktopShortcutController не переписывать. Дубликаты: DOM chat.qwen.ai вкладывает контейнеры сообщений друг в друга, селектор матчит внешний и внутренний узлы; лечить выбором только самых глубоких совпавших узлов плюс серверная страховка: ChatStore отбрасывает сообщение, равное предыдущему по нормализованному тексту. Пустая страница: экранный min-height простыни тянется в print; убрать только под media print, экранные стили не трогать.

ОКРУЖЕНИЕ: [ -f /workspace/env.sh ] || cat > /workspace/env.sh <<'EOF'
export JAVA_HOME=<фактический путь к Temurin 21 из прошлых сессий>
export PATH=$JAVA_HOME/bin:<путь к maven>/bin:$PATH
EOF
source /workspace/env.sh; java -version -> 21

ШАГИ:
0. Канарейка: grep -q "quickstart-btn" src/main/resources/templates/index.html && grep -rq "desktop-shortcut" src/main/java -> оба найдены. Иначе STOP и доклад.
0.5. git fetch origin; git checkout -b ptabc-11 origin/main.
1. Микро-фикс A: добавить spring.main.headless=false в application.properties с комментарием зачем. Проверка: локальный запуск jar в среде с рабочим столом -> в логе строка попытки открытия браузера, браузер открывается. Чек-поинт: git commit -m "fix: disable Spring headless so Desktop and shortcut work" и пуш.
2. Микро-фикс B: подсказка на /bookmarklet и index: кнопка-крестик с id hint-close и чекбокс больше не показывать (localStorage), автоскрытие после успешного захвата. Проверка: grep hint-close. Чек-поинт: git commit -m "fix: dismissible raise hint with memory" и пуш.
3. Тест раньше фикса: фикстура docs/prompts/fixtures/chat-dupes.json (вложенные контейнеры с одним текстом DUPE-MARKER) и кейс в MilkromugApplicationTests: preview содержит DUPE-MARKER ровно один раз; mvn -q test -Dtest=MilkromugApplicationTests -> кейс КРАСНЫЙ. Чек-поинт: git commit -m "test: reproduce duplicate blocks from nested containers" и пуш.
4. Микро-фикс C: в bookmarklet.js после сбора узлов отбрасывать любой узел, содержащий другой совпавший узел; в ChatStore отбрасывать сообщение, равное предыдущему по нормализованному тексту. Повтор кейса -> зелёный. Чек-поинт: git commit -m "fix: deepest-only selection and consecutive dedupe" и пуш.
5. Микро-фикс D: в print.css под media print убрать min-height и min-block-size у простыни и добавить break-inside: auto; экранные правила не трогать. Проверка: grep из ГОТОВНОСТИ пуст. Чек-поинт: git commit -m "fix: no blank first page in print" и пуш.
6. Уборка: удалить через GitHub API ветки milkromug-ux-launch-prompt-cdd50 и github-repository-milkromug-file-42d (любой суффикс). Проверка: git ls-remote grep -> 0.
7. mvn clean install -> BUILD SUCCESS; все команды ГОТОВНОСТИ зелёные.
8. Публикация: push ptabc-11, PR в main через API, мерж (владелец разрешил), git push origin --delete ptabc-11. Дождаться: CI на main green, ассет Release перезаписан.

СТОП-ПРАВИЛА: красный после микро-фикса -> трассировка с печатью стадии в отчёт, архитектуру не менять; девиация от схемы -> стоп и доклад; около 3 вызовов до лимита -> WIP-коммит + REMAINING.md в коммите.

ЗАПРЕТЫ: не переписывать BrowserAutoOpen и DesktopShortcutController целиком (лечим свойством); не трогать PortGuard, banner.txt, highlight.js, workflow maven.yml, LICENSE; не создавать no-op PR; не коммитить артефакты сборки.

ПРОТОКОЛ КОНЦА: отчёт не более 30 строк: таблица шаг->статус->хвост вывода, хэши коммитов, долги. Последние две строки строго:
СОСТОЯНИЕ: ЗЕЛЕНО|КРАСНО (tests: пройдено/всего, skipped Z)
ВЕРДИКТ: ПУБЛИКОВАТЬ|НЕ ПУБЛИКОВАТЬ — причина не длиннее 10 слов.
Плюс фраза чистого финиша: "Ветка удалена, PR смержен. Любые кнопки слияния или синхронизации, которые может показывать UI GitHub, недействительны и нажимать их не нужно".
