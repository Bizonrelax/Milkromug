package com.bizonrelax.milkromug.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * POST /desktop-shortcut — создаёт на рабочем столе Windows ярлык
 * «Milkromug Capture.lnk» (через PowerShell + WScript.Shell) с горячей
 * клавишей CTRL+SHIFT+G, открывающий http://localhost:8080/bookmarklet.
 * На не-Windows и в headless отвечает дружелюбный JSON {ok:false} без 500.
 */
@RestController
public class DesktopShortcutController {

    private static final String SHORTCUT_NAME = "Milkromug Capture.lnk";
    private static final String TARGET_URL = "http://localhost:8080/bookmarklet";
    private static final String HOTKEY = "CTRL+SHIFT+G";

    @PostMapping(value = "/desktop-shortcut", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> createShortcut() {
        Map<String, Object> response = new LinkedHashMap<>();
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        boolean headless = java.awt.GraphicsEnvironment.isHeadless();

        if (!os.contains("win")) {
            response.put("ok", false);
            response.put("error", "Ярлык рабочего стола доступен только на Windows"
                    + " (сейчас: " + os + "). Горячая клавиша " + HOTKEY
                    + " появится после запуска Milkromug.exe на Windows.");
            return response;
        }
        if (headless) {
            response.put("ok", false);
            response.put("error", "Сервер запущен в headless-режиме — рабочий стол недоступен."
                    + " Запустите Milkromug.exe обычным двойным кликом.");
            return response;
        }

        Path desktop = Path.of(System.getProperty("user.home"), "Desktop");
        Path lnk = desktop.resolve(SHORTCUT_NAME);
        // PS1-скрипт WScript.Shell по сниппету владельца: TargetPath/Hotkey задаются локально.
        String ps = "$ws = New-Object -ComObject WScript.Shell;"
                + " $s = $ws.CreateShortcut('" + lnk.toString().replace("'", "''") + "');"
                + " $s.TargetPath = '" + TARGET_URL + "';"
                + " $s.Hotkey = '" + HOTKEY + "';"
                + " $s.Description = 'Milkromug chat capture';"
                + " $s.Save()";
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell", "-NoProfile", "-NonInteractive", "-Command", ps);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes());
            int code = p.waitFor();
            if (code == 0 && Files.exists(lnk)) {
                response.put("ok", true);
                response.put("path", lnk.toString());
                response.put("hotkey", HOTKEY);
            } else {
                response.put("ok", false);
                response.put("error", "PowerShell завершился с кодом " + code
                        + ": " + out.trim());
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            response.put("ok", false);
            response.put("error", "Не удалось создать ярлык: " + e.getMessage());
        }
        return response;
    }
}
