package com.buruna.shared.source.mangadownloader;

import com.buruna.shared.source.ExternalSourceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Roda o cliente contra um binário falso (script sh) que imita a CLI. */
class MangaDownloaderClientTest {

    private static final String URL = "https://site.test/manga/obra";

    @TempDir
    Path temp;

    private MangaDownloaderClient client(String script, Duration timeout) throws IOException {
        Path binary = temp.resolve("manga-downloader");
        Files.writeString(binary, "#!/bin/sh\n" + script);
        assertThat(binary.toFile().setExecutable(true)).isTrue();
        return new MangaDownloaderClient(binary.toString(), new ObjectMapper(), timeout, timeout, 1, 4);
    }

    @Test
    void shouldParseListing_whenCliPrintsJson() throws IOException {
        MangaDownloaderClient client = client("""
                echo "$@" > "$(dirname "$0")/args"
                cat <<'JSON'
                {"title":"Obra","grabber":"PlainHTML","chapters":[
                  {"id":"c1","number":1,"title":"Capítulo 1","language":"es","group":"A, B","groups":["A, B"],"volume":2},
                  {"number":2,"title":"Capítulo 2","locked":true}],
                 "languages":null,"scanlators":{"values":["A, B"],"all":true},"volumes":""}
                JSON
                echo "aviso qualquer" >&2
                """, Duration.ofSeconds(10));

        SiteListing listing = client.list(URL);

        assertThat(listing.title()).isEqualTo("Obra");
        assertThat(listing.chapters()).hasSize(2);
        assertThat(listing.chapters().get(0).groups()).containsExactly("A, B");
        assertThat(listing.chapters().get(0).volume()).isEqualTo(2);
        assertThat(listing.chapters().get(1).locked()).isTrue();
        assertThat(listing.chapters().get(1).id()).isNull();
        assertThat(listing.languages()).isNull();
        assertThat(Files.readString(temp.resolve("args")).strip()).isEqualTo("list --json --language all -- " + URL);
    }

    @Test
    void shouldListOneLanguage_whenLanguageIsGiven() throws IOException {
        MangaDownloaderClient client = client("""
                echo "$@" >> "$(dirname "$0")/args"
                echo '{"title":"Obra","grabber":"Mangafire","chapters":[]}'
                """, Duration.ofSeconds(10));

        client.list(URL, "pt-br");
        client.list(URL, "");

        assertThat(Files.readAllLines(temp.resolve("args")))
                .containsExactly("list --json --language pt-br -- " + URL, "list --json -- " + URL);
    }

    @Test
    void shouldReturnArchive_whenCliWritesIt() throws IOException {
        // a CLI grava o CBZ no --output-dir com o nome do template ({{.Number}})
        MangaDownloaderClient client = client("""
                echo "$@" > "$(dirname "$0")/args"
                touch "$2/12.5.cbz"
                """, Duration.ofSeconds(10));
        Path directory = Files.createDirectory(temp.resolve("out"));

        Path archive = client.download(URL, "12.5", "es", "A, B", directory);

        assertThat(archive).isEqualTo(directory.resolve("12.5.cbz"));
        assertThat(Files.readString(temp.resolve("args")).strip()).isEqualTo(
                "--output-dir " + directory + " --format cbz --filename-template {{.Number}} --concurrency 1"
                        + " --concurrency-pages 4 --language es --scanlator A, B -- " + URL + " 12.5");
    }

    @Test
    void shouldExplainFailure_whenCliWritesNothing() throws IOException {
        MangaDownloaderClient client = client("exit 0\n", Duration.ofSeconds(10));
        Path directory = Files.createDirectory(temp.resolve("out"));

        assertThatThrownBy(() -> client.download(URL, "3", "", "", directory))
                .isInstanceOf(ExternalSourceException.class)
                .hasMessageContaining("não gravou o capítulo 3");
    }

    @Test
    void shouldCarryCliReason_whenCliExitsZeroWithoutArchive() throws IOException {
        // a CLI conta a página que falhou na própria saída e sai com 0
        MangaDownloaderClient client = client("""
                echo "downloading Capítulo 59..."
                printf '\\033[31mfailed Capítulo 59: page 12: 502 Bad Gateway\\033[0m\\n'
                """, Duration.ofSeconds(10));
        Path directory = Files.createDirectory(temp.resolve("out"));

        assertThatThrownBy(() -> client.download(URL, "59", "", "", directory))
                .isInstanceOf(ExternalSourceException.class)
                .hasMessageEndingWith("failed Capítulo 59: page 12: 502 Bad Gateway")
                .hasMessageStartingWith("O manga-downloader não gravou o capítulo 59. ");
    }

    @Test
    void shouldCarryTailOfStderrWithoutColors_whenCliFails() throws IOException {
        MangaDownloaderClient client = client("""
                printf '\\033[31mSite not recognised\\033[0m\\n' >&2
                exit 1
                """, Duration.ofSeconds(10));

        assertThatThrownBy(() -> client.list(URL))
                .isInstanceOf(ExternalSourceException.class)
                .hasMessage("O manga-downloader falhou: Site not recognised");
    }

    @Test
    void shouldKillCli_whenItPassesTimeout() throws IOException {
        MangaDownloaderClient client = client("sleep 5\n", Duration.ofMillis(300));

        assertThatThrownBy(() -> client.list(URL))
                .isInstanceOf(ExternalSourceException.class)
                .hasMessageContaining("passou de");
    }

    @Test
    void shouldFail_whenListingIsNotJson() throws IOException {
        MangaDownloaderClient client = client("echo 'Do you want to download all 3 chapters?'\n", Duration.ofSeconds(10));

        assertThatThrownBy(() -> client.list(URL))
                .isInstanceOf(ExternalSourceException.class)
                .hasMessageContaining("ilegível");
    }

    @Test
    void shouldKeepOnlyTheEnd_whenStderrIsLong() {
        String tail = MangaDownloaderClient.tail("x".repeat(2000) + "fim");

        assertThat(tail).hasSize(501).startsWith("…").endsWith("fim");
    }
}
