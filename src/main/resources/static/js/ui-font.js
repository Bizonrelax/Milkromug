/* ============================================================
   Milkromug — спиннер размера шрифта ИНТЕРФЕЙСА (Task A)
   Независим от печатного спиннера (?font=N, 8–24 pt).

   - Диапазон: 10–22 px, шаг 1, по умолчанию 16.
   - Значение пишется в CSS-переменную --ui-font-size на :root;
     все UI-элементы (панель, заголовки, подписи, кнопки) используют её.
   - Сохраняется в localStorage ("milkromug-ui-font") и
     восстанавливается при загрузке страницы.
   ============================================================ */
(function () {
    'use strict';

    var UI_FONT_KEY = 'milkromug-ui-font';
    var UI_FONT_MIN = 10;
    var UI_FONT_MAX = 22;
    var UI_FONT_DEFAULT = 16;

    function clampUiFont(v) {
        if (isNaN(v)) v = UI_FONT_DEFAULT;
        return Math.max(UI_FONT_MIN, Math.min(UI_FONT_MAX, v));
    }

    // Применяем размер шрифта интерфейса до DOMContentLoaded,
    // чтобы избежать «прыжка» текста при загрузке.
    function applyUiFont(px) {
        var v = clampUiFont(px);
        document.documentElement.style.setProperty('--ui-font-size', v + 'px');
        return v;
    }

    function readStoredUiFont() {
        try {
            var raw = localStorage.getItem(UI_FONT_KEY);
            if (raw === null || raw === '') return UI_FONT_DEFAULT;
            return clampUiFont(parseInt(raw, 10));
        } catch (e) {
            return UI_FONT_DEFAULT; // localStorage недоступен
        }
    }

    var current = applyUiFont(readStoredUiFont());

    document.addEventListener('DOMContentLoaded', function () {
        var uiSpinner = document.getElementById('uiFontSpinner');
        if (!uiSpinner) return;

        uiSpinner.value = current;

        uiSpinner.addEventListener('input', function () {
            var v = applyUiFont(parseInt(uiSpinner.value, 10));
            uiSpinner.value = v;
            try {
                localStorage.setItem(UI_FONT_KEY, String(v));
            } catch (e) { /* ignore */ }
        });
    });
})();
