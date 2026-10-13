// Copyright (c) 2026 Giorgio Gandola <giogandola@gmail.com>
// FIX Flow Simulator — https://github.com/giogandola98/fix-flow-simulator
// Licensed under the FIX Flow Simulator Source Available License v1.0.
// Commercial use requires a separate license. See LICENSE for details.
package com.fixflow.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@SpringBootApplication(scanBasePackages = "com.fixflow")
@EntityScan(basePackages = "com.fixflow.adapters.persistence.entity")
@EnableJpaRepositories(basePackages = "com.fixflow.adapters.persistence.jpa")
public class FixFlowApplication {

    static final String NO_RELAUNCH_PROPERTY = "fixflow.no-relaunch";
    static final String WINDOW_TITLE = "FIX Flow Simulator";
    static final String[] LINUX_TERMINALS =
        {"gnome-terminal", "konsole", "xfce4-terminal", "xterm", "x-terminal-emulator"};

    public static void main(String[] args) {
        // No console + a desktop display = double-clicked JAR; re-launch inside a terminal window.
        // Services, containers and SSH sessions start directly with the original arguments.
        String os = System.getProperty("os.name", "");
        if (shouldRelaunch(os, System.getenv(), GraphicsEnvironment.isHeadless(),
                System.console() != null, Boolean.getBoolean(NO_RELAUNCH_PROPERTY))) {
            try {
                List<String> command = buildRelaunchCommand(os,
                    ProcessHandle.current().info().command().orElse("java"),
                    jarPath(),
                    ManagementFactory.getRuntimeMXBean().getInputArguments(),
                    args,
                    findExecutableOnPath(System.getenv("PATH"), LINUX_TERMINALS));
                if (command != null) {
                    new ProcessBuilder(command).inheritIO().start();
                    return;
                }
            } catch (Exception e) {
                System.err.println("FIX Flow: terminal relaunch failed, starting in this process: " + e);
            }
        }
        SpringApplication.run(FixFlowApplication.class, args);
    }

    /**
     * Relaunch only for an interactive desktop start without a console (double-clicked JAR).
     */
    static boolean shouldRelaunch(String osName, Map<String, String> env, boolean headless,
                                  boolean hasConsole, boolean noRelaunch) {
        if (noRelaunch || hasConsole || headless) return false;
        String os = osName.toLowerCase(Locale.ROOT);
        if (os.contains("win") || os.contains("mac")) return true;
        // Linux/Unix: without an X11 or Wayland display no terminal window can be opened
        return !isBlank(env.get("DISPLAY")) || !isBlank(env.get("WAYLAND_DISPLAY"));
    }

    /**
     * Builds the command that re-runs this JAR inside a terminal window, forwarding the
     * original JVM options and program arguments.
     *
     * @return the command, or {@code null} when no relaunch is possible (not running from a
     *         JAR, or no terminal emulator found on Linux)
     */
    static List<String> buildRelaunchCommand(String osName, String javaExe, String jarPath,
                                             List<String> jvmArgs, String[] args,
                                             String linuxTerminal) {
        if (jarPath == null || !jarPath.toLowerCase(Locale.ROOT).endsWith(".jar")) return null;

        List<String> java = new ArrayList<>();
        java.add(javaExe);
        for (String a : jvmArgs) {
            if (isForwardableJvmArg(a)) java.add(a);
        }
        java.add("-D" + NO_RELAUNCH_PROPERTY + "=true");
        java.add("-jar");
        java.add(jarPath);
        java.addAll(List.of(args));

        String os = osName.toLowerCase(Locale.ROOT);
        List<String> exec = new ArrayList<>();
        if (os.contains("win")) {
            // cmd /c start "title" cmd /k java ... — each token separate, ProcessBuilder quotes them
            exec.addAll(List.of("cmd", "/c", "start", WINDOW_TITLE, "cmd", "/k"));
            exec.addAll(java);
        } else if (os.contains("mac")) {
            String script = "tell application \"Terminal\" to do script \"" +
                            appleScriptEscape(shellJoin(java)) + "\"";
            exec.addAll(List.of("osascript", "-e", script));
        } else {
            if (linuxTerminal == null) return null;
            String bashCmd = shellJoin(java) + "; echo; read -p 'Press Enter to close...'";
            if (linuxTerminal.equals("gnome-terminal")) {
                exec.addAll(List.of(linuxTerminal, "--", "bash", "-c", bashCmd));
            } else {
                exec.addAll(List.of(linuxTerminal, "-e", "bash", "-c", bashCmd));
            }
        }
        return exec;
    }

    /** Debug agents would clash on their port in the relaunched JVM; the guard flag is re-added. */
    static boolean isForwardableJvmArg(String arg) {
        return !arg.startsWith("-agentlib:jdwp")
            && !arg.startsWith("-Xrunjdwp")
            && !arg.startsWith("-D" + NO_RELAUNCH_PROPERTY);
    }

    /** POSIX shell quoting: every token in single quotes, embedded quotes escaped. */
    static String shellJoin(List<String> tokens) {
        StringBuilder sb = new StringBuilder();
        for (String t : tokens) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append('\'').append(t.replace("'", "'\\''")).append('\'');
        }
        return sb.toString();
    }

    static String appleScriptEscape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** Looks up the first candidate executable in the given PATH (no dependency on `which`). */
    static String findExecutableOnPath(String path, String... candidates) {
        if (isBlank(path)) return null;
        for (String candidate : candidates) {
            for (String dir : path.split(File.pathSeparator)) {
                if (dir.isEmpty()) continue;
                File f = new File(dir, candidate);
                if (f.isFile() && f.canExecute()) return candidate;
            }
        }
        return null;
    }

    private static String jarPath() {
        // java.class.path == the JAR path when launched with java -jar
        String cp = System.getProperty("java.class.path", "");
        File f = new File(cp);
        return f.isFile() ? f.getAbsolutePath() : null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
