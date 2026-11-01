# Milkromug

A local Java utility that saves complete AI chats into clean, printable PDF documents. No more cut-off messages, missing code blocks, or random UI elements ruining your exports.

## ☕ Etymology
The name **Milkromug** is a portmanteau of *Milk* + *Micro* (as in microwave) + *Mug*.

It comes from a simple daily ritual: I often drink milk. I buy it at the local grocery store *Pyaterochka* (Пятёрочка) — 3.2% fat milk in soft pillow-style packs. I heat it up in the microwave, pour it into my favorite mug, and settle in to read long AI chats.

Just like a microwave warms up your milk perfectly without spilling a drop, **Milkromug** "warms up" and packages your AI chats perfectly, without cutting a single message.

## 🛠️ Architecture
Web-based AI interfaces often truncate content when you try to use the browser's "Print to PDF" feature. To solve this safely and ethically (without violating any terms of service by scraping), **Milkromug** uses a two-part system:

1. **Browser Script (Bookmarklet):** A lightweight JS script that you run while viewing an AI chat in your browser. It isolates the chat DOM, strips away all UI clutter (buttons, sidebars, menus), and sends the clean content to a local endpoint.
2. **Local Java Engine:** A local Java application that receives the chat data, parses the roles (User / Assistant), preserves code formatting and markdown, and renders it into a beautiful, properly paginated PDF.

## 🖥️ User Interface
The application features a **minimalist UI**. The only interactive element is a **font size spinner**, allowing you to adjust the text scale before generating the final document.

## 🚀 Tech Stack
- **Language:** Java 21 (Temurin JDK)
- **Build Tool:** Maven
- **PDF Engine:** Flying Saucer / OpenPDF (HTML+CSS → PDF)
- **Parsing:** jsoup / Jackson
- **UI Framework:** Lightweight local web dashboard (Spring Boot)

## 📦 Build
```bash
mvn clean install
java -jar target/milkromug-1.0.jar
```

## 📥 Releases / Installation
The CI pipeline (GitHub Actions, `windows-latest`) automatically builds a **portable Windows package** on every push to `main` and publishes it as a **GitHub Release**. The portable zip is verified on the owner's machine (**Windows 11 x64**) and built in CI on `windows-latest`.

