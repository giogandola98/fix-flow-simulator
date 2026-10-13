package com.fixflow.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the double-click relaunch logic (issue #108): decision, command building,
 * quoting and terminal lookup are pure functions, so no process is started and Spring is not booted.
 */
class FixFlowApplicationRelaunchTest {

    private static final String[] ARGS = {"--server.port=9090", "--spring.h2.console.enabled=false"};
    private static final List<String> JVM = List.of("-Xmx512m", "-Dfoo=bar baz");

    // --- shouldRelaunch ---

    @Test
    void headlessNeverRelaunches() {
        assertThat(FixFlowApplication.shouldRelaunch("Windows 11", Map.of(), true, false, false)).isFalse();
        assertThat(FixFlowApplication.shouldRelaunch("Linux", Map.of("DISPLAY", ":0"), true, false, false)).isFalse();
    }

    @Test
    void linuxWithoutDisplayDoesNotRelaunch() {
        // systemd service / SSH session: no console and no X11/Wayland display
        assertThat(FixFlowApplication.shouldRelaunch("Linux", Map.of(), false, false, false)).isFalse();
        assertThat(FixFlowApplication.shouldRelaunch("Linux", Map.of("DISPLAY", " "), false, false, false)).isFalse();
    }

    @Test
    void linuxDesktopRelaunches() {
        assertThat(FixFlowApplication.shouldRelaunch("Linux", Map.of("DISPLAY", ":0"), false, false, false)).isTrue();
        assertThat(FixFlowApplication.shouldRelaunch("Linux", Map.of("WAYLAND_DISPLAY", "wayland-0"), false, false, false)).isTrue();
    }

    @Test
    void consoleOrGuardFlagPreventsRelaunch() {
        assertThat(FixFlowApplication.shouldRelaunch("Windows 11", Map.of(), false, true, false)).isFalse();
        assertThat(FixFlowApplication.shouldRelaunch("Mac OS X", Map.of(), false, false, true)).isFalse();
    }

    @Test
    void windowsAndMacDesktopRelaunch() {
        assertThat(FixFlowApplication.shouldRelaunch("Windows 11", Map.of(), false, false, false)).isTrue();
        assertThat(FixFlowApplication.shouldRelaunch("Mac OS X", Map.of(), false, false, false)).isTrue();
    }

    // --- buildRelaunchCommand ---

    @Test
    void windowsForwardsJvmArgsAndArgsAsSeparateTokens() {
        List<String> cmd = FixFlowApplication.buildRelaunchCommand("Windows 11",
            "C:\\Program Files\\Java\\bin\\java.exe", "C:\\My Apps\\fix-flow.jar", JVM, ARGS, null);

        assertThat(cmd).containsExactly("cmd", "/c", "start", "FIX Flow Simulator", "cmd", "/k",
            "C:\\Program Files\\Java\\bin\\java.exe", "-Xmx512m", "-Dfoo=bar baz",
            "-Dfixflow.no-relaunch=true", "-jar", "C:\\My Apps\\fix-flow.jar",
            "--server.port=9090", "--spring.h2.console.enabled=false");
    }

    @Test
    void linuxQuotesEveryTokenAndForwardsArgs() {
        List<String> cmd = FixFlowApplication.buildRelaunchCommand("Linux",
            "/usr/bin/java", "/opt/it's here/fix-flow.jar", JVM, ARGS, "xterm");

        assertThat(cmd).hasSize(5).startsWith("xterm", "-e", "bash", "-c");
        assertThat(cmd.get(4)).startsWith(
            "'/usr/bin/java' '-Xmx512m' '-Dfoo=bar baz' '-Dfixflow.no-relaunch=true' '-jar' "
            + "'/opt/it'\\''s here/fix-flow.jar' '--server.port=9090' '--spring.h2.console.enabled=false'");
    }

    @Test
    void gnomeTerminalUsesDoubleDashSeparator() {
        List<String> cmd = FixFlowApplication.buildRelaunchCommand("Linux",
            "/usr/bin/java", "/opt/app.jar", List.of(), new String[0], "gnome-terminal");

        assertThat(cmd).startsWith("gnome-terminal", "--", "bash", "-c");
    }

    @Test
    void linuxWithoutTerminalReturnsNullSoCallerStartsWithOriginalArgs() {
        assertThat(FixFlowApplication.buildRelaunchCommand("Linux",
            "/usr/bin/java", "/opt/app.jar", JVM, ARGS, null)).isNull();
    }

    @Test
    void macEscapesForAppleScriptAndShell() {
        List<String> cmd = FixFlowApplication.buildRelaunchCommand("Mac OS X",
            "/usr/bin/java", "/Users/me/My \"Apps\"/it's.jar", List.of(), ARGS, null);

        assertThat(cmd).hasSize(3).startsWith("osascript", "-e");
        assertThat(cmd.get(2)).isEqualTo(
            "tell application \"Terminal\" to do script \""
            + "'/usr/bin/java' '-Dfixflow.no-relaunch=true' '-jar' "
            + "'/Users/me/My \\\"Apps\\\"/it'\\\\''s.jar' "
            + "'--server.port=9090' '--spring.h2.console.enabled=false'\"");
    }

    @Test
    void notRunningFromJarReturnsNull() {
        assertThat(FixFlowApplication.buildRelaunchCommand("Windows 11",
            "java", null, JVM, ARGS, null)).isNull();
        assertThat(FixFlowApplication.buildRelaunchCommand("Windows 11",
            "java", "target/classes", JVM, ARGS, null)).isNull();
    }

    @Test
    void debugAgentAndGuardFlagAreNotForwarded() {
        List<String> cmd = FixFlowApplication.buildRelaunchCommand("Windows 11", "java", "app.jar",
            List.of("-agentlib:jdwp=transport=dt_socket,server=y,address=5005",
                    "-Xrunjdwp:transport=dt_socket", "-Dfixflow.no-relaunch=false", "-Xmx1g"),
            new String[0], null);

        assertThat(cmd).containsSubsequence("java", "-Xmx1g", "-Dfixflow.no-relaunch=true", "-jar", "app.jar")
            .noneMatch(t -> t.contains("jdwp"))
            .doesNotContain("-Dfixflow.no-relaunch=false");
    }

    // --- findExecutableOnPath ---

    @Test
    void findsFirstCandidatePresentOnPath(@TempDir Path dir1, @TempDir Path dir2) throws IOException {
        executable(dir2, "xterm");
        executable(dir2, "konsole");
        String path = dir1 + File.pathSeparator + File.pathSeparator + dir2;

        assertThat(FixFlowApplication.findExecutableOnPath(path, "gnome-terminal", "konsole", "xterm"))
            .isEqualTo("konsole");
    }

    @Test
    void returnsNullWhenNothingFoundOrPathMissing(@TempDir Path dir) {
        assertThat(FixFlowApplication.findExecutableOnPath(dir.toString(), "xterm")).isNull();
        assertThat(FixFlowApplication.findExecutableOnPath(null, "xterm")).isNull();
        assertThat(FixFlowApplication.findExecutableOnPath("", "xterm")).isNull();
    }

    private static void executable(Path dir, String name) throws IOException {
        File f = Files.createFile(dir.resolve(name)).toFile();
        assertThat(f.setExecutable(true)).isTrue();
    }
}
