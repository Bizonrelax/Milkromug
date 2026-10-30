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
The CI pipeline (GitHub Actions, `windows-latest`) automatically builds a **portable Windows package** on every push to `main` and publishes it as a **GitHub Release**.

- Download the latest `Milkromug-portable-win64.zip` from the [Releases](https://github.com/Bizonrelax/Milkromug/releases) page (or the `latest` tag).
- Unpack it and run `Milkromug\Milkromug.exe`. The archive contains the EXE **with a bundled JRE 21** — installing Java is **not required**.
- After launch, the local UI is available at **http://localhost:8080**.
- The same zip is also available as the `Milkromug-portable-win64` build artifact in the Actions tab for any run.


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
CI-конвейер (GitHub Actions, `windows-latest`) автоматически собирает **портативный пакет для Windows** при каждом пуше в `main` и публикует его как **GitHub Release**.

- Скачайте свежий `Milkromug-portable-win64.zip` из раздела [Releases](https://github.com/Bizonrelax/Milkromug/releases) (или тега `latest`).
- Распакуйте архив и запустите `Milkromug\Milkromug.exe`. Внутри — EXE **со встроенной JRE 21**: устанавливать Java **не требуется**.
- После запуска интерфейс доступен на **http://localhost:8080**.
- Тот же zip доступен как артефакт сборки `Milkromug-portable-win64` на вкладке Actions для любого прогона.

📜 Лицензия
Проект выпущен под The Unlicense. Делайте с кодом всё, что захотите. Никаких копирайтов и ограничений.



