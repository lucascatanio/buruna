package com.buruna.shared.media;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Extrai as páginas de um PDF. Páginas que são só um JPEG escaneado saem com os bytes originais;
 * qualquer outra coisa (texto, vetor, várias imagens, Flate, JBIG2, CCITT) é renderizada em JPEG.
 */
@Component
public class PdfPageExtractor {

    private static final int TARGET_WIDTH_PX = 1400;
    private static final float MAX_DPI = 300f;
    private static final float POINTS_PER_INCH = 72f;
    // Protege a memória contra páginas gigantes: 1400 px de largura por 28.000 px de altura.
    private static final long MAX_RENDER_PIXELS = 40_000_000L;
    // Tolerância entre a proporção da imagem e a da página para considerá-la a "página inteira".
    private static final double ASPECT_TOLERANCE = 0.05;

    public void extractPdf(Path pdfFile, ArchiveLimits limits, Consumer<ExtractedPage> sink) {
        try (PDDocument document = Loader.loadPDF(pdfFile.toFile(), "", null, null,
                IOUtils.createTempFileOnlyStreamCache())) {
            int pageCount = document.getNumberOfPages();
            if (pageCount == 0) {
                throw new InvalidArchiveException("O arquivo não tem nenhuma página");
            }
            ArchivePages.checkPageCount(pageCount, limits);

            PDFRenderer renderer = new PDFRenderer(document);
            ArchivePages.Emitter emitter = new ArchivePages.Emitter(limits, sink);
            for (int index = 0; index < pageCount; index++) {
                emitter.emit("página-" + (index + 1), pageBytes(document, renderer, index, limits));
            }
        } catch (InvalidPasswordException e) {
            throw new InvalidArchiveException("O PDF está protegido por senha", e);
        } catch (IOException e) {
            throw new InvalidArchiveException("O arquivo não é um PDF válido", e);
        }
    }

    private byte[] pageBytes(PDDocument document, PDFRenderer renderer, int index, ArchiveLimits limits)
            throws IOException {
        PDPage page = document.getPage(index);
        List<PDImageXObject> images = imagesOf(page);
        if (images.stream().anyMatch(PdfPageExtractor::usesJpeg2000)) {
            throw new InvalidArchiveException("Este PDF usa imagens em JPEG2000, que ainda não são aceitas");
        }
        if (isScannedJpeg(document, page, index, images)) {
            return readOriginalJpeg(images.get(0), limits.maxPageBytes());
        }
        return render(renderer, page, index);
    }

    private static List<PDImageXObject> imagesOf(PDPage page) throws IOException {
        List<PDImageXObject> images = new ArrayList<>();
        PDResources resources = page.getResources();
        if (resources == null) {
            return images;
        }
        for (COSName name : resources.getXObjectNames()) {
            PDXObject xObject = resources.getXObject(name);
            if (xObject instanceof PDImageXObject image) {
                images.add(image);
            }
        }
        return images;
    }

    private static boolean usesJpeg2000(PDImageXObject image) {
        return image.getStream().getFilters().contains(COSName.JPX_DECODE);
    }

    /**
     * Caso escaneado comum: uma única imagem DCTDecode ocupando a página, sem texto. Máscaras e CMYK
     * ficam de fora porque o JPEG original, sozinho, não reproduz o que o PDF desenha.
     */
    private boolean isScannedJpeg(PDDocument document, PDPage page, int index, List<PDImageXObject> images)
            throws IOException {
        if (images.size() != 1 || countXObjects(page) != 1) {
            return false;
        }
        PDImageXObject image = images.get(0);
        List<COSName> filters = image.getStream().getFilters();
        if (filters.size() != 1 || !filters.get(0).equals(COSName.DCT_DECODE)) {
            return false;
        }
        if (image.isStencil() || image.getSoftMask() != null || image.getMask() != null
                || image.getColorSpace().getNumberOfComponents() == 4 || image.getDecode() != null) {
            return false;
        }
        return coversPage(image, page) && !hasText(document, index);
    }

    private static int countXObjects(PDPage page) {
        int count = 0;
        for (COSName ignored : page.getResources().getXObjectNames()) {
            count++;
        }
        return count;
    }

    private static boolean coversPage(PDImageXObject image, PDPage page) {
        float[] size = visibleSize(page);
        double pageRatio = size[0] / size[1];
        double imageRatio = (double) image.getWidth() / image.getHeight();
        return Math.abs(pageRatio - imageRatio) / pageRatio <= ASPECT_TOLERANCE;
    }

    private static boolean hasText(PDDocument document, int index) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(index + 1);
        stripper.setEndPage(index + 1);
        return !stripper.getText(document).isBlank();
    }

    // Bytes ainda codificados: para a leitura no DCTDecode, sem decodificar o JPEG.
    private static byte[] readOriginalJpeg(PDImageXObject image, long maxPageBytes) throws IOException {
        try (InputStream in = image.getStream().createInputStream(List.of(COSName.DCT_DECODE.getName()))) {
            return in.readNBytes((int) Math.min(maxPageBytes + 1, Integer.MAX_VALUE - 8));
        }
    }

    private byte[] render(PDFRenderer renderer, PDPage page, int index) throws IOException {
        float[] size = visibleSize(page);
        float dpi = Math.min(MAX_DPI, TARGET_WIDTH_PX * POINTS_PER_INCH / size[0]);
        double scale = dpi / POINTS_PER_INCH;
        if ((size[0] * scale) * (size[1] * scale) > MAX_RENDER_PIXELS) {
            throw new InvalidArchiveException("A página " + (index + 1) + " é grande demais");
        }
        try {
            BufferedImage image = renderer.renderImageWithDPI(index, dpi, ImageType.RGB);
            return ImageInspector.encodeJpeg(image);
        } catch (IOException | RuntimeException e) {
            // PDF malformado costuma estourar em exceções de runtime dentro do renderizador.
            throw new InvalidArchiveException("A página " + (index + 1) + " não pôde ser processada", e);
        }
    }

    /** Largura e altura em pontos como o leitor mostra: área visível, com a rotação aplicada. */
    private static float[] visibleSize(PDPage page) {
        PDRectangle box = page.getCropBox();
        boolean sideways = page.getRotation() % 180 != 0;
        return sideways ? new float[]{box.getHeight(), box.getWidth()} : new float[]{box.getWidth(), box.getHeight()};
    }
}
