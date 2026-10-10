package com.buruna.shared.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Extrai as páginas de um CBR usando o binário {@code bsdtar} (libarchive), que abre RAR4 e RAR5 e
 * também um ZIP renomeado para .cbr. O arquivo vem de usuário não confiável: nada passa por shell, o que
 * foi escrito em disco é vigiado e só arquivos regulares dentro do diretório temporário são lidos.
 */
@Component
public class RarArchiveExtractor {

    static final String INVALID_MESSAGE = "O arquivo não é um CBR válido";
    private static final Duration EXTRACTION_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration LIST_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration WATCH_INTERVAL = Duration.ofMillis(200);
    private static final String PASSWORD_MESSAGE = "O CBR está protegido por senha";
    private static final int LIST_OUTPUT_LIMIT_BYTES = 16 * 1024 * 1024;
    private static final int ERROR_OUTPUT_LIMIT_BYTES = 64 * 1024;

    private final String bsdtarPath;

    public RarArchiveExtractor(@Value("${app.ingest.bsdtar-path:bsdtar}") String bsdtarPath) {
        this.bsdtarPath = bsdtarPath;
    }

    /** Arquivo regular extraído; {@code name} é o caminho relativo ao diretório temporário, com {@code /}. */
    record ExtractedFile(String name, Path path) {
    }

    public void extractCbr(Path cbrFile, ArchiveLimits limits, Consumer<ExtractedPage> sink) {
        withExtractedFiles(cbrFile, limits, files -> {
            List<ExtractedFile> pages = ArchivePages.selectPages(files, ExtractedFile::name, limits);
            ArchivePages.Emitter emitter = new ArchivePages.Emitter(limits, sink);
            for (ExtractedFile page : pages) {
                emitter.emit(page.name(), readLimited(page, limits.maxPageBytes()));
            }
            return null;
        });
    }

    /**
     * Extrai para um diretório temporário novo, entrega os arquivos regulares a {@code action} e
     * apaga o diretório no fim, mesmo com erro.
     */
    <T> T withExtractedFiles(Path archive, ArchiveLimits limits, Function<List<ExtractedFile>, T> action) {
        checkDeclaredSizes(archive, limits);
        Path directory = createTempDirectory();
        try {
            runExtraction(archive, directory, limits.maxTotalBytes());
            return action.apply(listRegularFiles(directory));
        } finally {
            deleteRecursively(directory);
        }
    }

    // Recusa antecipada: o tamanho declarado pode mentir, a vigia da extração é a defesa real.
    private void checkDeclaredSizes(Path archive, ArchiveLimits limits) {
        List<ListedEntry> entries = runList(archive);
        long declaredTotal = entries.stream().mapToLong(ListedEntry::size).sum();
        if (declaredTotal > limits.maxTotalBytes()) {
            throw new InvalidArchiveException("O arquivo é grande demais depois de descompactado");
        }
        long candidates = entries.stream().filter(e -> ArchivePages.isPageCandidate(e.path())).count();
        ArchivePages.checkPageCount((int) Math.min(candidates, Integer.MAX_VALUE), limits);
    }