- Download the latest `Milkromug-portable-win64.zip` from the [Releases](https://github.com/Bizonrelax/Milkromug/releases) page (or the `latest` tag).
- Unpack it and run `Milkromug\Milkromug.exe`. The archive contains the EXE **with a bundled JRE 21** — installing Java is **not required**.
- After launch, the local UI is available at **http://localhost:8080**.
- The same zip is also available as the `Milkromug-portable-win64` build artifact in the Actions tab for any run.

## 🖱️ How to use (full user manual)

**1. Launch.** Double-click `Milkromug\Milkromug.exe`. A **console window with Spring Boot logs** opens — this is normal, keep it open while you work; the program runs inside it. **No administrator rights are required** (the portable package just needs a writable folder to unpack into).

**2. Install the bookmarklet.** Open **http://localhost:8080** and go to **http://localhost:8080/bookmarklet**, then **drag the "Milkromug Capture" button onto your browser's bookmarks bar** (or copy the minified one-liner from the textarea into a new bookmark manually). Do this once — the bookmark stays in the browser forever.

**3. Capture a chat.** Open any AI chat (**chat.qwen.ai**, **chatgpt.com**, **gemini.google.com** or most other chat UIs) and **click the Milkromug bookmark**. The script collects the messages, sends them to the local server and a **clean preview opens in a new browser tab**.

**4. Save to PDF.** In the preview tab **tune the font size** with the two spinners (chat text 8–24 pt, UI 10–22 px, the `?font=N` URL param works too) and press **"Save to PDF"** (browser print dialog → Save as PDF).

**5. Stop the program.** Simply **close the console window**, or run `taskkill /IM Milkromug.exe /F` in a terminal. Milkromug does not depend on the browser: closing tabs or the browser does **not** stop it — only closing the console window (or taskkill) does.

**6. Update.** Download the new `Milkromug-portable-win64.zip` from [Releases](https://github.com/Bizonrelax/Milkromug/releases), **first stop the old process** (close its console window or `taskkill /IM Milkromug.exe /F`), then unpack the archive over the old folder and launch `Milkromug.exe`. If you skip the stop step, the still-running old process keeps holding port 8080 and the new instance will just print "Milkromug is already running…" and exit.

**7. Java is not required.** The JRE 21 is bundled inside the package — nothing else has to be installed.

Notes:
* If the server is not running, the opened tab shows a short instruction — start `Milkromug.exe` and click the bookmark again.
* Starting a second copy of `Milkromug.exe` is safe: it detects that port 8080 is busy, prints a friendly hint (in Russian on RU systems, in English otherwise) and exits without errors.
* `POST /chat` returns JSON `{id, url}` and keeps the last 20 chats in memory; `GET /preview/{id}` renders the saved chat. Legacy HTML mode is still available via `Accept: text/html` or `?legacy=html`.

## 📖 Glossary & FAQ

Live pages: **http://localhost:8080/glossary** (RU) · http://localhost:8080/glossary?lang=en (EN). Mirror below.

### Terms (EN)
| Term | Etymology & meaning |
|---|---|
| **Bookmarklet** | *bookmark* + *applet*: a browser bookmark whose URL is a small JavaScript program; a click runs the code inside the current page. |
| **Loader** | A tiny bookmarklet that fetches fresh capture code from `http://localhost:8080/bookmarklet.js` on every click — the bookmark survives app updates without reinstallation. |
| **Sanitization** | From Latin *sanus* — cleaning captured HTML of foreign tags, styles and scripts (jsoup on the server), so previews show readable messages, not CSS junk like `.fd1gae{...}`. |
| **Repository** | From Latin *repositorium* — "storage place": the project's source-code store with history (GitHub). |
| **JSON** | JavaScript Object Notation — lightweight text data format used to transfer and export chats. |
| **curl** | Command-line HTTP client ("Client URL"): `curl http://localhost:8080/`. |
| **localhost** | "This computer" (127.0.0.1); Milkromug listens only locally — your data never leaves the machine. |
| **Headless** | Running without GUI/console (CI); Milkromug then skips the Enter-wait when the port is busy and exits with code 0. |
| **Markdown** | Lightweight text markup (`**bold**`, `# heading`); export via `/preview/{id}?format=md`. |
| **DOM** | Document Object Model — the tree of the open page that the bookmarklet reads messages from. |
| **Selector** | A short address of DOM nodes, e.g. `section.message > div.content`; each supported chat site has its own precise selectors. |
| **Shortcut** | Windows .lnk file linking to a program/page with its own hotkey ("Milkromug Capture.lnk"). |
| **Hotkey** | Keyboard combination for a quick command — capture uses **CTRL+ALT+M**. |

### FAQ (EN)
* **Why a bookmarklet?** The browser's same-origin policy forbids one page from reading a tab on another site; the only sanctioned way to run code inside the chat tab is a bookmarklet click. No extension needed.
* **Reinstall the bookmark after updating the app?** No — drag the **loader** button once; it downloads the current capture code from the local server on every click. The full one-liner remains only as an emergency fallback on /bookmarklet.
* **"Raise" vs "Configure capture"?** Per the actual current code: "Configure capture" (🔖 Настроить захват) is a plain link opening /bookmarklet in the same tab; "Raise" (🚀 Поднять) opens the same page in a NEW tab and additionally shows the floating drag-to-bookmarks-bar hint (dismissable, remembered via localStorage). Both lead to bookmarklet setup.
* **Where to paste the one-liner manually?** **Chrome**: Ctrl+Shift+B → right-click bar → "Add page…" → paste into URL → Save. **Edge**: Ctrl+Shift+B → "Add bookmark…" → paste into Address → Save. **Firefox**: Ctrl+Shift+B (toolbar) → Ctrl+D → paste into Address → Done. **Opera**: Ctrl+Shift+B → Menu → Bookmarks → "Add bookmark…". **Safari**: Settings → Advanced → Develop menu → Show Bookmarks page → "+" → paste into URL, drag to bar. **Yandex.Browser**: Ctrl+Shift+B → right-click bar → "Add bookmark…" → paste into Address.

### Глоссарий (RU)
| Термин | Этимология и значение |
|---|---|
| **Букмарклет** | *bookmark* («закладка») + *applet* («апплет»): закладка браузера, в адрес которой записана небольшая JS-программа; клик запускает код на открытой странице. |
| **Лоадер** | Букмарклет-«загрузчик»: крошечная строка скачивает свежий код захвата с `http://localhost:8080/bookmarklet.js` при каждом клике — закладка переживает обновления программы без переустановки. |
| **Санитизация** | От англ. *sanitize* — «очищать»: вырезание из HTML чужих тегов, стилей и скриптов (jsoup на сервере); в предпросмотр попадают читаемые реплики, а не CSS-мусор вида `.fd1gae{...}`. |
| **Репозиторий** | От лат. *repositorium* — «хранилище»: хранилище исходного кода проекта с историей изменений (GitHub). |
| **JSON** | JavaScript Object Notation — текстовый формат обмена данными; им букмарклет передаёт чат серверу и им отдаются экспорты. |
| **curl** | Утилита командной строки для HTTP-запросов (*Client URL*): `curl http://localhost:8080/`. |
| **localhost** | «Этот компьютер» (127.0.0.1); сервер Milkromug доступен только с вашей машины — данные никуда не уходят. |
| **Headless** | Запуск без графического интерфейса и консоли (CI); в этом режиме Milkromug не ждёт Enter при занятом порту и выходит с кодом 0. |
| **Markdown** | Лёгкий текст с разметкой (`**жирный**`, `# заголовок`); экспорт — `/preview/{id}?format=md`. |
| **DOM** | «Дерево» открытой веб-страницы, из которого букмарклет читает реплики чата. |
| **Селектор** | Короткий адрес узлов DOM, например `section.message > div.content`; для каждого сайта захвата подобраны свои точные селекторы. |
| **Ярлык** | В Windows файл-ссылка (.lnk) на программу или страницу со своей горячей клавишей («Milkromug Capture.lnk»). |
| **Горячая клавиша** | Комбинация клавиш для быстрой команды; у захвата Milkromug это **CTRL+ALT+M**. |

### FAQ (RU)
* **Зачем букмарклет и почему нельзя читать чужие вкладки без него?** Браузер запрещает одной странице читать вкладки с другим сайтом (same-origin). Единственный способ выполнить код внутри вкладки с чатом — клик по букмарклету. Расширения не нужно.
* **Надо ли переустанавливать закладку после обновления программы?** Нет. Один раз перетащите **лоадер-кнопку** со страницы /bookmarklet — она каждый раз скачивает актуальный код с локального сервера. Полный однострочник оставлен лишь как «Аварийный вариант».
* **Чем «Поднять» отличается от «Настроить захват»?** По фактическому текущему коду главной страницы: «Настроить захват» — обычная ссылка, открывающая /bookmarklet в той же вкладке; «Поднять» — кнопка-дубль: открывает тот же /bookmarklet в **новой вкладке** и показывает плавающую подсказку «перетащи кнопку на панель закладок» (закрывается крестиком, выбор запоминается чекбоксом). Обе кнопки ведут на установку букмарклета.
* **Куда вставлять однострочник вручную?** **Chrome**: Ctrl+Shift+B → правый клик по панели → «Добавить страницу…» → вставить javascript:-строку в URL → Сохранить. **Edge**: Ctrl+Shift+B → «Добавить закладку…» → вставить в поле Адрес → Сохранить. **Firefox**: Ctrl+Shift+B (панель закладок) → Ctrl+D → вставить в поле Адрес → Готово. **Opera**: Ctrl+Shift+B → Меню → Закладки → «Добавить закладку…». **Safari**: Настройки → Дополнительно → меню «Разработка» → показать страницу закладок → «+» → вставить в URL, перетащить на панель. **Яндекс.Браузер**: Ctrl+Shift+B → правый клик по панели → «Добавить закладку…» → вставить в поле Адрес.



## 🙏 Credits
The credits table mirrors the `/about` page of the application:

| Project | Version | License |
|---|---|---|
| [Spring Boot](https://spring.io/projects/spring-boot) | 3.3.5 | Apache License 2.0 |
| [Thymeleaf](https://www.thymeleaf.org) | 3.1.2.RELEASE | Apache License 2.0 |
| [jsoup](https://jsoup.org) | 1.18.1 | MIT License |
| [Jackson](https://github.com/FasterXML/jackson) | 2.17.2 | Apache License 2.0 |
| [openhtmltopdf](https://openhtmltopdf.com) | 1.0.10 | LGPL v2.1 / Apache License 2.0 |
| [Eclipse Temurin JDK](https://adoptium.net) | 21 | Eclipse Public License 2.0 |
| [Apache Maven](https://maven.apache.org) | 3.9+ | Apache License 2.0 |
| [jpackage (OpenJDK)](https://openjdk.org) | 21 | GPLv2 + Classpath Exception |

Developed with the help of AI assistants **Qwen** and **Qwen Coder**.

📜 License
This project is released under The Unlicense. Do whatever you want with it. No copyright, no restrictions.




# Milkromug

Локальная Java-утилита для сохранения полных чатов с ИИ в чистые PDF-документы, готовые к печати. Больше никаких обрезанных сообщений, отсутствующих блоков кода или случайных элементов интерфейса, портящих экспорт.

## ☕ Этимология
Название **Milkromug** — это склейка слов *Milk* (молоко) + *Micro* (от микроволновки) + *Mug* (кружка).

Оно родилось из простой ежедневной привычки: я часто пью молоко. Покупаю его в магазине «Пятёрочка» — молоко 3,2% жирности в мягких пакетах. Я подогреваю его в микроволновке, наливаю в любимую кружку и усаживаюсь читать длинные чаты с нейросетями.

Точно так же, как микроволновка идеально греет молоко, не пролив ни капли, **Milkromug** идеально «подогревает» и упаковывает ваши чаты с ИИ, не срезая ни единого сообщения.

## 🛠️ Архитектура
Веб-интерфейсы нейросетей часто обрезают контент, если пытаться использовать функцию «Печать в PDF» через браузер. Чтобы решить эту проблему безопасно и этично (не нарушая пользовательские соглашения скрейпингом), **Milkromug** использует двухкомпонентную систему:

1. **Браузерный скрипт (Букмарклет):** Легкий JS-скрипт, который вы запускаете, находясь в чате. Он изолирует DOM чата, убирает весь мусор (кнопки, меню, сайдбары) и отправляет чистые данные на локальный сервер.
2. **Локальный Java-движок:** Локальное Java-приложение принимает данные чата, парсит роли (Пользователь / Ассистент), сохраняет форматирование кода и markdown, а затем рендерит красивый PDF с правильной пагинацией.

## 🖥️ Интерфейс
Приложение имеет **минималистичный UI**. Единственный интерактивный элемент — **спиннер размера шрифта**, позволяющий настроить масштаб текста перед генерацией итогового документа.

## 🚀 Технологический стек
- **Язык:** Java 21 (Temurin JDK)
- **Сборщик:** Maven
- **Движок PDF:** Flying Saucer / OpenPDF (HTML+CSS → PDF)
- **Парсинг:** jsoup / Jackson
- **Интерфейс:** Легковесная локальная веб-панель (Spring Boot)

## 📦 Сборка
```
mvn clean install
java -jar target/milkromug-1.0.jar
```

## 📥 Релизы / Установка
CI-конвейер (GitHub Actions, `windows-latest`) автоматически собирает **портативный пакет для Windows** при каждом пуше в `main` и публикует его как **GitHub Release**. Портативный zip проверен на машине владельца (**Windows 11 x64**) и собирается в CI на `windows-latest`.

- Скачайте свежий `Milkromug-portable-win64.zip` из раздела [Releases](https://github.com/Bizonrelax/Milkromug/releases) (или тега `latest`).
- Распакуйте архив и запустите `Milkromug\Milkromug.exe`. Внутри — EXE **со встроенной JRE 21**: устанавливать Java **не требуется**.
- После запуска интерфейс доступен на **http://localhost:8080**.
- Тот же zip доступен как артефакт сборки `Milkromug-portable-win64` на вкладке Actions для любого прогона.

## 🖱️ Как пользоваться (полное руководство пользователя)

**1. Запуск.** Дважды кликните `Milkromug\Milkromug.exe`. Откроется **консольное окно с логами Spring Boot** — это нормально, держите его открытым во время работы: программа живёт именно в этом окне. **Права администратора НЕ нужны** (портативному пакету нужна только доступная для записи папка, куда вы его распаковали).

**2. Установите букмарклет.** Откройте **http://localhost:8080**, затем страницу **http://localhost:8080/bookmarklet** и **перетащите кнопку «Milkromug Capture» на панель закладок браузера** (или скопируйте минифицированный однострочник из textarea в новую закладку вручную). Делается один раз — закладка остаётся в браузере навсегда.

**3. Захват чата.** Откройте чат с ИИ (**chat.qwen.ai**, **chatgpt.com**, **gemini.google.com** или большинство других интерфейсов) и **кликните по закладке Milkromug**. Скрипт соберёт сообщения, отправит их на локальный сервер, и в **новой вкладке откроется чистый предпросмотр**.

**4. Сохранение в PDF.** В новой вкладке **настройте шрифт** двумя спиннерами (текст чата 8–24 pt, интерфейс 10–22 px, параметр URL `?font=N` тоже работает) и нажмите **«Сохранить в PDF»** (диалог печати браузера → Сохранить как PDF).

**5. Как остановить программу.** Просто **закройте консольное окно** или выполните в терминале `taskkill /IM Milkromug.exe /F`. Milkromug не зависит от браузера: закрытие вкладок или браузера его **не останавливает** — только закрытие консольного окна (или taskkill).

**6. Как обновить.** Скачайте новый `Milkromug-portable-win64.zip` из [Releases](https://github.com/Bizonrelax/Milkromug/releases), **СНАЧАЛА остановите старый процесс** (закройте его консольное окно или `taskkill /IM Milkromug.exe /F`), затем распакуйте архив поверх старой папки и запустите `Milkromug.exe`. Если пропустить остановку, старый процесс продолжит держать порт 8080, и новый экземпляр просто напишет «Milkromug уже запущен…» и завершится.

**7. Java не требуется.** JRE 21 встроена в пакет — ничего дополнительно устанавливать не нужно.

Примечания:
* Если сервер не запущен, в открытой вкладке появится короткая инструкция — запустите `Milkromug.exe` и кликните закладку снова.
* Повторный запуск `Milkromug.exe` безопасен: программа определит, что порт 8080 занят, напечатает понятную подсказку (на русском для русской локали, на английском для остальных) и выйдет без ошибок.
* `POST /chat` возвращает JSON `{id, url}` и хранит в памяти последние 20 чатов; `GET /preview/{id}` рендерит сохранённый чат. Прежний HTML-режим доступен через заголовок `Accept: text/html` или параметр `?legacy=html`.

## 📖 Глоссарий и FAQ

Живые страницы: **http://localhost:8080/glossary** (RU) · http://localhost:8080/glossary?lang=en (EN). Зеркало ниже.

### Термины (RU)
| Термин | Этимология и значение |
|---|---|
| **Букмарклет** | *bookmark* («закладка») + *applet* («апплет»): закладка браузера, в адрес которой записана небольшая JS-программа; клик запускает код на открытой странице. |
| **Лоадер** | Букмарклет-«загрузчик»: крошечная строка скачивает свежий код захвата с `http://localhost:8080/bookmarklet.js` при каждом клике — закладка переживает обновления программы без переустановки. |
| **Санитизация** | От англ. *sanitize* — «очищать»: вырезание из HTML чужих тегов, стилей и скриптов (jsoup на сервере); в предпросмотр попадают читаемые реплики, а не CSS-мусор вида `.fd1gae{...}`. |
| **Репозиторий** | От лат. *repositorium* — «хранилище»: хранилище исходного кода проекта с историей изменений (GitHub). |
| **JSON** | JavaScript Object Notation — текстовый формат обмена данными; им букмарклет передаёт чат серверу и им отдаются экспорты. |
| **curl** | Утилита командной строки для HTTP-запросов (*Client URL*): `curl http://localhost:8080/`. |
| **localhost** | «Этот компьютер» (127.0.0.1); сервер Milkromug доступен только с вашей машины — данные никуда не уходят. |
| **Headless** | Запуск без графического интерфейса и консоли (CI); в этом режиме Milkromug не ждёт Enter при занятом порту и выходит с кодом 0. |
| **Markdown** | Лёгкий текст с разметкой (`**жирный**`, `# заголовок`); экспорт — `/preview/{id}?format=md`. |
| **DOM** | «Дерево» открытой веб-страницы, из которого букмарклет читает реплики чата. |
| **Селектор** | Короткий адрес узлов DOM, например `section.message > div.content`; для каждого сайта захвата подобраны свои точные селекторы. |
| **Ярлык** | В Windows файл-ссылка (.lnk) на программу или страницу со своей горячей клавишей («Milkromug Capture.lnk»). |
| **Горячая клавиша** | Комбинация клавиш для быстрой команды; у захвата Milkromug это **CTRL+ALT+M**. |

### FAQ (RU)
* **Зачем букмарклет и почему нельзя читать чужие вкладки без него?** Браузер запрещает одной странице читать вкладки с другим сайтом (same-origin). Единственный способ выполнить код внутри вкладки с чатом — клик по букмарклету. Расширения не нужно.
* **Надо ли переустанавливать закладку после обновления программы?** Нет. Один раз перетащите **лоадер-кнопку** со страницы /bookmarklet — она каждый раз скачивает актуальный код с локального сервера. Полный однострочник оставлен лишь как «Аварийный вариант».
* **Чем «Поднять» отличается от «Настроить захват»?** По фактическому текущему коду главной страницы: «Настроить захват» — обычная ссылка, открывающая /bookmarklet в той же вкладке; «Поднять» — кнопка-дубль: открывает тот же /bookmarklet в **новой вкладке** и показывает плавающую подсказку «перетащи кнопку на панель закладок» (закрывается крестиком, выбор запоминается чекбоксом). Обе кнопки ведут на установку букмарклета.
* **Куда вставлять однострочник вручную?** **Chrome**: Ctrl+Shift+B → правый клик по панели → «Добавить страницу…» → вставить javascript:-строку в URL → Сохранить. **Edge**: Ctrl+Shift+B → «Добавить закладку…» → вставить в поле Адрес → Сохранить. **Firefox**: Ctrl+Shift+B (панель закладок) → Ctrl+D → вставить в поле Адрес → Готово. **Opera**: Ctrl+Shift+B → Меню → Закладки → «Добавить закладку…». **Safari**: Настройки → Дополнительно → меню «Разработка» → показать страницу закладок → «+» → вставить в URL, перетащить на панель. **Яндекс.Браузер**: Ctrl+Shift+B → правый клик по панели → «Добавить закладку…» → вставить в поле Адрес.

### Terms (EN)
| Term | Etymology & meaning |
|---|---|
| **Bookmarklet** | *bookmark* + *applet*: a browser bookmark whose URL is a small JavaScript program; a click runs the code inside the current page. |
| **Loader** | A tiny bookmarklet that fetches fresh capture code from `http://localhost:8080/bookmarklet.js` on every click — the bookmark survives app updates without reinstallation. |
| **Sanitization** | From Latin *sanus* — cleaning captured HTML of foreign tags, styles and scripts (jsoup on the server), so previews show readable messages, not CSS junk like `.fd1gae{...}`. |
| **Repository** | From Latin *repositorium* — "storage place": the project's source-code store with history (GitHub). |
| **JSON** | JavaScript Object Notation — lightweight text data format used to transfer and export chats. |
| **curl** | Command-line HTTP client ("Client URL"): `curl http://localhost:8080/`. |
| **localhost** | "This computer" (127.0.0.1); Milkromug listens only locally — your data never leaves the machine. |
| **Headless** | Running without GUI/console (CI); Milkromug then skips the Enter-wait when the port is busy and exits with code 0. |
| **Markdown** | Lightweight text markup (`**bold**`, `# heading`); export via `/preview/{id}?format=md`. |
| **DOM** | Document Object Model — the tree of the open page that the bookmarklet reads messages from. |
| **Selector** | A short address of DOM nodes, e.g. `section.message > div.content`; each supported chat site has its own precise selectors. |
| **Shortcut** | Windows .lnk file linking to a program/page with its own hotkey ("Milkromug Capture.lnk"). |
| **Hotkey** | Keyboard combination for a quick command — capture uses **CTRL+ALT+M**. |

### FAQ (EN)
* **Why a bookmarklet?** The browser's same-origin policy forbids one page from reading a tab on another site; the only sanctioned way to run code inside the chat tab is a bookmarklet click. No extension needed.
* **Reinstall the bookmark after updating the app?** No — drag the **loader** button once; it downloads the current capture code from the local server on every click. The full one-liner remains only as an emergency fallback on /bookmarklet.
* **"Raise" vs "Configure capture"?** Per the actual current code: "Configure capture" (🔖 Настроить захват) is a plain link opening /bookmarklet in the same tab; "Raise" (🚀 Поднять) opens the same page in a NEW tab and additionally shows the floating drag-to-bookmarks-bar hint (dismissable, remembered via localStorage). Both lead to bookmarklet setup.
* **Where to paste the one-liner manually?** **Chrome**: Ctrl+Shift+B → right-click bar → "Add page…" → paste into URL → Save. **Edge**: Ctrl+Shift+B → "Add bookmark…" → paste into Address → Save. **Firefox**: Ctrl+Shift+B (toolbar) → Ctrl+D → paste into Address → Done. **Opera**: Ctrl+Shift+B → Menu → Bookmarks → "Add bookmark…". **Safari**: Settings → Advanced → Develop menu → Show Bookmarks page → "+" → paste into URL, drag to bar. **Yandex.Browser**: Ctrl+Shift+B → right-click bar → "Add bookmark…" → paste into Address.

## 🙏 Кредиты
Таблица кредитов зеркальна таблице со страницы `/about` приложения:

| Проект | Версия | Лицензия |
|---|---|---|
| [Spring Boot](https://spring.io/projects/spring-boot) | 3.3.5 | Apache License 2.0 |
| [Thymeleaf](https://www.thymeleaf.org) | 3.1.2.RELEASE | Apache License 2.0 |
| [jsoup](https://jsoup.org) | 1.18.1 | MIT License |
| [Jackson](https://github.com/FasterXML/jackson) | 2.17.2 | Apache License 2.0 |
| [openhtmltopdf](https://openhtmltopdf.com) | 1.0.10 | LGPL v2.1 / Apache License 2.0 |
| [Eclipse Temurin JDK](https://adoptium.net) | 21 | Eclipse Public License 2.0 |
| [Apache Maven](https://maven.apache.org) | 3.9+ | Apache License 2.0 |
| [jpackage (OpenJDK)](https://openjdk.org) | 21 | GPLv2 + Classpath Exception |

Разработано с помощью ИИ-ассистентов **Qwen** и **Qwen Coder**.

📜 Лицензия
Проект выпущен под The Unlicense. Делайте с кодом всё, что захотите. Никаких копирайтов и ограничений.



