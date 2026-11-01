package com.bizonrelax.milkromug.config;

import java.awt.Desktop;
import java.net.URI;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Opens the dashboard http://localhost:PORT/ in the default browser
 * after the server is ready ("run it and use it").
 *
 * Guards:
 *  - headless mode (no GUI / java.awt.headless=true) - skipped;
 *  - command-line argument --no-open - skipped;
 *  - any exception during opening is logged, the app does NOT crash.
 */
@Component
public class BrowserAutoOpen implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(BrowserAutoOpen.class);

    private final Environment environment;

    /**
     * Аргументы командной строки JVM недоступны напрямую из Spring-события,
     * поэтому проверяем системное свойство no-open (Spring Boot mapToSystemProperties)
     * и переменную окружения MILKROMUG_NO_OPEN как резервный вариант.
     */
    private boolean isNoOpenRequested() {
        if (Boolean.parseBoolean(System.getProperty("no-open", "false"))) {
            return true;
        }
        String env = System.getenv("MILKROMUG_NO_OPEN");
        return env != null && !env.isBlank() && !"0".equals(env);
    }

    public BrowserAutoOpen(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (Boolean.getBoolean("java.awt.headless")
                || !Desktop.isDesktopSupported()
                || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            log.info("Browser auto-open skipped: headless or Desktop.Browse is not supported.");
            return;
        }
        if (isNoOpenRequested()) {
            log.info("Browser auto-open skipped: --no-open argument was passed.");
            return;
        }
        int port = environment.getProperty("server.port", Integer.class, 8080);
        String url = "http://localhost:" + port + "/";
        try {
            Desktop.getDesktop().browse(new URI(url));
            // Prompt-13/Prompt-14: diagnostics instead of silence - English only (no mojibake).
            System.out.println("Browser opened: " + url);
            log.info("Browser opened in default OS browser: {}", url);
        } catch (Exception e) {
            System.out.println("Could not open browser automatically - go to http://localhost:" + port);
            log.warn("Browser open skipped: could not open browser automatically ({})."
                    + " Open {} manually.", e.getMessage(), url);
        }
    }
}
