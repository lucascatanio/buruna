package com.buruna.shared.media;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfPageExtractorTest {

    private static final ArchiveLimits BIG = new ArchiveLimits(1000, 100_000_000L, 50_000_000L);

    @TempDir
    Path tmp;

    private final PdfPageExtractor extractor = new PdfPageExtractor();

    private static BufferedImage gradient(int w, int h) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.BLUE);
        g.fillOval(w / 4, h / 4, w / 2, h / 2);
        g.dispose();
        return image;
    }

    private static byte[] jpegBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", out);
        return out.toByteArray();
    }

    private Path save(PDDocument document) throws IOException {
        Path file = tmp.resolve("doc-" + System.nanoTime() + ".pdf");
        document.save(file.toFile());
        document.close();
        return file;
    }

    private static void drawFullPage(PDDocument document, PDPage page, PDImageXObject image) throws IOException {
        try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
            PDRectangle box = page.getMediaBox();
            cs.drawImage(image, 0, 0, box.getWidth(), box.getHeight());
        }
    }

    private List<ExtractedPage> extract(Path file) {
        List<ExtractedPage> pages = new ArrayList<>();
        extractor.extractPdf(file, BIG, pages::add);
        return pages;
    }

    @Test
    void shouldKeepOriginalJpegBytes_whenPageIsSingleScannedJpeg() throws IOException {
        byte[] jpeg = jpegBytes(gradient(400, 600));
        PDDocument document = new PDDocument();
        PDPage page = new PDPage(new PDRectangle(200, 300));
        document.addPage(page);
        drawFullPage(document, page, JPEGFactory.createFromByteArray(document, jpeg));
        Path file = save(document);

        List<ExtractedPage> pages = extract(file);

        assertThat(pages).hasSize(1);
        assertThat(pages.get(0).content()).isEqualTo(jpeg);
        assertThat(pages.get(0).contentType()).isEqualTo("image/jpeg");
        assertThat(pages.get(0).width()).isEqualTo(400);
        assertThat(pages.get(0).height()).isEqualTo(600);
    }

    @Test
    void shouldRenderPageToJpeg_whenPageHasText() throws IOException {
        PDDocument document = new PDDocument();
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
            cs.beginText();
            cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 24);
            cs.newLineAtOffset(72, 700);
            cs.showText("Capitulo 1");
            cs.endText();
        }
        Path file = save(document);

        List<ExtractedPage> pages = extract(file);

        assertThat(pages).hasSize(1);
        assertThat(pages.get(0).contentType()).isEqualTo("image/jpeg");
        assertThat(pages.get(0).width()).isBetween(1395, 1405);
        assertThat(pages.get(0).height()).isGreaterThan(pages.get(0).width());
    }

    @Test
    void shouldRenderPageToJpeg_whenImageUsesFlate() throws IOException {
        PDDocument document = new PDDocument();
        PDPage page = new PDPage(new PDRectangle(400, 500));
        document.addPage(page);
        drawFullPage(document, page, LosslessFactory.createFromImage(document, gradient(300, 375)));
        Path file = save(document);

        List<ExtractedPage> pages = extract(file);

        assertThat(pages).hasSize(1);
        assertThat(pages.get(0).contentType()).isEqualTo("image/jpeg");
        assertThat(pages.get(0).width()).isBetween(1395, 1405);
    }

    @Test
    void shouldKeepPageOrder_whenPdfHasMixedPages() throws IOException {
        PDDocument document = new PDDocument();
        for (int i = 0; i < 3; i++) {
            PDPage page = new PDPage(new PDRectangle(100, 150));
            document.addPage(page);
            drawFullPage(document, page, LosslessFactory.createFromImage(document, gradient(100, 150)));
        }
        Path file = save(document);

        List<ExtractedPage> pages = extract(file);

        assertThat(pages).extracting(ExtractedPage::position).containsExactly(1, 2, 3);
    }

    @Test
    void shouldRejectPdf_whenItNeedsPassword() throws IOException {
        PDDocument document = new PDDocument();
        document.addPage(new PDPage());
        StandardProtectionPolicy policy =
                new StandardProtectionPolicy("dono", "usuario", new AccessPermission());
        policy.setEncryptionKeyLength(128);
        document.protect(policy);
        Path file = save(document);

        assertThatThrownBy(() -> extract(file))
                .isInstanceOf(InvalidArchiveException.class)
                .hasMessage("O PDF está protegido por senha");
    }

    @Test
    void shouldRejectPdf_whenImageUsesJpeg2000() throws IOException {
        PDDocument document = new PDDocument();
        PDPage page = new PDPage(new PDRectangle(200, 300));
        document.addPage(page);
        // Bytes falsos gravados crus: o PDFBox não sabe codificar JPX, e o teste só precisa do /Filter.
        COSStream stream = document.getDocument().createCOSStream();
        try (OutputStream out = stream.createRawOutputStream()) {
            out.write(new byte[]{0, 1, 2, 3});
        }
        stream.setItem(COSName.FILTER, COSName.JPX_DECODE);
        PDImageXObject image = new PDImageXObject(new PDStream(stream), null);
        image.getCOSObject().setInt(COSName.WIDTH, 100);
        image.getCOSObject().setInt(COSName.HEIGHT, 150);
        image.getCOSObject().setItem(COSName.COLORSPACE, COSName.getPDFName("DeviceRGB"));
        image.getCOSObject().setInt(COSName.BITS_PER_COMPONENT, 8);
        drawFullPage(document, page, image);
        Path file = save(document);

        assertThatThrownBy(() -> extract(file))
                .isInstanceOf(InvalidArchiveException.class)
                .hasMessage("Este PDF usa imagens em JPEG2000, que ainda não são aceitas");
    }

    @Test
    void shouldRejectPdf_whenPageCountExceedsLimit() throws IOException {
        PDDocument document = new PDDocument();
        for (int i = 0; i < 3; i++) {
            document.addPage(new PDPage());
        }
        Path file = save(document);

        assertThatThrownBy(() -> extractor.extractPdf(file, new ArchiveLimits(2, 100_000_000L, 50_000_000L), p -> { }))
                .isInstanceOf(InvalidArchiveException.class)
                .hasMessageContaining("mais de 2 páginas");
    }

    @Test
    void shouldRejectPdf_whenRenderedPageExceedsPageBytesLimit() throws IOException {
        PDDocument document = new PDDocument();
        document.addPage(new PDPage(PDRectangle.A4));
        Path file = save(document);

        assertThatThrownBy(() -> extractor.extractPdf(file, new ArchiveLimits(10, 100_000_000L, 100L), p -> { }))
                .isInstanceOf(InvalidArchiveException.class)
                .hasMessageContaining("é grande demais");
    }

    @Test
    void shouldRejectFile_whenBytesAreNotPdf() throws IOException {
        Path file = Files.write(tmp.resolve("fake.pdf"), "isto não é um pdf".getBytes());

        assertThatThrownBy(() -> extract(file))
                .isInstanceOf(InvalidArchiveException.class)
                .hasMessage("O arquivo não é um PDF válido");
    }

    @Test
    void shouldSaveReadablePdf_whenFixtureIsGenerated() throws IOException {
        // Sanidade da fixture: o PDF gerado nos testes é um PDF que o PDFBox reabre.
        PDDocument document = new PDDocument();
        document.addPage(new PDPage());
        Path file = save(document);

        try (PDDocument reopened = Loader.loadPDF(new RandomAccessReadBuffer(Files.readAllBytes(file)))) {
            assertThat(reopened.getNumberOfPages()).isEqualTo(1);
        }
    }
}
