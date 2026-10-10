package com.buruna.shared.media;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Optional;

/**
 * Identifica o formato de uma imagem pelos magic bytes, lê suas dimensões sem decodificá-la
 * e converte para JPEG os formatos que o leitor não serve (GIF, BMP, TIFF).
 */
public final class ImageInspector {

    public enum ImageType {
        JPEG("image/jpeg", "jpg"),
        PNG("image/png", "png"),
        WEBP("image/webp", "webp"),
        GIF("image/gif", "gif"),
        BMP("image/bmp", "bmp"),
        TIFF("image/tiff", "tiff");

        private final String contentType;
        private final String extension;

        ImageType(String contentType, String extension) {
            this.contentType = contentType;
            this.extension = extension;
        }

        public String contentType() {
            return contentType;
        }

        public String extension() {
            return extension;
        }
    }

    public record InspectedImage(byte[] content, String contentType, String extension, int width, int height) {
    }

    private static final float JPEG_QUALITY = 0.85f;
    // Teto de pixels decodificados na conversão: protege a memória contra imagens "bomba".
    private static final long MAX_CONVERSION_PIXELS = 100_000_000L;
    private static final int WEBP_HEADER_MIN_SIZE = 30;

    private ImageInspector() {
    }

    public static Optional<ImageType> detectType(byte[] c) {
        if (c == null) {
            return Optional.empty();
        }
        if (startsWith(c, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(ImageType.JPEG);
        }
        if (startsWith(c, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(ImageType.PNG);
        }
        if (c.length >= 12 && startsWith(c, 'R', 'I', 'F', 'F') && matchesAt(c, 8, 'W', 'E', 'B', 'P')) {
            return Optional.of(ImageType.WEBP);
        }
        if (startsWith(c, 'G', 'I', 'F', '8') && c.length >= 6 && (c[4] == '7' || c[4] == '9') && c[5] == 'a') {
            return Optional.of(ImageType.GIF);
        }
        if (startsWith(c, 'B', 'M')) {
            return Optional.of(ImageType.BMP);
        }
        if (startsWith(c, 'I', 'I', 0x2A, 0x00) || startsWith(c, 'M', 'M', 0x00, 0x2A)) {
            return Optional.of(ImageType.TIFF);
        }
        return Optional.empty();
    }

    public static InspectedImage inspect(byte[] content, String entryName) {
        ImageType type = detectType(content).orElseThrow(() -> notAnImage(entryName, null));
        try {
            int[] size = type == ImageType.WEBP ? readWebpSize(content) : readSizeWithImageIO(content);
            if (size[0] < 1 || size[1] < 1) {
                throw notAnImage(entryName, null);
            }
            return switch (type) {
                case JPEG, PNG, WEBP ->
                        new InspectedImage(content, type.contentType(), type.extension(), size[0], size[1]);
                case GIF, BMP, TIFF -> convertToJpeg(content, size[0], size[1], entryName);
            };
        } catch (InvalidArchiveException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw notAnImage(entryName, e);
        }
    }

    private static int[] readSizeWithImageIO(byte[] content) throws IOException {
        try (ImageInputStream in = new MemoryCacheImageInputStream(new ByteArrayInputStream(content))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new IOException("Nenhum leitor para a imagem");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                return new int[]{reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        }
    }

    private static int[] readWebpSize(byte[] c) {
        if (c.length < WEBP_HEADER_MIN_SIZE) {
            throw new InvalidArchiveException("Imagem WebP truncada");
        }
        String chunk = new String(c, 12, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
        switch (chunk) {
            case "VP8 " -> {
                if (u8(c, 23) != 0x9D || u8(c, 24) != 0x01 || u8(c, 25) != 0x2A) {
                    throw new InvalidArchiveException("Imagem WebP inválida");
                }
                return new int[]{(u8(c, 26) | u8(c, 27) << 8) & 0x3FFF, (u8(c, 28) | u8(c, 29) << 8) & 0x3FFF};
            }
            case "VP8L" -> {
                if (u8(c, 20) != 0x2F) {
                    throw new InvalidArchiveException("Imagem WebP inválida");
                }
                int bits = u8(c, 21) | u8(c, 22) << 8 | u8(c, 23) << 16 | u8(c, 24) << 24;
                return new int[]{(bits & 0x3FFF) + 1, ((bits >>> 14) & 0x3FFF) + 1};
            }
            case "VP8X" -> {
                int w = (u8(c, 24) | u8(c, 25) << 8 | u8(c, 26) << 16) + 1;
                int h = (u8(c, 27) | u8(c, 28) << 8 | u8(c, 29) << 16) + 1;
                return new int[]{w, h};
            }
            default -> throw new InvalidArchiveException("Imagem WebP inválida");
        }
    }

    private static InspectedImage convertToJpeg(byte[] content, int width, int height, String entryName)
            throws IOException {
        if ((long) width * height > MAX_CONVERSION_PIXELS) {
            throw new InvalidArchiveException("A imagem " + entryName + " é grande demais");
        }
        // ImageIO.read devolve só o primeiro quadro de um GIF animado.
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(content));
        if (source == null) {
            throw notAnImage(entryName, null);
        }
        // JPEG não tem canal alfa: desenha sobre branco para a transparência não virar preto.
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            g.drawImage(source, 0, 0, null);
        } finally {
            g.dispose();
        }
        return new InspectedImage(encodeJpeg(rgb), ImageType.JPEG.contentType(),
                ImageType.JPEG.extension(), rgb.getWidth(), rgb.getHeight());
    }

    /** Codifica uma imagem RGB (sem alfa) em JPEG qualidade 0,85. */
    static byte[] encodeJpeg(BufferedImage rgb) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(rgb, null, null), param);
            }
            return out.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    private static InvalidArchiveException notAnImage(String entryName, Throwable cause) {
        String message = "O arquivo " + entryName + " não é uma imagem válida";
        return cause == null ? new InvalidArchiveException(message) : new InvalidArchiveException(message, cause);
    }

    private static int u8(byte[] c, int i) {
        return c[i] & 0xFF;
    }

    private static boolean startsWith(byte[] c, int... prefix) {
        return matchesAt(c, 0, prefix);
    }

    private static boolean matchesAt(byte[] c, int offset, int... expected) {
        if (c.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((c[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
