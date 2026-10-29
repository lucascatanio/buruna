package com.buruna.shared.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ExtractionWatchdogTest {

    private static final Duration INTERVAL = Duration.ofMillis(50);

    @TempDir
    Path dir;

    private Process shell(String script) throws IOException {
        assumeTrue(!System.getProperty("os.name").toLowerCase().contains("win"), "precisa de sh");
        return new ProcessBuilder("sh", "-c", script).start();
    }

    @Test
    void shouldReturnCompleted_whenProcessFinishesWithinLimits() throws IOException {
        Process process = shell("head -c 100 /dev/zero > '" + dir + "/f'");

        ExtractionWatchdog.Outcome outcome =
                ExtractionWatchdog.supervise(process, dir, 1000, Duration.ofSeconds(10), INTERVAL);

        assertThat(outcome).isEqualTo(ExtractionWatchdog.Outcome.COMPLETED);
    }

    @Test
    void shouldKillProcessAndReturnTooBig_whenDirectoryGrowsPastLimit() throws IOException {
        Process process = shell("head -c 5000 /dev/zero > '" + dir + "/f'; sleep 30");

        ExtractionWatchdog.Outcome outcome =
                ExtractionWatchdog.supervise(process, dir, 1000, Duration.ofSeconds(10), INTERVAL);

        assertThat(outcome).isEqualTo(ExtractionWatchdog.Outcome.TOO_BIG);
        assertThat(process.isAlive()).isFalse();
    }

    @Test
    void shouldKillProcessAndReturnTimedOut_whenProcessTakesTooLong() throws IOException {
        Process process = shell("sleep 30");

        ExtractionWatchdog.Outcome outcome =
                ExtractionWatchdog.supervise(process, dir, 1000, Duration.ofMillis(300), INTERVAL);

        assertThat(outcome).isEqualTo(ExtractionWatchdog.Outcome.TIMED_OUT);
        assertThat(process.isAlive()).isFalse();
    }

    @Test
    void shouldCountOnlyRegularFiles_whenMeasuringDirectory() throws IOException {
        Files.createDirectories(dir.resolve("a/b"));
        Files.write(dir.resolve("a/b/x"), new byte[300]);
        Files.write(dir.resolve("y"), new byte[200]);

        assertThat(ExtractionWatchdog.sizeOf(dir)).isEqualTo(500);
    }
}
