ЗАЧЕМ: после слияния PR #21 владелец на новом билде снова получает HTTP 400 при захвате живого чата. Дружелюбный 400 сервера уже содержит причину Jackson и первые 200 символов payload, но букмарклет в ветке !resp.ok читает тело ответа и НЕ прикрепляет его к ошибке, поэтому оверлей показывает голый статус; владелец вынужден охотиться за строкой в консоли вручную. Плюс две гигиенические задолженности: ведущие пробелы в хвосте .gitignore ломают шесть шаблонов; ромб-неудачник архитектора ещё не лежит в коллекции.

ЦЕЛЬ: при любом неудачном POST /chat причина и фрагмент payload видны в трёх местах сразу: оверлей браузера, консоль Milkromug, файл в temp-каталоге; строка замены слэша в sanitizeText проверена побайтово и закрыта регрессией; контроллер парсит тем же ObjectMapper, что и остальное приложение; .gitignore работает; ромб в коллекции.

ГОТОВНОСТЬ:
- grep -n "e.raw = text" src/main/resources/static/bookmarklet.js внутри ветки !resp.ok -> непусто (тело ответа прикрепляется к ошибке до throw)
- grep -rc "milkromug-bad-payload" src/main/java -> 1 или больше
- grep -n 's = s.replace(/\\\\/g, String.fromCharCode(0xFF3C));' src/main/resources/static/bookmarklet.js -> ровно 1 совпадение (побайтово два слэша внутри regex)
- awk '/^[ \t]+\*/' .gitignore -> пусто; grep -n "Thumbs.db" .gitignore -> строка начинается с первой колонки
- test -f docs/ascii-collection/failed-gwen-rhombus.txt -> истина
- mvn -q test -> Failures: 0, Errors: 0
- вручную: клик захвата на длинном чате при 400 показывает в оверлее текст с "Причина:" и путём к файлу payload

КОНТЕКСТ: аудит main: дружелюбный 400 в ChatController уже логирует в stderr и возвращает причину плюс первые 200 символов; дыра клиентская: в ветке !resp.ok переменная text читается, но к ошибке не прикрепляется (extra=null). Серверная добавка: в catch Jackson писать rawBody (обрезать до 512 КБ) в файл milkromug-bad-payload-<epoch>.txt в каталоге из свойства milkromug.bad-payload-dir (по умолчанию java.io.tmpdir), логировать абсолютный путь и дописывать в тело 400 фразу "Файл с полным payload: <путь>". Контроллер: заменить new ObjectMapper() на внедрённый бин ObjectMapper (иначе StreamReadConstraints из JacksonConfig не действуют на разбор входящего чата). sanitizeText: строка замены обратного слэша должна побайтово быть s = s.replace(/\\/g, String.fromCharCode(0xFF3C)); с двумя слэшами внутри regex; если в файле она искажена (пустой regex или съеденный комментарий) — исправить. .gitignore: хвостовой блок владельца переписать без ведущих пробелов, если они ещё есть. Ромб: файл docs/ascii-collection/failed-gwen-rhombus.txt с шапкой "Gwen's failed horse (reads as rhombus), history of attempts, Unlicense, 2026-11-03" и артом архитектора (владелец мог создать сам — сначала проверить наличие); на баннер НЕ добавлять.

ОКРУЖЕНИЕ: [ -f /workspace/env.sh ] || cat > /workspace/env.sh <<'EOF'
export JAVA_HOME=/opt/jdk-21.0.13+9
export PATH=$JAVA_HOME/bin:/opt/apache-maven-3.9.6/bin:$PATH
EOF
source /workspace/env.sh; java -version -> 21; mvn -v -> 3.9.x

ШАГИ:
0. Канарейка: grep -rq "StreamReadConstraints" src/main/java && grep -q "sanitizeText" src/main/resources/static/bookmarklet.js -> оба найдены. Иначе STOP и доклад.
0.5. git fetch origin; git checkout -b ptabc-18 origin/main.
0.6. Наследование: git status --porcelain и git log --oneline @{upstream}..HEAD; грязное или непушнутое прошлой сессией — сначала WIP-коммит "wip: resume handoff" и пуш.
1. Тест раньше фикса: Prompt18Test: (а) читаемый bookmarklet.js содержит побайтовую строку regex со слэшем; (б) MockMvc POST /chat с телом из \r, управляющих символов, кавычек, слэшей и U+2028 -> 200; (в) битый JSON -> 400, тело содержит "Первые 200 символов" И создан файл bad-payload в temp-каталоге. mvn -q test -Dtest=Prompt18Test -> красные нужные кейсы. Чек-поинт: git commit -m "test: reproduce 400 diagnostics gaps" и СРАЗУ пуш.
2. Микро-фикс клиента: в ветке !resp.ok прикреплять e.raw = text перед throw; оверлей уже печатает extra. Чек-поинт: git commit -m "fix: bookmarklet shows server reason on http errors" и СРАЗУ пуш.
3. Микро-фикс сервера: дамп bad-payload в файл с свойством milkromug.bad-payload-dir, путь в лог и в тело 400; внедрённый ObjectMapper вместо new ObjectMapper(). Чек-поинт: git commit -m "fix: bad payload dump and shared object mapper" и СРАЗУ пуш.
4. Микро-фикс sanitizeText: побайтовая строка слэша; регрессия из шага 1а зелёная. Чек-поинт: git commit -m "fix: sanitizeText backslash regex byte-exact" и СРАЗУ пуш.
5. Гигиена: .gitignore без ведущих пробелов (если ещё есть), failed-gwen-rhombus.txt (если отсутствует). Чек-поинт: git commit -m "chore: gitignore spaces and failed rhombus in collection" и СРАЗУ пуш.
6. mvn clean install -> BUILD SUCCESS; все команды ГОТОВНОСТИ зелёные.
7. Публикация: push ptabc-18, PR в main через API, мерж (владелец разрешил), git push origin --delete ptabc-18. Дождаться: CI на main green, ассет Release перезаписан.

СТОП-ПРАВИЛА: красный после микро-фикса -> трассировка с печатью стадии в отчёт, архитектуру не менять; девиация от схемы -> стоп и доклад; около 3 вызовов до лимита -> WIP-коммит и СРАЗУ пуш + REMAINING.md в коммите.

ЗАПРЕТЫ: не менять селекторы и эвристики букмарклета; не трогать лимиты JacksonConfig и workflow maven.yml; не добавлять ромб на баннер; не создавать no-op PR; не коммитить артефакты сборки.

ПРОТОКОЛ КОНЦА: отчёт не более 30 строк: таблица шаг->статус->хвост вывода, хэши коммитов, долги. Последние две строки строго:
СОСТОЯНИЕ: ЗЕЛЕНО|КРАСНО (tests: пройдено/всего, skipped Z)
ВЕРДИКТ: ПУБЛИКОВАТЬ|НЕ ПУБЛИКОВАТЬ — причина не длиннее 10 слов.
Плюс фраза чистого финиша: "Ветка удалена, PR смержен. Любые кнопки слияния или синхронизации, которые может показывать UI GitHub, недействительны и нажимать их не нужно".
