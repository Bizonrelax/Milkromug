package com.bizonrelax.milkromug.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Prompt-16: GET /debug/env - environment facts for future diagnostics.
 * No secrets: only headless flags, OS name and desktop availability.
 */
@RestController
public class DebugEnvController {

    @GetMapping(value = "/debug/env", produces = "application/json")
    public Map<String, Object> env() {
        Map<String, Object> m = new LinkedHashMap<>();
        boolean headless = java.awt.GraphicsEnvironment.isHeadless();
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        java.nio.file.Path desktop = java.nio.file.Path.of(
                System.getenv().getOrDefault("USERPROFILE", System.getProperty("user.home")),
                "Desktop");
        m.put("headless", headless ? 1 : 0);
        m.put("headlessProperty", System.getProperty("java.awt.headless", "unset"));
        m.put("osName", System.getProperty("os.name", ""));
        m.put("desktopSupported", (!headless && os.contains("win")) ? 1 : 0);
        m.put("desktopPath", desktop.toString());
        return m;
    }
}
