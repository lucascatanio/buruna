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

    private static boolean bsdtarInstalled() {
        try {
            Process process = new ProcessBuilder("bsdtar", "--version").redirectErrorStream(true)
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

    private static void assumeBsdtar() {
        assumeTrue(bsdtarInstalled(), "bsdtar não está no PATH");
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

    // --- Com o bsdtar real ---

    @Test
    void shouldExtractRegularFiles_whenArchiveIsRar5() throws IOException {
        assumeBsdtar();
        Path rar = fixture("rar5-stored.rar");
        RarArchiveExtractor extractor = new RarArchiveExtractor("bsdtar");

        List<String> names = extractor.withExtractedFiles(rar, BIG,
                files -> files.stream().map(RarArchiveExtractor.ExtractedFile::name).toList());

        assertThat(names).containsExactly("helloworld.txt");
    }

    @Test
    void shouldIgnoreSymlink_whenArchiveIsRar4() throws IOException {
        assumeBsdtar();
        Path rar = fixture("rar4-with-symlink.rar");
        RarArchiveExtractor extractor = new RarArchiveExtractor("bsdtar");

        List<String> names = extractor.withExtractedFiles(rar, BIG,
                files -> files.stream().map(RarArchiveExtractor.ExtractedFile::name).toList());

        assertThat(names).containsExactlyInAnyOrder("test.txt", "testdir/test.txt");
    }

    @Test
    void shouldDeleteTempDirectory_whenExtractionFinishes() throws IOException {
        assumeBsdtar();
        Path rar = fixture("rar5-stored.rar");
        RarArchiveExtractor extractor = new RarArchiveExtractor("bsdtar");
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
        assumeBsdtar();
        Path rar = fixture("rar5-stored.rar");
        RarArchiveExtractor extractor = new RarArchiveExtractor("bsdtar");
        List<Path> dirs = new ArrayList<>();

        assertThatThrownBy(() -> extractor.withExtractedFiles(rar, BIG, files -> {
            dirs.add(files.get(0).path().getParent());
            throw new IllegalStateException("falha");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(dirs.get(0)).doesNotExist();
    }

    @Test
    void shouldExtractPagesInOrder_whenCbrIsRenamedZip() throws IOException {
        assumeBsdtar();
        Path cbr = zipRenamedToCbr("p10.png", "p2.png", "__MACOSX/p1.png", "notes.txt");
        List<ExtractedPage> pages = new ArrayList<>();

        new RarArchiveExtractor("bsdtar").extractCbr(cbr, BIG, pages::add);

        assertThat(pages).extracting(ExtractedPage::position).containsExactly(1, 2);
        assertThat(pages).allSatisfy(p -> {
            assertThat(p.contentType()).isEqualTo("image/png");
            assertThat(p.width()).isEqualTo(10);
            assertThat(p.height()).isEqualTo(20);
        });
    }

    @Test
    void shouldRejectArchive_whenPageCountExceedsLimit() throws IOException {
        assumeBsdtar();
        Path cbr = zipRenamedToCbr("1.png", "2.png", "3.png");

        assertThatThrownBy(() -> new RarArchiveExtractor("bsdtar")
                .extractCbr(cbr, new ArchiveLimits(2, 100_000_000L, 50_000_000L), p -> { }))
                .isInstanceOf(InvalidArchiveException.class)
                .hasMessageContaining("mais de 2 páginas");
    }

    @Test
    void shouldRejectArchive_whenDeclaredTotalExceedsLimit() throws IOException {
        assumeBsdtar();
        Path cbr = zipRenamedToCbr("1.png", "2.png");

        assertThatThrownBy(() -> new RarArchiveExtractor("bsdtar")
                .extractCbr(cbr, new ArchiveLimits(10, 50L, 50_000_000L), p -> { }))
                .isInstanceOf(InvalidArchiveException.class)
                .hasMessage("O arquivo é grande demais depois de descompactado");
    }

    @Test
    void shouldRejectArchive_whenFileIsNeitherRarNorZip() throws IOException {
        assumeBsdtar();
        Path garbage = tmp.resolve("garbage.cbr");
        Files.write(garbage, "isto não é um arquivo compactado".getBytes());

        assertThatThrownBy(() -> new RarArchiveExtractor("bsdtar").extractCbr(garbage, BIG, p -> { }))
                .isInstanceOf(InvalidArchiveException.class)
                .hasMessage("O arquivo não é um CBR válido");
    }

    // --- Sem o bsdtar: lógica que não depende do binário ---

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
    void shouldParseRegularFilesOnly_whenListingHasLinksAndDirectories() {
        String output = """
                -rw-r--r--  0 0      0          20 Jun 26  2011 test.txt
                lrwxrwxrwx  0 0      0           0 Jun 24  2011 testlink -> test.txt
                -rw-r--r--  0 0      0          20 Jun 26  2011 testdir/test.txt
                drwxr-xr-x  0 0      0           0 Jun 26  2011 testdir
                linha que nao casa
                """;

        List<RarArchiveExtractor.ListedEntry> entries = RarArchiveExtractor.parseListing(output);

        assertThat(entries).containsExactly(
                new RarArchiveExtractor.ListedEntry("test.txt", 20),
                new RarArchiveExtractor.ListedEntry("testdir/test.txt", 20));
    }

    @Test
    void shouldKeepFullName_whenFileNameHasSpaces() {
        String output = "-rw-r--r--  0 0      0        1234 Jun 26  2011 meu arquivo 01.png\n"
                + "-rw-r--r--  0 0      0          99 Oct 10 14:32 outro arquivo.jpg\n";

        List<RarArchiveExtractor.ListedEntry> entries = RarArchiveExtractor.parseListing(output);

        assertThat(entries).containsExactly(
                new RarArchiveExtractor.ListedEntry("meu arquivo 01.png", 1234),
                new RarArchiveExtractor.ListedEntry("outro arquivo.jpg", 99));
    }

    @Test
    void shouldRefuseAndCleanUp_whenExtractedBytesExceedLimit() throws IOException {
        assumeUnix();
        Path script = fakeBsdtar("""
                #!/bin/sh
                if [ "$1" = "-tvf" ]; then
                  echo '-rw-r--r--  0 0      0          10 Jun 26  2011 a.png'
                  exit 0
                fi
                out=""
                while [ $# -gt 0 ]; do if [ "$1" = "-C" ]; then out="$2"; fi; shift; done
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
    void shouldRefuse_whenBsdtarExitsWithError() throws IOException {
        assumeUnix();
        Path script = fakeBsdtar("""
                #!/bin/sh
                if [ "$1" = "-tvf" ]; then
                  echo '-rw-r--r--  0 0      0          10 Jun 26  2011 a.png'
                  exit 0
                fi
                echo 'bsdtar: Damaged archive' >&2
                exit 1
                """);
        Path archive = Files.writeString(tmp.resolve("x.cbr"), "x");

        assertThatThrownBy(() -> new RarArchiveExtractor(script.toString()).extractCbr(archive, BIG, p -> { }))
                .isInstanceOf(InvalidArchiveException.class)
                .hasMessage("O arquivo não é um CBR válido");
    }

    @Test
    void shouldRefuseAsPasswordProtected_whenListingFailsWithEncryptedMessage() throws IOException {
        assumeUnix();
        Path script = fakeBsdtar("""
                #!/bin/sh
                echo 'bsdtar: Encrypted file is unsupported' >&2
                exit 1
                """);
        Path archive = Files.writeString(tmp.resolve("x.cbr"), "x");

        assertThatThrownBy(() -> new RarArchiveExtractor(script.toString()).extractCbr(archive, BIG, p -> { }))
                .isInstanceOf(InvalidArchiveException.class)
                .hasMessage("O CBR está protegido por senha");
    }

    @Test
    void shouldRefuseAsPasswordProtected_whenExtractionFailsWithEncryptedMessage() throws IOException {
        assumeUnix();
        Path script = fakeBsdtar("""
                #!/bin/sh
                if [ "$1" = "-tvf" ]; then
                  echo '-rw-r--r--  0 0      0          10 Jun 26  2011 a.png'
                  exit 0
                fi
                echo 'bsdtar: Encrypted file is unsupported' >&2
                exit 1
                """);
        Path archive = Files.writeString(tmp.resolve("x.cbr"), "x");

        assertThatThrownBy(() -> new RarArchiveExtractor(script.toString()).extractCbr(archive, BIG, p -> { }))
                .isInstanceOf(InvalidArchiveException.class)
                .hasMessage("O CBR está protegido por senha");
    }

    private Path fakeBsdtar(String content) throws IOException {
        Path script = Files.createTempFile(tmp, "fakebsdtar", ".sh");
        Files.writeString(script, content);
        script.toFile().setExecutable(true);
        return script;
    }
}
