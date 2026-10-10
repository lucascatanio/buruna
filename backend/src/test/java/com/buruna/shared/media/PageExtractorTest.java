package com.buruna.shared.media;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageExtractorTest {

    private static final ArchiveLimits BIG = new ArchiveLimits(1000, 100_000_000L, 50_000_000L);

    @TempDir
    Path tmp;

    private final PageExtractor extractor = new PageExtractor(
            new ComicArchiveExtractor(), new RarArchiveExtractor("7zz"), new PdfPageExtractor());

    @Test
    void shouldExtractCbzPages_whenFormatIsCbz() throws IOException {
        Path cbz = tmp.resolve("a.cbz");
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", png);
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(cbz))) {
            zos.putNextEntry(new ZipEntry("1.png"));
            zos.write(png.toByteArray());
            zos.closeEntry();
        }
        List<ExtractedPage> pages = new ArrayList<>();

        extractor.extract(cbz, ArchiveFormat.CBZ, BIG, pages::add);

        assertThat(pages).hasSize(1);
    }

    @Test
    void shouldExtractPdfPages_whenFormatIsPdf() throws IOException {
        Path pdf = tmp.resolve("a.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(pdf.toFile());
        }
        List<ExtractedPage> pages = new ArrayList<>();

        extractor.extract(pdf, ArchiveFormat.PDF, BIG, pages::add);

        assertThat(pages).hasSize(1);
    }

    @Test
    void shouldRejectCbz_whenFileIsNotZip() throws IOException {
        Path file = Files.writeString(tmp.resolve("a.cbz"), "nada");

        assertThatThrownBy(() -> extractor.extract(file, ArchiveFormat.CBZ, BIG, p -> { }))
                .isInstanceOf(InvalidArchiveException.class);
    }
}
