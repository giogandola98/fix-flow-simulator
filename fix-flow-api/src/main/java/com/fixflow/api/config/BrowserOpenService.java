// Copyright (c) 2026 Giorgio Gandola <giogandola@gmail.com>
// FIX Flow Simulator — https://github.com/giogandola98/fix-flow-simulator
// Licensed under the FIX Flow Simulator Source Available License v1.0.
// Commercial use requires a separate license. See LICENSE for details.
package com.fixflow.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.awt.Desktop;
import java.net.URI;

@Component
public class BrowserOpenService {

    private static final Logger log = LoggerFactory.getLogger(BrowserOpenService.class);

    @Value("${server.port:8080}")
    private int port;

    @Value("${fixflow.browser.auto-open:true}")
    private boolean autoOpen;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        String url = "http://localhost:" + port;
        log.info("");
        log.info("╔══════════════════════════════════════════════╗");
        log.info("║  FIX Flow Simulator ready                    ║");
        log.info("║  {}  ║", padRight(url, 44));
        log.info("╚══════════════════════════════════════════════╝");
        log.info("");

        if (!autoOpen) return;

        try {
            openBrowser(url);
        } catch (Exception e) {
            log.debug("Browser auto-open failed: {}", e.getMessage());
        }
    }

    private void openBrowser(String url) throws Exception {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI.create(url));
            return;
        }
        if (os.contains("win")) {
            Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", url});
        } else if (os.contains("mac")) {
            Runtime.getRuntime().exec(new String[]{"open", url});
        } else {
            Runtime.getRuntime().exec(new String[]{"xdg-open", url});
        }
    }

    private static String padRight(String s, int n) {
        if (s.length() >= n) return s;
        return s + " ".repeat(n - s.length());
    }
}
