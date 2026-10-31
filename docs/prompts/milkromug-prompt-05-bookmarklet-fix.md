ЗАЧЕМ: пользователь кликает букмарклет на странице чата и получает непонятную ошибку "Failed to execute 'json' on 'Response': Unexpected token '<'": сервер отвечает на POST /chat HTML-ошибкой (подозрение: 415 из-за Content-Type), а букмарклет слепо зовёт .json(). Захват должен работать от клика до предпросмотра, а ошибки быть человеко-читаемыми.

ЦЕЛЬ: POST /chat принимает JSON-тело с Content-Type application/json И text/plain; букмарклет всегда завершается либо предпросмотром, либо читаемым сообщением об ошибке.

ГОТОВНОСТЬ:
- curl -i -X POST -H "Content-Type: text/plain" --data "{\"title\":\"T\",\"messages\":[{\"author\":\"user\",\"text\":\"Привет\"}]}" http://localhost:8080/chat -> HTTP 200, тело начинается с {"id":
- curl -i -X POST -H "Content-Type: application/json" --data то же -> HTTP 200, тело начинается с {"id":
- curl -i -X POST -H "Accept: text/html" -H "Content-Type: application/json" --data то же -> HTTP 200, тело начинается с <! (legacy-режим)
- mvn -q test -> Failures: 0, Errors: 0
- grep -n "content-type" src/main/resources/static/bookmarklet.js -> непусто (проверка перед .json())

КОНТЕКСТ: в main слит PR #4 (PortGuard, --win-console). Гипотеза: несовпадение Content-Type у букмарклета и consumes у контроллера. Трассировка курлами обязательна ПЕРЕД правкой, вывод вставить в отчёт. Файлы: ChatController.java, bookmarklet.js, MilkromugApplicationTests.java. НЕ трогать: workflow, PortGuard, print.css, спиннеры, LICENSE.

ОКРУЖЕНИЕ: [ -f /workspace/env.sh ] || cat > /workspace/env.sh <<'EOF'
export JAVA_HOME=<фактический путь к Temurin 21 из прошлой сессии>
export PATH=$JAVA_HOME/bin:<путь к maven>/bin:$PATH
EOF
source /workspace/env.sh; java -version -> 21; mvn -v -> 3.9.x

ШАГИ:
0. Канарейка: grep -q "PortGuard" src/main/java/com/bizonrelax/milkromug/MilkromugApplication.java && grep -q "win-console" .github/workflows/maven.yml -> оба найдены. Иначе STOP, доклад владельцу, ничего не править.
0.5. git fetch origin; git checkout -b ptabc-5 origin/main; вся работа только в ptabc-5.
1. Трассировка: собрать jar, запустить, выполнить три курла из ГОТОВНОСТИ, вставить выводы в отчёт. Без коммита.
2. Тест раньше фикса: добавить в MilkromugApplicationTests кейс POST /chat с Content-Type text/plain, ожидание 200 и JSON. mvn -q test -Dtest=MilkromugApplicationTests -> новый кейс КРАСНЫЙ. Чек-поинт: git commit -m "test: reproduce 415 on text/plain POST /chat" и пуш в ptabc-5.
3. Микро-фикс сервера: consumes у POST /chat = application/json + text/plain, тело парсится как JSON; JSON-ответ {id,url} всегда, КРОМЕ ?legacy=html или явного Accept с text/html (звёздочка */* не считается явным). Проверка: три курла из ГОТОВНОСТИ. Чек-поинт: git commit -m "fix: POST /chat accepts text/plain, legacy-html only explicit" и пуш.
4. Микро-фикс букмарклета: перед .json() проверять res.ok и content-type ответа; при не-JSON печатать в открытое окно: статус, первые 200 символов тела, подсказку про запуск Milkromug.exe. Точечная правка, без переписывания. Проверка: grep из ГОТОВНОСТИ + GET /bookmarklet -> 200. Чек-поинт: git commit -m "fix: bookmarklet readable error on non-JSON response" и пуш.
5. Полный прогон: mvn clean install -> BUILD SUCCESS. В отчёт отдельной строкой: имя и причина скипнутого теста из вывода mvn test (владелец задал вопрос).
6. Публикация: push ptabc-5, PR в main через API, мерж (владелец разрешил), затем git push origin --delete ptabc-5. Дождаться CI на main: green и ассет Release перезаписан.

СТОП-ПРАВИЛА: красный после микро-фикса -> трассировка с печатью стадии в отчёт, архитектуру не менять; нужна девиация от контракта выше -> стоп и доклад; осталось около 3 вызовов -> коммит WIP + файл REMAINING.md в коммите.

ЗАПРЕТЫ: не откатывать чужие изменения; не трогать workflow maven.yml, PortGuard, print.css, спиннеры, LICENSE; не вводить второй механизм парсинга JSON; не переписывать букмарклет целиком.

ПРОТОКОЛ КОНЦА: отчёт не более 30 строк: таблица шаг->статус->хвост вывода, хэши коммитов, долги. Последние две строки строго:
СОСТОЯНИЕ: ЗЕЛЕНО|КРАСНО (tests: пройдено/всего, skipped Z)
ВЕРДИКТ: ПУБЛИКОВАТЬ|НЕ ПУБЛИКОВАТЬ — причина не длиннее 10 слов.
Плюс фраза чистого финиша: "Ветка удалена, PR смержен. Любые кнопки слияния или синхронизации, которые может показывать UI GitHub, недействительны и нажимать их не нужно".
