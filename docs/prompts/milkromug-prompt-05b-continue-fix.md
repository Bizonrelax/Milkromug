ЗАЧЕМ: прошлый прогон корректно остановился на красном регрессионном тесте и запретил публикацию; гипотеза фикса не подтверждена трассировкой, правка осталась некоммиченной и потеряна. Продолжаем в ветке ptabc-5: трассируем причину красного теста, доводим до зелени микро-фиксами и публикуем. Мусорная ветка рабочей папки должна исчезнуть.

ЦЕЛЬ: ветка ptabc-5 зелёная по всем командам готовности, смержена в main; ветка milkromug-bookmarklet-fix-fee64 удалена; в отчёте есть имя и причина скипнутого теста.

ГОТОВНОСТЬ:
- curl -i -X POST -H "Content-Type: text/plain" --data "{\"title\":\"T\",\"messages\":[{\"author\":\"user\",\"text\":\"Привет\"}]}" http://localhost:8080/chat -> HTTP 200, тело начинается с {"id":
- curl -i -X POST -H "Content-Type: application/json" --data то же -> HTTP 200, тело начинается с {"id":
- curl -i -X POST -H "Accept: text/html" -H "Content-Type: application/json" --data то же -> HTTP 200, тело начинается с <!
- mvn -q test -> Failures: 0, Errors: 0; строка отчёта: имя и причина скипнутого теста
- grep -n "content-type" src/main/resources/static/bookmarklet.js -> непусто
- git ls-remote origin milkromug-bookmarklet-fix-fee64 -> пусто (ветка удалена)

КОНТЕКСТ: в ptabc-5 есть коммит с красным регрессионным тестом контракта text/plain. Фикс сервера переписать заново ПОСЛЕ трассировки. Подозреваемые: consumes не включает text/plain; тело не парсится как JSON при text/plain; legacy-проверка ловит Accept: */*. Целевые файлы: ChatController.java, bookmarklet.js. НЕ трогать: workflow maven.yml, PortGuard, print.css, спиннеры, LICENSE, .gitignore.

ОКРУЖЕНИЕ: [ -f /workspace/env.sh ] || cat > /workspace/env.sh <<'EOF'
export JAVA_HOME=<фактический путь к Temurin 21 из прошлых сессий>
export PATH=$JAVA_HOME/bin:<путь к maven>/bin:$PATH
EOF
source /workspace/env.sh; java -version -> 21; mvn -v -> 3.9.x

ШАГИ:
0. Канарейка: git fetch origin; git checkout ptabc-5; grep -q "PortGuard" src/main/java/com/bizonrelax/milkromug/MilkromugApplication.java && grep -q "text/plain" src/test/java/com/bizonrelax/milkromug/MilkromugApplicationTests.java -> оба найдены. Иначе STOP и доклад, ничего не править.
1. Трассировка без коммита: mvn -q test -Dtest=MilkromugApplicationTests -> зафиксировать красный кейс; запустить jar; выполнить первые два курла из ГОТОВНОСТИ, вставить статусы и тела в отчёт; сформулировать причину красноты одним предложением.
2. Микро-фикс сервера: consumes у POST /chat = application/json + text/plain; тело принимать как String и парсить Jackson; JSON-ответ {id,url} всегда, КРОМЕ ?legacy=html или явного Accept с text/html (*/* не считается явным). Повторить красный кейс -> зелёный. Чек-поинт: git commit -m "fix: POST /chat accepts text/plain (trace: <причина>)" и пуш в ptabc-5.
3. Микро-фикс букмарклета: перед .json() проверять res.ok и content-type ответа; при не-JSON печатать в открытое окно статус, первые 200 символов тела и подсказку про запуск Milkromug.exe. Точечно, без переписывания. Чек-поинт: git commit -m "fix: bookmarklet readable error on non-JSON" и пуш.
4. mvn clean install -> BUILD SUCCESS, Failures: 0. Три курла из ГОТОВНОСТИ -> 200 JSON, 200 JSON, 200 HTML.
5. Публикация: push ptabc-5, PR в main через API, мерж (владелец разрешил), затем git push origin --delete ptabc-5 и git push origin --delete milkromug-bookmarklet-fix-fee64. Дождаться: CI на main green, ассет Release перезаписан.

СТОП-ПРАВИЛА: красный после микро-фикса -> трассировка с печатью стадии в отчёт, архитектуру не менять; девиация от контракта -> стоп и доклад; около 3 вызовов до лимита -> WIP-коммит + REMAINING.md в коммите.

ЗАПРЕТЫ: не коммитить файлы из target/ и любые артефакты сборки; не править .gitignore; не откатывать чужие изменения; не трогать workflow, PortGuard, print.css, спиннеры, LICENSE; не переписывать букмарклет целиком; не мержить milkromug-bookmarklet-fix-fee64.

ПРОТОКОЛ КОНЦА: отчёт не более 30 строк: таблица шаг->статус->хвост вывода, причина красноты из шага 1, имя и причина скипнутого теста, хэши коммитов, долги. Последние две строки строго:
СОСТОЯНИЕ: ЗЕЛЕНО|КРАСНО (tests: пройдено/всего, skipped Z)
ВЕРДИКТ: ПУБЛИКОВАТЬ|НЕ ПУБЛИКОВАТЬ — причина не длиннее 10 слов.
Плюс фраза чистого финиша: "Ветка удалена, PR смержен. Любые кнопки слияния или синхронизации, которые может показывать UI GitHub, недействительны и нажимать их не нужно".
