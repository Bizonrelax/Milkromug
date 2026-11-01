ЗАЧЕМ: владелец — новичок-пользователь с философией «запустил и пользуешься, программа сама всему учит». Полевые отчёты показали: захват на chat.qwen.ai и share.google/aimode тянет служебный мусор и теряет роли; интерфейсу не хватает подсказок; букмарклет трудно ставить без панели закладок; владелец хочет бегущую лошадь рядом с кружкой и ярлык на рабочем столе с горячей клавишей. Промт закрывает UX-разрыв и качество захвата регрессиями из примеров владельца.

ЦЕЛЬ: exe сам открывает дашборд в браузере; на дашборде две большие кнопки быстрого старта; все активные элементы имеют подсказки; кнопка Ярлык создаёт desktop-ярлык с CTRL+SHIFT+G; баннер состоит из кружки и оригинальной бегущей лошади; markdown-расширения и офлайн-подсветка кода работают; регрессии aimode/qwen закрыты тестами; PR #8 закрыт.

ГОТОВНОСТЬ:
- curl -s http://localhost:8080/ | grep -c "quickstart-btn" -> 2 или больше
- curl -s http://localhost:8080/bookmarklet | grep -c "desktop-shortcut" -> 1 или больше
- curl -s http://localhost:8080/ | grep -c "title=" -> 5 или больше
- awk "length>80" src/main/resources/banner.txt -> пусто; grep -c "MILKROMUG" src/main/resources/banner.txt -> 1 или больше; test -f docs/ascii-collection/horse.txt && test -f docs/ascii-collection/mug.txt -> истина
- POST фикстуры с блоками php и python и markdown-таблицей на /chat, затем GET preview: grep -c "language-php" -> 1 или больше; grep -c "<table" -> 1 или больше; grep -c "highlight" -> 1 или больше
- POST фикстуры aimode-junk (message html со script-текстом google.sn=) на /chat, затем GET preview: grep -c "google.sn=" -> 0
- mvn -q test -> Failures: 0, Errors: 0
- GitHub API: pull request 8 имеет state=closed

КОНТЕКСТ: в main слиты PR #9 и #10 (readable export + хотфикс): commonmark в pom, вырезание style/script, серверный рендер markdown. Маркеры канарейки: grep -q "commonmark" pom.xml и test -f src/main/resources/banner.txt. Лошадь — только оригинальная отрисовка кодера (плагиат запрещён владельцем); кружку не перерисовывать и не удалять (коллекция владельца). Ярлык создаётся локальным сервером через PowerShell WScript.Shell (.lnk, TargetPath=http://localhost:8080/bookmarklet, Hotkey=CTRL+SHIFT+G); на не-Windows и в headless — дружелюбный JSON ok:false без падения. Автооткрытие браузера: ApplicationReadyEvent, Desktop.browse,-guard headless и аргументом --no-open, в try/catch. Подсветка: org.webjars highlightjs внутрь jar (офлайн), theme один, hljs.highlightAll на chat-print.html; санитайзер сохраняет class у pre и code.

ОКРУЖЕНИЕ: [ -f /workspace/env.sh ] || cat > /workspace/env.sh <<'EOF'
export JAVA_HOME=<фактический путь к Temurin 21 из прошлых сессий>
export PATH=$JAVA_HOME/bin:<путь к maven>/bin:$PATH
EOF
source /workspace/env.sh; java -version -> 21

