package com.buruna.shared.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComicArchiveExtractorTest {

    private static final ArchiveLimits BIG = new ArchiveLimits(1000, 100_000_000L, 50_000_000L);

    @TempDir
    Path tmp;

    private final ComicArchiveExtractor extractor = new ComicArchiveExtractor();

    private static byte[] png(int w, int h) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    private Path zip(Map<String, byte[]> entries) throws IOException {
        Path file = tmp.resolve("test-" + System.nanoTime() + ".cbz");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(file))) {
            for (var e : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                if (!e.getKey().endsWith("/")) {
                    zos.write(e.getValue());
                }
                zos.closeEntry();
            }
        }
        return file;
    }

    private List<ExtractedPage> extract(Path file, ArchiveLimits limits) {
        List<ExtractedPage> pages = new ArrayList<>();
        extractor.extractCbz(file, limits, pages::add);
        return pages;
    }

    @Test
    void shouldExtractInNaturalOrder_whenNamesHaveNumbers() throws IOException {
        // Arrange
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("page10.png", png(10, 1));
        entries.put("page2.png", png(2, 1));
        entries.put("Page1.png", png(1, 1));
        Path file = zip(entries);

        // Act
        List<ExtractedPage> pages = extract(file, BIG);

        // Assert
        assertThat(pages).extracting(ExtractedPage::width).containsExactly(1, 2, 10);
    }

    @Test
    void shouldOrderByFullPath_whenEntriesAreInSubfolders() throws IOException {
        // Arrange
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("cap2/001.png", png(3, 1));
        entries.put("cap1/010.png", png(2, 1));
        entries.put("cap1/009.png", png(1, 1));
        Path file = zip(entries);

        // Act
        List<ExtractedPage> pages = extract(file, BIG);

        // Assert
        assertThat(pages).extracting(ExtractedPage::width).containsExactly(1, 2, 3);
    }

    @Test
    void shouldStartPositionsAtOneAndBeContiguous_whenExtracting() throws IOException {
        // Arrange
        Map<String, byte[]> entries = new LinkedHashMap<>();
        for (int i = 1; i <= 4; i++) {
            entries.put("p" + i + ".png", png(i, 2));
        }
        Path file = zip(entries);

        // Act
        List<ExtractedPage> pages = extract(file, BIG);

        // Assert
        assertThat(pages).extracting(ExtractedPage::position).containsExactly(1, 2, 3, 4);
        assertThat(pages.get(0).contentType()).isEqualTo("image/png");
        assertThat(pages.get(0).extension()).isEqualTo("png");
        assertThat(pages.get(3).height()).isEqualTo(2);
    }

    @Test
    void shouldIgnoreJunkAndNonImageEntries_whenExtracting() throws IOException {
        // Arrange
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("__MACOSX/._001.png", new byte[]{1, 2, 3});
        entries.put(".DS_Store", new byte[]{1, 2, 3});
        entries.put("sub/._002.png", new byte[]{1, 2, 3});
        entries.put("Thumbs.db", new byte[]{1, 2, 3});
        entries.put("desktop.ini", new byte[]{1, 2, 3});
        entries.put("ComicInfo.xml", "<ComicInfo/>".getBytes());
        entries.put("pasta/", new byte[0]);
        entries.put("001.png", png(5, 5));
        Path file = zip(entries);

        // Act
        List<ExtractedPage> pages = extract(file, BIG);

        // Assert
        assertThat(pages).hasSize(1);
        assertThat(pages.get(0).width()).isEqualTo(5);
    }

    @Test
    void shouldThrow_whenEntryIsNotAnImage() throws IOException {
        // Arrange
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("001.png", png(5, 5));
        entries.put("notes.bin", new byte[]{9, 9, 9, 9, 9, 9, 9, 9});
        Path file = zip(entries);

        // Act & Assert
        assertThatThrownBy(() -> extract(file, BIG))
                .isInstanceOf(InvalidArchiveException.class)
                .hasMessageContaining("A página 2")
                .hasMessageContaining("notes.bin");
    }

    @Test
    void shouldThrow_whenFileIsNotAZip() throws IOException {
        // Arrange
        Path file = tmp.resolve("random.cbz");
        Files.write(file, new java.util.Random(42).ints(500, 0, 256)
                .collect(ByteArrayOutputStream::new, ByteArrayOutputStream::write, (a, b) -> { })
                .toByteArray());

        // Act & Assert
        assertThatThrownBy(() -> extract(file, BIG))
                .isInstanceOf(InvalidArchiveException.class)
                .hasMessage("O arquivo não é um CBZ válido");
    }

    @Test
    void shouldThrowBeforeReadingContent_whenPageCountExceedsLimit() throws IOException {
        // Arrange
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("1.png", png(1, 1));
        entries.put("2.png", png(1, 1));
        entries.put("3.png", png(1, 1));
        Path file = zip(entries);
        List<ExtractedPage> received = new ArrayList<>();

        // Act & Assert
        assertThatThrownBy(() -> extractor.extractCbz(file, new ArchiveLimits(2, 1_000_000, 1_000_000), received::add))
                .isInstanceOf(InvalidArchiveException.class)
                .hasMessage("O arquivo tem mais de 2 páginas");
        assertThat(received).isEmpty();
    }

    @Test
    void shouldThrow_whenPageExceedsMaxPageBytesEvenWithFakeDeclaredSize() throws IOException {
        // Arrange
        byte[] big = new byte[5000];
        Path file = tmp.resolve("stored.cbz");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(file))) {
            ZipEntry entry = new ZipEntry("001.bin");
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(big.length);
            entry.setCompressedSize(big.length);
            CRC32 crc = new CRC32();
            crc.update(big);
            entry.setCrc(crc.getValue());
            zos.putNextEntry(entry);
            zos.write(big);
            zos.closeEntry();
        }

        // Act & Assert
        assertThatThrownBy(() -> extract(file, new ArchiveLimits(10, 1_000_000, 100)))
                .isInstanceOf(InvalidArchiveException.class)
                .hasMessageContaining("A página 1")
                .hasMessageContaining("grande demais");
    }

    @Test
    void shouldThrow_whenTotalBytesExceedLimit() throws IOException {
        // Arrange
        byte[] page = png(50, 50);
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("1.png", page);
        entries.put("2.png", page);
        entries.put("3.png", page);
        Path file = zip(entries);
        ArchiveLimits limits = new ArchiveLimits(10, page.length * 2L + 1, page.length + 10L);

        // Act & Assert
        assertThatThrownBy(() -> extract(file, limits))
                .isInstanceOf(InvalidArchiveException.class)
                .hasMessageContaining("grande demais");
    }

    @Test
    void shouldThrow_whenArchiveHasNoPages() throws IOException {
        // Arrange
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("ComicInfo.xml", "<ComicInfo/>".getBytes());
        entries.put("__MACOSX/x.png", new byte[]{1});
        Path file = zip(entries);

        // Act & Assert
        assertThatThrownBy(() -> extract(file, BIG))
                .isInstanceOf(InvalidArchiveException.class)
                .hasMessage("O arquivo não tem nenhuma página");
    }

    @Test
    void shouldConvertGifPage_whenArchiveContainsGif() throws IOException {
        // Arrange
        ByteArrayOutputStream gif = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(9, 6, BufferedImage.TYPE_INT_RGB), "gif", gif);
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("001.gif", gif.toByteArray());
        Path file = zip(entries);

        // Act
        List<ExtractedPage> pages = extract(file, BIG);

        // Assert
        assertThat(pages.get(0).contentType()).isEqualTo("image/jpeg");
        assertThat(pages.get(0).width()).isEqualTo(9);
        assertThat(pages.get(0).height()).isEqualTo(6);
    }

    @Test
    void shouldCompareNaturally_whenNumbersHaveLeadingZeros() {
        // Arrange & Act
        int cmp = ComicArchiveExtractor.naturalCompare("p007.png", "p10.png");

        // Assert
        assertThat(cmp).isNegative();
    }
}
