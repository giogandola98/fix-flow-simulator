// Copyright (c) 2026 Giorgio Gandola <giogandola@gmail.com>
// FIX Flow Simulator — https://github.com/giogandola98/fix-flow-simulator
// Licensed under the FIX Flow Simulator Source Available License v1.0.
// Commercial use requires a separate license. See LICENSE for details.
package com.fixflow.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@SpringBootApplication(scanBasePackages = "com.fixflow")
@EntityScan(basePackages = "com.fixflow.adapters.persistence.entity")
@EnableJpaRepositories(basePackages = "com.fixflow.adapters.persistence.jpa")
public class FixFlowApplication {

    public static void main(String[] args) {
        // No console = double-clicked JAR; re-launch inside a terminal window
        if (System.console() == null && !Boolean.getBoolean("fixflow.no-relaunch")) {
            try { relaunchInTerminal(); } catch (Exception e) { /* fall through to normal start */ }
            return;
        }
        SpringApplication.run(FixFlowApplication.class, args);
    }

    private static void relaunchInTerminal() throws Exception { // NOSONAR
        String jarPath = jarPath();
        String javaExe = ProcessHandle.current().info().command().orElse("java");
        String cmd = "\"" + javaExe + "\" -Dfixflow.no-relaunch=true -jar \"" + jarPath + "\"";

        String os = System.getProperty("os.name", "").toLowerCase();
        List<String> exec = new ArrayList<>();

        if (os.contains("win")) {
            exec.addAll(List.of("cmd", "/c", "start", "cmd", "/k", cmd));
        } else if (os.contains("mac")) {
            String script = "tell application \"Terminal\" to do script \"" + cmd + "\"";
            exec.addAll(List.of("osascript", "-e", script));
        } else {
            // Linux — try common terminal emulators in order
            String found = findTerminal("gnome-terminal", "konsole", "xfce4-terminal", "xterm", "x-terminal-emulator");
            if (found == null) {
                // No terminal found — just run normally without relaunching
                SpringApplication.run(FixFlowApplication.class, new String[0]);
                return;
            }
            if (found.equals("gnome-terminal")) {
                exec.addAll(List.of(found, "--", "bash", "-c", cmd + "; echo; read -p 'Press Enter to close...'"));
            } else if (found.equals("konsole")) {
                exec.addAll(List.of(found, "-e", "bash", "-c", cmd + "; echo; read -p 'Press Enter to close...'"));
            } else {
                exec.addAll(List.of(found, "-e", "bash", "-c", cmd + "; echo; read -p 'Press Enter to close...'"));
            }
        }

        new ProcessBuilder(exec).inheritIO().start();
    }

    private static String jarPath() {
        // java.class.path == the JAR path when launched with java -jar
        String cp = System.getProperty("java.class.path", "");
        File f = new File(cp);
        return f.isFile() ? f.getAbsolutePath() : cp;
    }

    private static String findTerminal(String... candidates) {
        for (String t : candidates) {
            try {
                Process p = new ProcessBuilder("which", t).start();
                if (p.waitFor() == 0) return t;
            } catch (Exception ignored) {}
        }
        return null;
    }
}
