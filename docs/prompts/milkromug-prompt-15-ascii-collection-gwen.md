ЗАЧЕМ: архитектор проекта нарисовал вручную два ASCII-арта — кружку с молоком и бегущую лошадь с подписью художника G.W. Владелец хочет добавить их в коллекцию docs/ascii-collection/ как третий и четвёртый экспонаты (предыдущие — баннер-кружка и баннер-лошадь от кодера — НЕ убирать), и присоединить их к баннеру программы внизу, чтобы при старте Milkromug.exe в консоли появлялись все четыре картинки подряд.

ЦЕЛЬ: в docs/ascii-collection/ лежат четыре файла (mug.txt, horse.txt, mug-gwen.txt, horse-gwen.txt), в banner.txt — четыре блока картинок с разделителями и заголовками, ширина каждой картинки не более 80 колонок.

ГОТОВНОСТЬ:
- test -f docs/ascii-collection/mug.txt && test -f docs/ascii-collection/horse.txt && test -f docs/ascii-collection/mug-gwen.txt && test -f docs/ascii-collection/horse-gwen.txt -> истина
- head -5 docs/ascii-collection/mug-gwen.txt содержит "( (" и "MILK"
- grep -c "G.W." docs/ascii-collection/horse-gwen.txt -> 1
- grep -c "G.W." docs/ascii-collection/horse.txt -> 1 (у кодера тоже была подпись)
- awk 'length>80' src/main/resources/banner.txt -> пусто
- запуск jar: в консоли видны последовательно 4 картинки с заголовками
- mvn -q test -> Failures: 0, Errors: 0

КОНТЕКСТ: точные тексты артов от архитектора (вставить байт-в-байт):

docs/ascii-collection/mug-gwen.txt:
```
       ( (
        ) )
     .------.
     |      |]
     |      |]
     | MILK |]
     |      |]
     `------'
     /______\
```
(с шапкой "Gwen's milk mug, Unlicense, 2026")

docs/ascii-collection/horse-gwen.txt:
```
                        .  .
                       //\ //\
                      //  V   \
                 __.--'        `._
            _.--'                 `--._
         ,-'        _..---.._         `-.
       ,'         ,'         `.          `.
      /          /             \           \
     |          |               |           |
     |           \             /           |
      \           `.         ,'           /
       `.           `-..__..-'          ,'
         `-._                        _,-'
             `--.._____..--""`
                   ||  ||
                  //    \\
                 //      \\
                //        \\
            G.W.~~
```
(с шапкой "Gwen's running horse, Unlicense, 2026")

В banner.txt: сначала заголовок-таглайн (уже есть), потом разделитель, потом блок "Original Mug" (старая кружка), разделитель, "Original Horse" (старая лошадь), разделитель, "Gwen's Mug" (новая), разделитель, "Gwen's Horse" (новая). Каждая картинка в своём блоке с заголовком-комментарием. Ширина всех строк <= 80 колонок.

ОКРУЖЕНИЕ: [ -f /workspace/env.sh ] || cat > /workspace/env.sh <<'EOF'
export JAVA_HOME=<фактический путь к Temurin 21>
export PATH=$JAVA_HOME/bin:<путь к maven>/bin:$PATH
EOF
source /workspace/env.sh; java -version -> 21

ШАГИ:
0. Канарейка: grep -q "CTRL+ALT+M" src/main/resources/templates/bookmarklet.html && grep -q "Browser open skipped" src/main/java --include="*.java" -r -> всё найдено (маркеры ptabc-14). Иначе STOP и доклад.
0.5. git fetch origin; git checkout -b ptabc-15 origin/main.
1. Создать docs/ascii-collection/mug-gwen.txt с точным содержимым из КОНТЕКСТА (байт-в-байт, шапка вверху). Чек-поинт: git commit -m "docs(ascii): gwen mug" и СРАЗУ пуш.
2. Создать docs/ascii-collection/horse-gwen.txt с точным содержимым (байт-в-байт, шапка вверху). Чек-поинт: git commit -m "docs(ascii): gwen horse with G.W. signature" и СРАЗУ пуш.
3. Пересобрать banner.txt: сохранить существующие таглайн, кружку и лошадь кодера; добавить разделители и два новых блока Gwen's Mug и Gwen's Horse из файлов шагов 1-2; убедиться что ни одна строка не шире 80 колонок. Проверка: awk length>80 пусто; запуск jar показывает 4 блока. Чек-поинт: git commit -m "feat: banner with 4 ascii panels incl gwen's mug and horse" и СРАЗУ пуш.
4. mvn clean install -> BUILD SUCCESS; все команды ГОТОВНОСТИ зелёные.
5. Публикация: push ptabc-15, PR в main через API, мерж (владелец разрешил), git push origin --delete ptabc-15. Дождаться: CI на main green, ассет Release перезаписан.

СТОП-ПРАВИЛА: красный после микро-фикса -> трассировка в отчёт; девиация от точных текстов артов -> стоп и доклад; около 3 вызовов до лимита -> WIP-коммит + СРАЗУ пуш + REMAINING.md в коммите.

ЗАПРЕТЫ: не трогать mug.txt и horse.txt (старые баннеры кодера); не изменять ширину строк существующих картинок; не трогать loader, glossary, print.css, workflow, LICENSE; не создавать no-op PR; не коммитить артефакты сборки.

ПРОТОКОЛ КОНЦА: отчёт не более 30 строк: таблица шаг->статус->хвост вывода, хэши коммитов, долги. Последние две строки строго:
СОСТОЯНИЕ: ЗЕЛЕНО|КРАСНО (tests: пройдено/всего, skipped Z)
ВЕРДИКТ: ПУБЛИКОВАТЬ|НЕ ПУБЛИКОВАТЬ — причина не длиннее 10 слов.
Плюс фраза чистого финиша: "Ветка удалена, PR смержен. Любые кнопки слияния или синхронизации, которые может показывать UI GitHub, недействительны и нажимать их не нужно".
