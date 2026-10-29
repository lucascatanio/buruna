package com.buruna.shared.media;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;

/**
 * Vigia um processo que descompacta para um diretório: o tamanho declarado no cabeçalho do arquivo
 * pode mentir, então o que vale é o que de fato foi escrito em disco.
 */
final class ExtractionWatchdog {

    enum Outcome { COMPLETED, TOO_BIG, TIMED_OUT }

    private ExtractionWatchdog() {
    }

    /** Espera o processo terminar. Se estourar o tamanho ou o tempo, mata o processo. */
    static Outcome supervise(Process process, Path directory, long maxBytes, Duration timeout, Duration interval) {
        long deadline = System.nanoTime() + timeout.toNanos();
        try {
            while (!process.waitFor(interval.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)) {
                if (sizeOf(directory) > maxBytes) {
                    kill(process);
                    return Outcome.TOO_BIG;
                }
                if (System.nanoTime() - deadline > 0) {
                    kill(process);
                    return Outcome.TIMED_OUT;
                }
            }
            // O processo pode ter terminado entre duas verificações.
            return sizeOf(directory) > maxBytes ? Outcome.TOO_BIG : Outcome.COMPLETED;
        } catch (InterruptedException e) {
            kill(process);
            Thread.currentThread().interrupt();
            throw new InvalidArchiveException("A extração foi interrompida", e);
        }
    }

    /** Soma o tamanho dos arquivos regulares, sem seguir links. */
    static long sizeOf(Path directory) {
        long[] total = {0};
        try {
            Files.walkFileTree(directory, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile()) {
                        total[0] += attrs.size();
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            // Falha ao medir não pode derrubar a vigia; a próxima rodada mede de novo.
        }
        return total[0];
    }

    private static void kill(Process process) {
        process.destroyForcibly();
        try {
            process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static boolean isRegularFile(Path path) {
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
    }
}
