ЗАЧЕМ: владелец поставил требование: экспорт не только в PDF через печать браузера. Предпросмотр уже хранит чат в памяти сервера, значит сервер может сам отдать тот же чат в четырёх текстовых форматах, а страница предпросмотра должна предложить их кнопками.

ЦЕЛЬ: на странице GET /preview/{id} есть четыре кнопки Скачать .md, Скачать .html, Скачать .txt, Скачать .json; каждая отдаёт файл с верным Content-Disposition и содержимым, соответствующим чату.

ГОТОВНОСТЬ:
- для каждого fmt из md, html, txt, json: curl -s -D - -o /tmp/body "http://localhost:8080/preview/<id>?format=fmt" -> статус 200, заголовок Content-Disposition: attachment, тело непустое
- тело md содержит заголовки с решеткой и не содержит тегов html; тело html начинается с <!DOCTYPE html> и не ссылается на внешние сети (весь css внутри); тело txt не содержит символов решетки и звездочек разметки; тело json содержит строку "messages"
- curl -s http://localhost:8080/preview/<id> | grep -c "format=" -> 4 или больше
- mvn -q test -> Failures: 0, Errors: 0

КОНТЕКСТ: переиспользовать ChatStore и существующий шаблон chat-print.html. txt = текст сообщений из jsoup-текста сохранённого html с переносами строк. md = поле text сообщения, если оно непустое и похоже на markdown, иначе обратная конвертация html в md только для базовых узлов (заголовки, списки, жирный, код-блоки с языком). html = тот же предпросмотр без панели инструментов и кнопок, со встроенным css, офлайн. json = исходный принятый payload. Кнопки разместить рядом с Сохранить в PDF, с тултипами.

ОКРУЖЕНИЕ: [ -f /workspace/env.sh ] || cat > /workspace/env.sh <<'EOF'
export JAVA_HOME=<фактический путь к Temurin 21 из прошлых сессий>
export PATH=$JAVA_HOME/bin:<путь к maven>/bin:$PATH
EOF
source /workspace/env.sh; java -version -> 21

ШАГИ:
0. Канарейка: grep -q "spring.main.headless" src/main/resources/application.properties && grep -q "hint-close" src/main/resources/templates/bookmarklet.html -> оба найдены (маркеры ptabc-11). Иначе STOP и доклад.
0.5. git fetch origin; git checkout -b ptabc-12 origin/main.
1. Тест раньше кода: MockMvc-кейсы на четыре формата с фикстурой демо-чата: статус 200, Content-Disposition attachment, признаки содержимого из ГОТОВНОСТИ; mvn -q test -Dtest=ExportFormatsTest -> КРАСНЫЕ. Чек-поинт: git commit -m "test: export formats md html txt json" и пуш.
2. Микро-шаг: сервис ExportService с четырьмя методами генерации из сохранённого чата; контроллер: параметр format у GET /preview/{id} отдаёт файл, без параметра поведение прежнее. Повтор тестов -> зелёные по md и txt. Чек-поинт: git commit -m "feat: export service md and txt" и пуш.
3. Микро-шаг: добавить html (шаблон без панели, css внутри) и json (исходный payload). Повтор тестов -> все зелёные. Чек-поинт: git commit -m "feat: export standalone html and raw json" и пуш.
4. Микро-шаг: четыре кнопки с тултипами на chat-print.html рядом с Сохранить в PDF; в media print кнопки скрыты. Проверка: grep format= >=4. Чек-поинт: git commit -m "feat(ui): download buttons on preview" и пуш.
5. mvn clean install -> BUILD SUCCESS; все команды ГОТОВНОСТИ зелёные.
6. Публикация: push ptabc-12, PR в main через API, мерж (владелец разрешил), git push origin --delete ptabc-12. Дождаться: CI на main green, ассет Release перезаписан.

СТОП-ПРАВИЛА: красный после микро-фикса -> трассировка с печатью стадии в отчёт, архитектуру не менять; девиация от схемы -> стоп и доклад; около 3 вызовов до лимита -> WIP-коммит + REMAINING.md в коммите.

ЗАПРЕТЫ: не трогать дедупликацию и print.css из ptabc-11, PortGuard, banner.txt, workflow maven.yml, LICENSE; не добавлять внешние CDN в standalone html; не создавать no-op PR.

ПРОТОКОЛ КОНЦА: отчёт не более 30 строк: таблица шаг->статус->хвост вывода, хэши коммитов, долги. Последние две строки строго:
СОСТОЯНИЕ: ЗЕЛЕНО|КРАСНО (tests: пройдено/всего, skipped Z)
ВЕРДИКТ: ПУБЛИКОВАТЬ|НЕ ПУБЛИКОВАТЬ — причина не длиннее 10 слов.
Плюс фраза чистого финиша: "Ветка удалена, PR смержен. Любые кнопки слияния или синхронизации, которые может показывать UI GitHub, недействительны и нажимать их не нужно".
