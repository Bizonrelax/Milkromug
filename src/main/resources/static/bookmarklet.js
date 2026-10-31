/* ==========================================================================
 * Milkromug Capture — букмарклет захвата чатов из браузера одним кликом.
 *
 * ЧИТАЕМЫЙ ИСХОДНИК. Сервер (GET /bookmarklet) читает этот файл из classpath,
 * подставляет вместо %SERVER_URL% адрес локального сервера и генерирует
 * javascript:-ссылку для панели закладок.
 *
 * ВНИМАНИЕ при правке: сервер «минифицирует» файл построчно (убирает пустые
 * строки и строки-комментарии, склеивая остальное через пробел). Поэтому:
 *   - не используйте многострочные шаблонные литералы (`...`);
 *   - не оставляйте хвостовых // комментариев после кода на той же строке;
 *   - не переносите незавершённые выражения через строку так, чтобы они
 *     требовали точной расстановки переводов строк (у всего есть ; или {}).
 * Синтаксис проверяется автоматически тестом (node --check).
 * ========================================================================== */
(function () {
  var SERVER = '%SERVER_URL%';
  /* НОВОЕ ОКНО ОТКРЫВАЕМСЯ СИНХРОННО В МОМЕНТ КЛИКА — иначе блокировщик
   * всплывающих окон зарежет window.open из асинхронного колбэка fetch. */
  var win = window.open('about:blank', '_blank');
  function show(html) {
    try {
      if (win && win.document) {
        win.document.open();
        win.document.write('<!DOCTYPE html><html lang="ru"><head><meta charset="utf-8"><title>Milkromug</title></head><body style="font-family:system-ui,sans-serif;padding:24px">' + html + '</body></html>');
        win.document.close();
      } else {
        alert('Milkromug: ' + html.replace(/<[^>]*>/g, ' '));
      }
    } catch (e) {
      alert('Milkromug: ' + html.replace(/<[^>]*>/g, ' '));
    }
  }
  function fail(err, extra) {
    console.warn('[Milkromug] ошибка захвата:', err);
    show('<h2>&#9749; Milkromug</h2><p><b>Не удалось отправить чат в Milkromug.</b></p><ol><li>Запусти <b>Milkromug.exe</b> (двойной клик по иконке).</li><li>Дождись, пока сервер поднимется на <code>http:\u002F\u002Flocalhost:8080</code>.</li><li>Вернись на страницу чата и <b>повтори клик по закладке</b>.</li></ol><p style="color:#888">Детали ошибки: ' + String(err && err.message ? err.message : err) + (extra ? '<br/>' + extra : '') + '</p>');
  }
  /* ---- временный индикатор статуса прямо на странице чата ---- */
  var toast = document.createElement('div');
  toast.textContent = '\u2615 Milkromug: сохраняю чат\u2026';
  toast.setAttribute('style', 'position:fixed;top:12px;right:12px;z-index:2147483647;background:#6b4f3a;color:#fff;padding:10px 16px;border-radius:10px;font:14px system-ui,sans-serif;box-shadow:0 4px 14px rgba(0,0,0,.3)');
  document.body.appendChild(toast);
  function done(msg) { toast.textContent = msg; setTimeout(function () { toast.remove(); }, 3000); }
  function failHere(msg) { toast.textContent = msg; toast.style.background = '#a33'; }
  /* ---- подбор селекторов сообщений по hostname ---- */
  var host = location.hostname;
  var SELECTORS = [];
  if (/(\.|^)qwen\.ai$/.test(host)) {
    SELECTORS.push('.markdown');
    SELECTORS.push('[class*="message"]');
  } else if (/(\.|^)openai\.com$/.test(host) || /(\.|^)chatgpt\.com$/.test(host)) {
    SELECTORS.push('article[data-message-author-role]');
    SELECTORS.push('div[data-message-id]');
  } else if (/(\.|^)gemini\.google\.com$/.test(host) || /(^|\.)gemini\.google$/.test(host)) {
    SELECTORS.push('message-content');
    SELECTORS.push('.model-response-text');
  }
  var nodes = [];
  for (var s = 0; s < SELECTORS.length && nodes.length === 0; s++) {
    try {
      nodes = Array.prototype.slice.call(document.querySelectorAll(SELECTORS[s]));
    } catch (e) { nodes = []; }
  }
  /* отфильтровать мусор и вложенные дубликаты markdown-блоков */
  nodes = nodes.filter(function (n) {
    var t = n.innerText || '';
    return t.trim().length > 0;
  });
  nodes = nodes.filter(function (n) {
    var hasChildNode = false;
    for (var a = 0; a < nodes.length; a++) {
      if (nodes[a] !== n && n.contains(nodes[a])) hasChildNode = true;
    }
    if (hasChildNode) return true;
    for (var b = 0; b < nodes.length; b++) {
      if (nodes[b] !== n && nodes[b].contains(n)) return false;
    }
    return true;
  });
  /* ---- фолбэк-эвристика: контейнер с двумя и более одинаковыми детьми ---- */
  if (nodes.length === 0) {
    var best = null;
    var all = document.querySelectorAll('*');
    for (var k = 0; k < all.length; k++) {
      var c = all[k];
      var ch = c.children;
      if (!ch || ch.length < 2) continue;
      var sigs = {};
      var maxRep = 0;
      var totalChars = 0;
      for (var i = 0; i < ch.length; i++) {
        var el = ch[i];
        var t = el.innerText || '';
        totalChars += t.trim().length;
        var tag = el.tagName + '|' + (el.getAttribute('class') || '');
        sigs[tag] = (sigs[tag] || 0) + 1;
        if (sigs[tag] > maxRep) maxRep = sigs[tag];
      }
      if (maxRep < 2) continue;
      if (totalChars < 40) continue;
      if (!best || totalChars > best.totalChars) best = { el: c, totalChars: totalChars };
    }
    if (best) {
      nodes = Array.prototype.filter.call(best.el.children, function (x) {
        return (x.innerText || '').trim().length > 0;
      });
    }
  }
  if (nodes.length === 0) { failHere('Сообщения не найдены'); return; }
  /* ---- роль автора: data-атрибуты, иначе чередование от первого сообщения ---- */
  function roleOf(node, idx) {
    var el = node;
    for (var d = 0; d < 2 && el; d++) {
      var r = el.getAttribute && (el.getAttribute('data-message-author-role') || el.getAttribute('data-role'));
      if (r) return r.toLowerCase() === 'user' ? 'user' : 'assistant';
      el = el.parentElement;
    }
    return idx % 2 === 0 ? 'user' : 'assistant';
  }
  /* ---- сбор сообщений ---- */
  // Микро-фикс C: перед снятием innerHTML/innerText вырезаем служебные узлы
  // style, script из клона узла сообщения — иначе их содержимое попадает
  // в экспорт как видимый текст.
  function stripServiceNodes(node) {
    var clone = node.cloneNode(true);
    var junk = clone.querySelectorAll('style, script');
    for (var j = junk.length - 1; j >= 0; j--) {
      junk[j].parentNode.removeChild(junk[j]);
    }
    return clone;
  }
  var messages = [];
  for (var m = 0; m < nodes.length; m++) {
    var node = stripServiceNodes(nodes[m]);
    var text = node.innerText || '';
    if (!text.trim()) continue;
    messages.push({ author: roleOf(node, messages.length), text: text, html: false });
    var md = node.matches && node.matches('.markdown') ? node : node.querySelector('.markdown');
    if (md) {
      messages[messages.length - 1].text = md.innerHTML;
      messages[messages.length - 1].html = true;
    }
  }
  if (messages.length === 0) { failHere('Сообщения пустые'); return; }
  /* ---- title: document.title без хвостовых суффиксов сайта ---- */
  var rawTitle = document.title || 'Milkromug Chat Print';
  var parts = rawTitle.split(/\s+[\u2013\u2014|-]\s+/);
  var title = parts[0].trim();
  for (var p = 1; p < parts.length; p++) {
    var seg = parts[p].trim().toLowerCase();
    if (seg === 'qwen' || seg === 'chatgpt' || seg === 'gemini' || seg === 'google' || seg === 'claude' || seg === 'deepseek' || seg === 'mistral' || seg === 'ai' || seg === 'chat') continue;
    if (seg.indexOf('free online ai chat') >= 0) continue;
    if (seg.indexOf('artificial intelligence') >= 0) continue;
    if (seg) { title = parts[0].trim(); break; }
  }
  if (!title) title = rawTitle;
  /* ---- POST на локальный сервер (mixed content для loopback разрешён) ---- */
  fetch(SERVER + '/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ title: title, messages: messages })
  }).then(function (resp) {
    var ctype = (resp.headers.get('content-type') || '').toLowerCase();
    return resp.text().then(function (text) {
      if (!resp.ok) { throw new Error('HTTP ' + resp.status + ' от сервера'); }
      if (ctype.indexOf('json') < 0) { var e = new Error('Сервер ответил не JSON (' + (ctype || 'без content-type') + ')'); e.raw = text; throw e; }
      try { return JSON.parse(text); } catch (e2) { var e3 = new Error('Не удалось разборать ответ сервера как JSON'); e3.raw = text; throw e3; }
    });
  }).then(function (data) {
    done('\u2713 Готово, открываю предпросмотр');
    if (win) {
      win.location = data.url;
    } else {
      location.href = data.url;
    }
  }).catch(function (err) {
    failHere('\u2715 Ошибка отправки');
    var extra = err && err.raw ? ('Статус ответа: ' + String(err.message) + '; начало тела: ' + err.raw.slice(0, 200)) : null;
    fail(err, extra);
  });
})();
