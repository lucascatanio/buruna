package com.buruna.shared.source.mangadownloader;

import com.buruna.shared.source.ExternalSourceException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Roda a CLI do manga-downloader (ADR-54) como processo separado, sem shell: lista uma série
 * ({@code list --json}) ou baixa um capítulo em CBZ. O binário é AGPL e roda sem modificação; o
 * Burūna só lê o que ele grava.
 *
 * <p>A saída vai para arquivos, não para pipes: um processo que escreve muito no stderr não
 * trava esperando alguém ler. Passou do tempo, o processo é morto.
 */
public class MangaDownloaderClient {

    private static final Logger log = LoggerFactory.getLogger(MangaDownloaderClient.class);
    private static final int ERROR_TAIL_LENGTH = 500;
    private static final Pattern ANSI = Pattern.compile("\\u001B\\[[0-9;?]*[A-Za-z]");
    // o nome do CBZ é o número do capítulo; a versão de mesmo nome ganha " v2", que não importa aqui
    private static final String FILENAME_TEMPLATE = "{{.Number}}";
    private static final String ALL = "all";

    private final String binary;
    private final ObjectMapper objectMapper;
    private final Duration listTimeout;
    private final Duration downloadTimeout;
    private final int chapterConcurrency;
    private final int pageConcurrency;

    public MangaDownloaderClient(String binary, ObjectMapper objectMapper, Duration listTimeout,
                                 Duration downloadTimeout, int chapterConcurrency, int pageConcurrency) {
        this.binary = binary;
        this.objectMapper = objectMapper;
        this.listTimeout = listTimeout;
        this.downloadTimeout = downloadTimeout;
        this.chapterConcurrency = chapterConcurrency;
        this.pageConcurrency = pageConcurrency;
    }

    /** Lista a série em todos os idiomas e grupos, inclusive os capítulos bloqueados. */
    public SiteListing list(String seriesUrl) {
        Output output = run(List.of("list", "--json", "--language", ALL, "--", seriesUrl), listTimeout);
        try {
            SiteListing listing = objectMapper.readValue(output.stdout(), SiteListing.class);
            if (listing.chapters() == null) {
                throw new ExternalSourceException("Listagem do manga-downloader sem capítulos: " + seriesUrl);
            }
            return listing;
        } catch (JsonProcessingException e) {
            throw new ExternalSourceException("Listagem do manga-downloader ilegível: " + seriesUrl, e);
        }
    }

    /**
     * Baixa o capítulo {@code number} em CBZ para {@code directory}, que deve estar vazio, e
     * devolve o arquivo. {@code language} e {@code group} vazios: sem filtro.
     */
    public Path download(String seriesUrl, String number, String language, String group, Path directory) {
        List<String> args = new ArrayList<>(List.of(
                "--output-dir", directory.toString(),
                "--format", "cbz",
                "--filename-template", FILENAME_TEMPLATE,
                "--concurrency", String.valueOf(chapterConcurrency),
                "--concurrency-pages", String.valueOf(pageConcurrency)));
        if (!language.isEmpty()) {
            args.addAll(List.of("--language", language));
        }
        if (!group.isEmpty()) {
            args.addAll(List.of("--scanlator", group));
        }
        // "--" antes dos posicionais: uma URL que comece com "-" não vira flag da CLI
        args.addAll(List.of("--", seriesUrl, number));
        run(args, downloadTimeout);
        try (Stream<Path> files = Files.list(directory)) {
            List<Path> archives = files.filter(file -> file.getFileName().toString().endsWith(".cbz")).sorted().toList();
            if (archives.isEmpty()) {
                throw new ExternalSourceException("O manga-downloader não gravou o capítulo " + number
                        + ". Ele pode estar bloqueado ou fora do idioma e do grupo pedidos.");
            }
            if (archives.size() > 1) {
                log.warn("Capítulo {} de {} veio em {} arquivos; fica o primeiro", number, seriesUrl, archives.size());
            }
            return archives.get(0);
        } catch (IOException e) {
            throw new ExternalSourceException("Não foi possível ler o que o manga-downloader gravou", e);
        }
    }

    private Output run(List<String> args, Duration timeout) {
        List<String> command = new ArrayList<>();
        command.add(binary);
        command.addAll(args);
        Path stdout = null;
        Path stderr = null;
        try {
            stdout = Files.createTempFile("manga-downloader-", ".out");
            stderr = Files.createTempFile("manga-downloader-", ".err");
            ProcessBuilder builder = new ProcessBuilder(command)
                    .redirectOutput(stdout.toFile())
                    .redirectError(stderr.toFile());
            builder.environment().putAll(Map.of("NO_COLOR", "1", "MANGA_DOWNLOADER_NO_UPDATE_CHECK", "1"));
            Process process = builder.start();
            // entrada fechada: uma pergunta da CLI ("baixar tudo?") lê fim de arquivo e recusa
            process.getOutputStream().close();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new ExternalSourceException("O manga-downloader passou de " + timeout.toMinutes() + " min e foi interrompido.");
            }
            String errors = Files.readString(stderr, StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                throw new ExternalSourceException("O manga-downloader falhou: " + tail(errors));
            }
            return new Output(Files.readString(stdout, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ExternalSourceException("Não foi possível rodar o manga-downloader.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalSourceException("Execução do manga-downloader interrompida", e);
        } finally {
            deleteQuietly(stdout);
            deleteQuietly(stderr);
        }
    }

    // o fim do stderr é onde a CLI diz o que deu errado; sem as cores do terminal
    static String tail(String errors) {
        String clean = ANSI.matcher(errors).replaceAll("").strip();
        return clean.length() <= ERROR_TAIL_LENGTH ? clean : "…" + clean.substring(clean.length() - ERROR_TAIL_LENGTH);
    }

    private static void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Não foi possível apagar {}", file, e);
        }
    }

    private record Output(String stdout) {
    }
}
