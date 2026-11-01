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
 * POST /desktop-shortcut - creates a real Windows shell shortcut
 * "Milkromug.lnk" on the Desktop (via PowerShell + WScript.Shell) with
 * TargetPath = http://localhost:8080/bookmarklet and Hotkey = CTRL+ALT+M.
 * Prompt-14: a .lnk opens the URL in the default browser (a dragged
 * javascript: one-liner cannot be a desktop shortcut), and CTRL+ALT+M is free
 * system-wide (the previous combination was busy in Chrome). On non-Windows
 * and in headless mode responds with a friendly JSON {ok:false}, never a 500.
 */
@RestController
public class DesktopShortcutController {

    private static final String SHORTCUT_NAME = "Milkromug.lnk";
    private static final String TARGET_URL = "http://localhost:8080/bookmarklet";
    private static final String HOTKEY = "CTRL+ALT+M";

    @PostMapping(value = "/desktop-shortcut", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> createShortcut() {
        Map<String, Object> response = new LinkedHashMap<>();
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        boolean headless = java.awt.GraphicsEnvironment.isHeadless();

        if (!os.contains("win")) {
            response.put("ok", false);
            response.put("error", "Desktop shortcut is only available on Windows"
                    + " (current OS: " + os + "). The hotkey " + HOTKEY
                    + " will work after running Milkromug.exe on Windows.");
            return response;
        }
        if (headless) {
            response.put("ok", false);
            response.put("error", "Server runs in headless mode - no desktop available."
                    + " Start Milkromug.exe with a normal double click.");
            return response;
        }

        Path desktop = Path.of(System.getProperty("user.home"), "Desktop");
        Path lnk = desktop.resolve(SHORTCUT_NAME);
        // PS1 snippet via WScript.Shell per owner's note: TargetPath/Hotkey set locally.
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
                response.put("target", TARGET_URL);
                response.put("hotkey", HOTKEY);
            } else {
                response.put("ok", false);
                response.put("error", "PowerShell exited with code " + code
                        + ": " + out.trim());
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            response.put("ok", false);
            response.put("error", "Could not create the desktop shortcut: " + e.getMessage());
        }
        return response;
    }
}
