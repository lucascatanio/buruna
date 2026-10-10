package com.buruna.shared.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class RarArchiveExtractorTest {

    private static final ArchiveLimits BIG = new ArchiveLimits(1000, 100_000_000L, 50_000_000L);

    @TempDir
    Path tmp;

    private static boolean sevenZipInstalled() {
        try {
            Process process = new ProcessBuilder("7zz", "i").redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            process.getOutputStream().close();
            return process.waitFor() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void assumeSevenZip() {
        assumeTrue(sevenZipInstalled(), "7zz não está no PATH");
    }

    private static void assumeUnix() {
        assumeTrue(!System.getProperty("os.name").toLowerCase().contains("win"), "precisa de sh");
    }

    private Path fixture(String name) throws IOException {
        Path target = tmp.resolve(name);
        try (InputStream in = getClass().getResourceAsStream("/media/" + name)) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    private static byte[] png(int w, int h) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    private Path zipRenamedToCbr(String... names) throws IOException {
        Path file = tmp.resolve("comic.cbr");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(file))) {
            for (String name : names) {
                zos.putNextEntry(new ZipEntry(name));
                zos.write(png(10, 20));
                zos.closeEntry();
            }
        }
        return file;
    }

    // --- Com o 7zz real ---

    @Test
    void shouldExtractRegularFiles_whenArchiveIsRar5() throws IOException {
        assumeSevenZip();
        Path rar = fixture("rar5-stored.rar");
        RarArchiveExtractor extractor = new RarArchiveExtractor("7zz");

        List<String> names = extractor.withExtractedFiles(rar, BIG,
                files -> files.stream().map(RarArchiveExtractor.ExtractedFile::name).toList());

        assertThat(names).containsExactly("helloworld.txt");
    }

    @Test
    void shouldIgnoreSymlink_whenArchiveIsRar4() throws IOException {
        assumeSevenZip();
        Path rar = fixture("rar4-with-symlink.rar");
        RarArchiveExtractor extractor = new RarArchiveExtractor("7zz");

        List<String> names = extractor.withExtractedFiles(rar, BIG,
                files -> files.stream().map(RarArchiveExtractor.ExtractedFile::name).toList());

        assertThat(names).contains("test.txt", "testdir/test.txt").doesNotContain("testlink");
    }

    @Test
    void shouldDeleteTempDirectory_whenExtractionFinishes() throws IOException {
        assumeSevenZip();
        Path rar = fixture("rar5-stored.rar");
        RarArchiveExtractor extractor = new RarArchiveExtractor("7zz");
        List<Path> paths = new ArrayList<>();

        extractor.withExtractedFiles(rar, BIG, files -> {
            files.forEach(f -> paths.add(f.path()));
            return null;
        });

        assertThat(paths).isNotEmpty().allSatisfy(p -> assertThat(p).doesNotExist());
        assertThat(paths.get(0).getParent()).doesNotExist();
    }

    @Test
    void shouldDeleteTempDirectory_whenActionFails() throws IOException {
        assumeSevenZip();
        Path rar = fixture("rar5-stored.rar");
        RarArchiveExtractor extractor = new RarArchiveExtractor("7zz");
        List<Path> dirs = new ArrayList<>();

        assertThatThrownBy(() -> extractor.withExtractedFiles(rar, BIG, files -> {
            dirs.add(files.get(0).path().getParent());
            throw new IllegalStateException("falha");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(dirs.get(0)).doesNotExist();
    }

    @Test
    void shouldExtractPagesInOrder_whenCbrIsRenamedZip() throws IOException {
        assumeSevenZip();
        Path cbr = zipRenamedToCbr("p10.png", "p2.png", "__MACOSX/p1.png", "notes.txt");
        List<ExtractedPage> pages = new ArrayList<>();

        new RarArchiveExtractor("7zz").extractCbr(cbr, BIG, pages::add);

        assertThat(pages).extracting(ExtractedPage::position).containsExactly(1, 2);
        assertThat(pages).allSatisfy(p -> {
            assertThat(p.contentType()).isEqualTo("image/png");
            assertThat(p.width()).isEqualTo(10);
            assertThat(p.height()).isEqualTo(20);
        });
    }

    @Test
    void shouldRejectArchive_whenPageCountExceedsLimit() throws IOException {
        assumeSevenZip();
        Path cbr = zipRenamedToCbr("1.png", "2.png", "3.png");

        assertThatThrownBy(() -> new RarArchiveExtractor("7zz")
                .extractCbr(cbr, new ArchiveLimits(2, 100_000_000L, 50_000_000L), p -> { }))
                .isInstanceOf(InvalidArchiveException.class)
                .hasMessageContaining("mais de 2 páginas");
    }

    @Test
    void shouldRejectArchive_whenDeclaredTotalExceedsLimit() throws IOException {
        assumeSevenZip();
        Path cbr = zipRenamedToCbr("1.png", "2.png");

        assertThatThrownBy(() -> new RarArchiveExtractor("7zz")
                .extractCbr(cbr, new ArchiveLimits(10, 50L, 50_000_000L), p -> { }))
                .isInstanceOf(InvalidArchiveException.class)
                .hasMessage("O arquivo é grande demais depois de descompactado");
    }

    @Test
    void shouldRejectArchive_whenFileIsNeitherRarNorZip() throws IOException {
        assumeSevenZip();
        Path garbage = tmp.resolve("garbage.cbr");
        Files.write(garbage, "isto não é um arquivo compactado".getBytes());

        assertThatThrownBy(() -> new RarArchiveExtractor("7zz").extractCbr(garbage, BIG, p -> { }))
                .isInstanceOf(InvalidArchiveException.class)
                .hasMessage("O arquivo não é um CBR válido");
    }

    // --- Sem o 7zz: lógica que não depende do binário ---

    @Test
    void shouldListOnlyRegularFilesInsideDirectory_whenDirectoryHasSymlinks() throws IOException {
        assumeUnix();
        Path dir = Files.createDirectory(tmp.resolve("extracted"));
        Files.createDirectories(dir.resolve("sub"));
        Files.writeString(dir.resolve("sub/a.png"), "a");
        Path outside = Files.writeString(tmp.resolve("segredo.txt"), "segredo");
        try {
            Files.createSymbolicLink(dir.resolve("link-file"), outside);
            Files.createSymbolicLink(dir.resolve("link-dir"), tmp);
        } catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "sistema de arquivos sem symlink");
        }

        List<RarArchiveExtractor.ExtractedFile> files = RarArchiveExtractor.listRegularFiles(dir);

        assertThat(files).extracting(RarArchiveExtractor.ExtractedFile::name).containsExactly("sub/a.png");
    }

    @Test
    void shouldParseSizesAndEncryption_whenListingHasSeveralEntries() {
        String output = """
                7-Zip (z) 24.08

                Listing archive: x.rar

                --
                Path = x.rar
                Type = Rar5
                Size = 999

                ----------
                Path = a/1.jpg
                Folder = -
                Size = 100
                Encrypted = -

                Path = a
                Folder = +
                Size = 0

                Path = 2.jpg
                Folder = -
                Size = 50
                Encrypted = +
                """;

        List<RarArchiveExtractor.ListedEntry> entries = RarArchiveExtractor.parseListing(output);

        assertThat(entries).containsExactly(
                new RarArchiveExtractor.ListedEntry("a/1.jpg", 100, false),
                new RarArchiveExtractor.ListedEntry("2.jpg", 50, true));
    }

    @Test
    void shouldRefuseAndCleanUp_whenExtractedBytesExceedLimit() throws IOException {
        assumeUnix();
        Path script = fakeSevenZip("""
                #!/bin/sh
                if [ "$1" = "l" ]; then
                  printf -- '----------\\nPath = a.png\\nFolder = -\\nSize = 10\\n\\n'
                  exit 0
                fi
                out=""
                for arg in "$@"; do case "$arg" in -o*) out="${arg#-o}";; esac; done
                head -c 5000 /dev/zero > "$out/a.png"
                sleep 30
                """);
        Path archive = Files.writeString(tmp.resolve("x.cbr"), "x");

        assertThatThrownBy(() -> new RarArchiveExtractor(script.toString())
                .extractCbr(archive, new ArchiveLimits(10, 1000L, 50_000_000L), p -> { }))
                .isInstanceOf(InvalidArchiveException.class)
                .hasMessage("O arquivo é grande demais depois de descompactado");
    }

    @Test
    void shouldRefuse_whenSevenZipExitsWithError() throws IOException {
        assumeUnix();
        Path script = fakeSevenZip("""
                #!/bin/sh
                if [ "$1" = "l" ]; then
                  printf -- '----------\\nPath = a.png\\nFolder = -\\nSize = 10\\n\\n'
                  exit 0
                fi
                exit 2
                """);
        Path archive = Files.writeString(tmp.resolve("x.cbr"), "x");

        assertThatThrownBy(() -> new RarArchiveExtractor(script.toString()).extractCbr(archive, BIG, p -> { }))
                .isInstanceOf(InvalidArchiveException.class)
                .hasMessage("O arquivo não é um CBR válido");
    }

    @Test
    void shouldRefuse_whenListingShowsEncryptedEntries() throws IOException {
        assumeUnix();
        Path script = fakeSevenZip("""
                #!/bin/sh
                printf -- '----------\\nPath = a.png\\nFolder = -\\nSize = 10\\nEncrypted = +\\n\\n'
                """);
        Path archive = Files.writeString(tmp.resolve("x.cbr"), "x");

        assertThatThrownBy(() -> new RarArchiveExtractor(script.toString()).extractCbr(archive, BIG, p -> { }))
                .isInstanceOf(InvalidArchiveException.class)
                .hasMessage("O CBR está protegido por senha");
    }

    private Path fakeSevenZip(String content) throws IOException {
        Path script = Files.createTempFile(tmp, "fake7zz", ".sh");
        Files.writeString(script, content);
        script.toFile().setExecutable(true);
        return script;
    }
}
