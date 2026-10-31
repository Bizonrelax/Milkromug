ЗАЧЕМ: две проверки живут вне CI: тест синтаксиса букмарклета скипается без Node.js, а собранный exe никто не запускает после сборки. Пользователь Windows 11 должен получать гарантированно рабочий exe, а синтаксис букмарклета должен проверяться на каждом коммите.

ЦЕЛЬ: в CI на main тест BookmarkletSyntaxTest выполняется (Skipped: 0), а после jpackage раннер сам запускает Milkromug.exe и получает от него HTTP 200.

ГОТОВНОСТЬ:
- grep -n "setup-node" .github/workflows/maven.yml -> непусто
- grep -n "NODE_AVAILABLE" .github/workflows/maven.yml -> непусто
- grep -n "smoke" .github/workflows/maven.yml -> непусто
- лог последнего успешного прогона на main: строка Tests run: 17, Failures: 0, Errors: 0, Skipped: 0
- лог того же прогона: шаг smoke содержит HTTP 200 от запущенного Milkromug.exe и остановку процесса
- ассет Release перезаписан после мерджа

КОНТЕКСТ: workflow сейчас: windows-latest, Temurin 21, кэш m2, mvn package, jpackage app-image в RUNNER_TEMP, zip, upload-artifact, release на push в main. Правки ТОЛЬКО в maven.yml. Node брать actions/setup-node@v4, версия 20. Smoke-шаг: Start-Process exe из app-image, цикл ожидания 200 на http://localhost:8080/ с таймаутом 60 секунд, затем Stop-Process -Name Milkromug; шаг падает, если 200 не получено.

ОКРУЖЕНИЕ: [ -f /workspace/env.sh ] || cat > /workspace/env.sh <<'EOF'
export JAVA_HOME=<фактический путь к Temurin 21>
export PATH=$JAVA_HOME/bin:<путь к maven>/bin:$PATH
EOF
source /workspace/env.sh; java -version -> 21

ШАГИ:
0. Канарейка: test -f src/main/resources/templates/about.html && grep -q "banner.txt" src/main/resources/banner.txt 2>/dev/null; достаточно существования about.html (маркер PR #6). Иначе STOP и доклад.
0.5. git fetch origin; git checkout -b ptabc-7 origin/main.
1. Добавить setup-node@v4 перед тестовым шагом и NODE_AVAILABLE=1 в env шага mvn. Локально: NODE_AVAILABLE=1 mvn -q test -> Skipped: 0. Чек-поинт: git commit -m "ci: run bookmarklet syntax test with node" и пуш.
2. Добавить smoke-шаг после упаковки zip по схеме из КОНТЕКСТА. Локально схему не проверить (нет Windows) — пометить в отчёте, что проверка отложена до CI. Чек-поинт: git commit -m "ci: smoke-launch packaged exe on runner" и пуш.
3. Публикация: PR ptabc-7 в main через API, мерж, git push origin --delete ptabc-7. Дождаться прогона на main: обе новые проверки зелёные, Release перезаписан. Если smoke-шаг красный -> трассировка по логу, микро-фикс таймаутов/путей, повтор.

СТОП-ПРАВИЛА: красный после микро-фикса -> трассировка по логу CI в отчёт; девиация от ГОТОВНОСТИ -> стоп и доклад; около 3 вызовов до лимита -> WIP-коммит + REMAINING.md.

ЗАПРЕТЫ: не трогать код приложения и тесты (правки только maven.yml), PortGuard, LICENSE, .gitignore, флаги jpackage, релиз-шаг softprops.

ПРОТОКОЛ КОНЦА: отчёт не более 30 строк: таблица шаг->статус->хвост вывода, ссылки на run, хэши, долги. Последние две строки строго:
СОСТОЯНИЕ: ЗЕЛЕНО|КРАСНО (tests: пройдено/всего, skipped Z)
ВЕРДИКТ: ПУБЛИКОВАТЬ|НЕ ПУБЛИКОВАТЬ — причина не длиннее 10 слов.
Плюс фраза чистого финиша: "Ветка удалена, PR смержен. Любые кнопки слияния или синхронизации, которые может показывать UI GitHub, недействительны и нажимать их не нужно".
