ЗАЧЕМ: полевое тестирование релиза после PR #15-#16 выявило четыре дефекта готовой сборки: 1) в консоли вместо сообщения BrowserAutoOpen крокозябры из-за несовпадения кодировок (Java UTF-8, консоль Windows CP866/CP1251); 2) перетаскивание кнопки на рабочий стол создаёт .url-ярлык, который не умеет открывать javascript:-протокол, пользователь получает «Для протокола javascript не зарегистрирована программа»; 3) горячая клавиша CTRL+SHIFT+G конфликтует со встроенным хоткеем Chrome «Найти на странице» (Ctrl+Shift+G), поэтому сочетание не запускает Milkromug; 4) в репозитории осталась мусорная ветка-сирота рабочей папки github-repository-prompt-loader-1f635.

ЦЕЛЬ: вся диагностика в консоли на английском; кнопка «Создать ярлык» создаёт настоящий .lnk с TargetPath=http://localhost:8080/bookmarklet, а не javascript:-однострочник; горячая клавиша в .lnk — CTRL+ALT+M (свободная в системе); мусорная ветка удалена.

ГОТОВНОСТЬ:
- grep -rn "Откр\|пропущ\|headless" src/main/java --include="*.java" -> пусто (никакой кириллицы в диагностике BrowserAutoOpen и PortGuard)
- grep -n "Browser open skipped" src/main/java/com/bizonrelax/milkromug/config/BrowserAutoOpen.java -> непусто
- grep -n "PortGuard" src/main/java --include="*.java" -r | head -5 -> в лог-сообщениях только английский
- grep -rn "CTRL+SHIFT+G\|CTRL\+SHIFT\+G\|Ctrl+Shift+G" src/main/resources/templates/ --include="*.html" -> пусто; grep -rn "CTRL+ALT+M\|CTRL\+ALT\+M\|Ctrl+Alt+M" src/main/resources/templates/ --include="*.html" -> непусто
- grep -rn "Hotkey\|hotkey" src/main/java --include="*.java" | grep -i "ctrl+alt+m\|ctrlaltm" -> непусто
- curl -s -X POST http://localhost:8080/desktop-shortcut -> JSON ok:true (или ok:false с диагностикой, но не 500); локальный запуск: файл на рабочем столе имеет расширение .lnk, TargetPath содержит http://localhost:8080/bookmarklet, Hotkey содержит Ctrl+Alt+M
- git ls-remote origin | grep -c "github-repository-prompt-loader-1f635" -> 0
- mvn -q test -> Failures: 0, Errors: 0

КОНТЕКСТ: в main слиты PR #15 и #16 (aimode select, loader, glossary, banner). Маркеры канарейки ниже. Консольная кодировка: вся диагностическая печать в BrowserAutoOpen и PortGuard — только английский текст (без русских строк). Ярлык: DesktopShortcutController вызывает PowerShell WScript.Shell, создаёт на рабочем столе Milkromug.lnk (имя короткое), TargetPath="http://localhost:8080/bookmarklet", Hotkey="CTRL+ALT+M". Старый javascript:-ярлык на странице /bookmarklet остаётся как кнопка-перетаскивание в панель закладок (это отдельный механизм, браузерный), но для ярлыка на рабочем столе используется .lnk. Горячая клавиша: в bookmarklet.html заменить везде упоминания CTRL+SHIFT+G на CTRL+ALT+M; в about.html, glossary.html, README.md — то же; проверить что нигде в коде/шаблонах/README нет упоминания старой комбинации. Ветка-сирота: удалить через GitHub API ветку github-repository-prompt-loader-1f635 (и все похожие по маске github-repository-*).

ОКРУЖЕНИЕ: [ -f /workspace/env.sh ] || cat > /workspace/env.sh <<'EOF'
export JAVA_HOME=<фактический путь к Temurin 21>
export PATH=$JAVA_HOME/bin:<путь к maven>/bin:$PATH
EOF
source /workspace/env.sh; java -version -> 21

ШАГИ:
0. Канарейка: grep -q "loader" src/main/resources/templates/bookmarklet.html && grep -q "glossary" src/main/resources/templates/index.html && test -f src/main/resources/banner.txt -> всё найдено. Иначе STOP и доклад.
0.5. git fetch origin; git checkout -b ptabc-14 origin/main.
1. Микро-фикс A: заменить всю кириллицу в лог-сообщениях BrowserAutoOpen и PortGuard на английский (см. список из ГОТОВНОСТИ). Проверка: grep из ГОТОВНОСТИ. Чек-поинт: git commit -m "fix: console diagnostics in English (no mojibake)" и СРАЗУ пуш.
2. Микро-фикс B: DesktopShortcutController создаёт Milkromug.lnk с TargetPath="http://localhost:8080/bookmarklet", Hotkey="CTRL+ALT+M". Проверка: curl из ГОТОВНОСТИ. Чек-поинт: git commit -m "fix: desktop shortcut opens bookmarklet page, not javascript" и СРАЗУ пуш.
3. Микро-фикс C: заменить упоминания CTRL+SHIFT+G на CTRL+ALT+M во всех шаблонах и README. Проверка: grep из ГОТОВНОСТИ. Чек-поинт: git commit -m "fix: hotkey Ctrl+Alt+M (was busy in Chrome)" и СРАЗУ пуш.
4. Удаление мусорной ветки github-repository-prompt-loader-1f635 через GitHub API; если её нет — тихо пройти. Проверка: git ls-remote grep -> 0. Чек-поинт не требуется (действие в API).
5. mvn clean install -> BUILD SUCCESS; все команды ГОТОВНОСТИ зелёные; локальный прогон: поднять jar, POST /desktop-shortcut, проверить что в консоли нет крокозябр.
6. Публикация: push ptabc-14, PR в main через API, мерж (владелец разрешил), git push origin --delete ptabc-14. Дождаться: CI на main green, ассет Release перезаписан.

СТОП-ПРАВИЛА: красный после микро-фикса -> трассировка с печатью стадии в отчёт; девиация от схемы -> стоп и доклад; около 3 вызовов до лимита -> WIP-коммит + СРАЗУ пуш + REMAINING.md в коммите.

ЗАПРЕТЫ: не трогать селекторы aimode, loader, glossary, баннер из PR #15-#16; не трогать workflow maven.yml, LICENSE, print.css; не создавать no-op PR; не коммитить артефакты сборки.

ПРОТОКОЛ КОНЦА: отчёт не более 30 строк: таблица шаг->статус->хвост вывода, хэши коммитов, долги. Последние две строки строго:
СОСТОЯНИЕ: ЗЕЛЕНО|КРАСНО (tests: пройдено/всего, skipped Z)
ВЕРДИКТ: ПУБЛИКОВАТЬ|НЕ ПУБЛИКОВАТЬ — причина не длиннее 10 слов.
Плюс фраза чистого финиша: "Ветка удалена, PR смержен. Любые кнопки слияния или синхронизации, которые может показывать UI GitHub, недействительны и нажимать их не нужно".