    private List<ListedEntry> runList(Path archive) {
        Process process = start(List.of(bsdtarPath, "-tvf", archive.toString()));
        CompletableFuture<byte[]> output = CompletableFuture.supplyAsync(() -> readBounded(process));
        try {
            byte[] bytes = output.get(LIST_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (bytes.length > LIST_OUTPUT_LIMIT_BYTES) {
                throw new InvalidArchiveException("O arquivo tem itens demais para um capítulo");
            }
            String text = new String(bytes, StandardCharsets.UTF_8);
            if (!process.waitFor(LIST_TIMEOUT.toSeconds(), TimeUnit.SECONDS) || process.exitValue() != 0) {
                throw failure(text);
            }
            return parseListing(text);
        } catch (TimeoutException e) {
            throw new InvalidArchiveException(INVALID_MESSAGE, e);
        } catch (ExecutionException e) {
            throw new InvalidArchiveException(INVALID_MESSAGE, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InvalidArchiveException("A extração foi interrompida", e);
        } finally {
            process.destroyForcibly();
        }
    }

    // O bsdtar não tem código de saída próprio para senha: a mensagem ("Encrypted file is unsupported") é o único sinal.
    private static InvalidArchiveException failure(String output) {
        boolean encrypted = output.toLowerCase(Locale.ROOT).contains("ncrypt");
        return new InvalidArchiveException(encrypted ? PASSWORD_MESSAGE : INVALID_MESSAGE);
    }

    private static byte[] readBounded(Process process) {
        try (InputStream in = process.getInputStream()) {
            byte[] bytes = in.readNBytes(LIST_OUTPUT_LIMIT_BYTES + 1);
            if (bytes.length > LIST_OUTPUT_LIMIT_BYTES) {
                process.destroyForcibly();
            }
            return bytes;
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    record ListedEntry(String path, long size) {
    }

    // Linha do "bsdtar -tvf": permissões, links, dono, grupo, tamanho, data (mês dia ano|hora) e nome.
    private static final Pattern LISTING_LINE = Pattern.compile(
            "^(\\S)\\S*\\s+\\d+\\s+\\S+\\s+\\S+\\s+(\\d+)\\s+[A-Za-z]{3}\\s+\\d{1,2}\\s+(?:\\d{4}|\\d{1,2}:\\d{2}) (.+)$");

    /** Lê a saída de {@code bsdtar -tvf}. Só entradas regulares (tipo {@code -}) contam; o resto é ignorado. */
    static List<ListedEntry> parseListing(String output) {
        List<ListedEntry> entries = new ArrayList<>();
        for (String line : output.split("\r?\n")) {
            Matcher matcher = LISTING_LINE.matcher(line);
            if (matcher.matches() && matcher.group(1).equals("-")) {
                entries.add(new ListedEntry(matcher.group(3), parseLongOrZero(matcher.group(2))));
            }
        }
        return entries;
    }

    private static long parseLongOrZero(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void runExtraction(Path archive, Path directory, long maxTotalBytes) {
        // Sem -P: o bsdtar recusa caminhos absolutos e "..", o que reforça a checagem de toRealPath.
        Process process = start(List.of(
                bsdtarPath, "-x", "-f", archive.toString(), "-C", directory.toString(), "--no-same-owner"));
        CompletableFuture<byte[]> errors = CompletableFuture.supplyAsync(() -> drainKeepingHead(process));
        try {
            ExtractionWatchdog.Outcome outcome = ExtractionWatchdog.supervise(
                    process, directory, maxTotalBytes, EXTRACTION_TIMEOUT, WATCH_INTERVAL);
            switch (outcome) {
                case TOO_BIG -> throw new InvalidArchiveException("O arquivo é grande demais depois de descompactado");
                case TIMED_OUT -> throw new InvalidArchiveException("A extração do arquivo demorou demais");
                case COMPLETED -> {
                    if (process.exitValue() != 0) {
                        throw failure(new String(awaitErrors(errors), StandardCharsets.UTF_8));
                    }
                }
            }
        } finally {
            process.destroyForcibly();
        }
    }

    private static byte[] awaitErrors(CompletableFuture<byte[]> errors) {
        try {
            return errors.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new byte[0];
        } catch (ExecutionException | TimeoutException e) {
            return new byte[0];
        }
    }

    // Guarda só o começo da saída e consome o resto, para o buffer do pipe nunca travar o processo.
    private static byte[] drainKeepingHead(Process process) {
        try (InputStream in = process.getInputStream()) {
            byte[] head = in.readNBytes(ERROR_OUTPUT_LIMIT_BYTES);
            in.transferTo(java.io.OutputStream.nullOutputStream());
            return head;
        } catch (IOException e) {
            return new byte[0];
        }
    }

    // Sem shell: argumentos em lista. A saída (stdout + stderr) é lida pelo chamador.
    private Process start(List<String> command) {
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        try {
            Process process = builder.start();
            // Sem stdin: se o bsdtar pedir algo, recebe fim de arquivo em vez de esperar para sempre.
            process.getOutputStream().close();
            return process;
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível executar o bsdtar (" + bsdtarPath + ")", e);
        }
    }

    /**
     * Só arquivos regulares, sem seguir links: symlinks (e qualquer outra coisa) são ignorados.
     * Confere o caminho real contra o diretório para barrar path traversal.
     */
    static List<ExtractedFile> listRegularFiles(Path directory) {
        try (Stream<Path> walk = Files.walk(directory)) {
            Path root = directory.toRealPath();
            List<ExtractedFile> files = new ArrayList<>();
            for (Path path : (Iterable<Path>) walk::iterator) {
                if (!ExtractionWatchdog.isRegularFile(path)) {
                    continue;
                }
                if (!path.toRealPath().startsWith(root)) {
                    throw new InvalidArchiveException(INVALID_MESSAGE);
                }
                String name = directory.relativize(path).toString().replace('\\', '/');
                files.add(new ExtractedFile(name, path));
            }
            return files;
        } catch (IOException e) {
            throw new InvalidArchiveException(INVALID_MESSAGE, e);
        }
    }

    private static byte[] readLimited(ExtractedFile file, long maxPageBytes) {
        try (InputStream in = Files.newInputStream(file.path())) {
            // O tamanho em disco é o real; lê no máximo maxPageBytes + 1 para o limite ser aplicado adiante.
            return in.readNBytes((int) Math.min(maxPageBytes + 1, Integer.MAX_VALUE - 8));
        } catch (IOException e) {
            throw new InvalidArchiveException(INVALID_MESSAGE, e);
        }
    }

    private static Path createTempDirectory() {
        try {
            return Files.createTempDirectory("buruna-cbr-");
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível criar o diretório temporário", e);
        }
    }

    static void deleteRecursively(Path directory) {
        try {
            Files.walkFileTree(directory, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    // O RAR pode criar diretórios sem permissão de escrita; sem isso o delete falha.
                    dir.toFile().setWritable(true);
                    dir.toFile().setExecutable(true);
                    dir.toFile().setReadable(true);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            // Melhor esforço: o diretório fica no tmp do sistema, que é limpo pelo SO.
        }
    }
}