ШАГИ:
0. Канарейка: grep -q "commonmark" pom.xml && test -f src/main/resources/banner.txt && grep -q "PortGuard" src/main/java/com/bizonrelax/milkromug/MilkromugApplication.java -> все найдены. Иначе STOP и доклад.
0.5. git fetch origin; git checkout -b ptabc-10 origin/main.
1. Закрыть PR #8 через GitHub API (state=closed, комментарий: закрыт как ненужный no-op; цикл ветки закрыт удалением ветки; по промту-10). Проверка: API GET pulls/8 -> closed. Чек-поинт не требуется (действие в API, не в коде).
2. Баннер: к текущей кружке добавить разделитель и оригинальную бегущую лошадь (сам отрисуй, блестки/грива декоративными символами, не шире 80 колонок, не пьёт из кружки). Проверка: команды ГОТОВНОСТИ про banner.txt. Чек-поинт: git commit -m "feat: dual banner — mug collection + original running horse" и пуш.
3. docs/ascii-collection/mug.txt и horse.txt с шапкой "original ASCII for Milkromug, Unlicense". Чек-поинт: git commit -m "docs: ascii collection mug and horse" и пуш.
4. Автооткрытие браузера на http://localhost:8080/ после старта (guard: headless, --no-open, try/catch, лог-строка). Проверка: запуск jar в headless -> старт без падений и лог о пропуске открытия. Чек-поинт: git commit -m "feat: open dashboard in browser on start" и пуш.
5. Дашборд: две большие кнопки-ссылки с классом quickstart-btn на index: Настроить захват (/bookmarklet) и О программе (/about); демо-кнопку сохранить. Проверка: grep quickstart-btn >=2. Чек-поинт: git commit -m "feat(ui): quickstart buttons on dashboard" и пуш.
6. Тултипы title на всех кнопках, спиннерах и ссылках в index.html, bookmarklet.html, chat-print.html, about.html. Проверка: grep title= >=5 на /. Чек-поинт: git commit -m "feat(ui): tooltips on interactive elements" и пуш.
7. Кнопка Ярлык на /bookmarklet + POST /desktop-shortcut: Windows -> PowerShell WScript.Shell создаёт Milkromug Capture.lnk на рабочем столе с Hotkey CTRL+SHIFT+G; ответ JSON {ok, path или error}; не-Windows/headless -> ok:false с дружелюбным текстом. Проверка: MockMvc-тест JSON ok-поля; локально на linux ok:false без 500. Чек-поинт: git commit -m "feat: desktop shortcut with hotkey from bookmarklet page" и пуш.
8. Классификация тегов: commonmark-расширения tables, strikethrough, autolink; webjars highlightjs внутрь jar; chat-print.html подключает локальный hljs и тему, hljs.highlightAll; санитайзер сохраняет class у pre/code. Проверка: команды ГОТОВНОСТИ про language-php и table. Чек-поинт: git commit -m "feat: markdown extensions and offline syntax highlighting" и пуш.
9. Регрессии поля: фикстура aimode-junk (script-текст внутри html сообщения) -> preview без google.sn=; фикстура qwen-order (чередование user/assistant) -> порядок ролей сохранён. Тесты красные ДО фикса, зелёные после. Чек-поинт: git commit -m "test: field regressions aimode junk and qwen order" и пуш.
10. mvn clean install -> BUILD SUCCESS; все команды ГОТОВНОСТИ зелёные.
11. Публикация: push ptabc-10, PR в main через API, мерж (владелец разрешил), git push origin --delete ptabc-10. Дождаться: CI на main green, ассет Release перезаписан. No-op PR не создавать.

СТОП-ПРАВИЛА: красный после микро-фикса -> трассировка с печатью стадии в отчёт, архитектуру не менять; девиация от сниппетов PowerShell/hljs -> стоп и доклад; около 3 вызовов до лимита -> WIP-коммит + REMAINING.md в коммите.

ЗАПРЕТЫ: не удалять и не перерисовывать кружку; не заимствовать ASCII из интернета; не трогать PortGuard, LICENSE, print.css, workflow maven.yml; не добавлять CDN (только офлайн); не создавать no-op PR; не коммитить артефакты сборки.

ПРОТОКОЛ КОНЦА: отчёт не более 30 строк: таблица шаг->статус->хвост вывода, хэши коммитов, долги. Последние две строки строго:
СОСТОЯНИЕ: ЗЕЛЕНО|КРАСНО (tests: пройдено/всего, skipped Z)
ВЕРДИКТ: ПУБЛИКОВАТЬ|НЕ ПУБЛИКОВАТЬ — причина не длиннее 10 слов.
Плюс фраза чистого финиша: "Ветка удалена, PR смержен. Любые кнопки слияния или синхронизации, которые может показывать UI GitHub, недействительны и нажимать их не нужно".
