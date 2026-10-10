package com.buruna.shared.media;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageInspectorTest {

    static byte[] image(String format, int w, int h, int type) throws IOException {
        BufferedImage img = new BufferedImage(w, h, type);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    static byte[] riffWebp(String fourcc, int payloadSize) {
        byte[] b = new byte[20 + payloadSize];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, b, 0, 4);
        System.arraycopy("WEBP".getBytes(StandardCharsets.US_ASCII), 0, b, 8, 4);
        System.arraycopy(fourcc.getBytes(StandardCharsets.US_ASCII), 0, b, 12, 4);
        return b;
    }

    static byte[] webpLossy(int w, int h) {
        byte[] b = riffWebp("VP8 ", 10);
        b[23] = (byte) 0x9D;
        b[24] = 0x01;
        b[25] = 0x2A;
        b[26] = (byte) (w & 0xFF);
        b[27] = (byte) (w >> 8);
        b[28] = (byte) (h & 0xFF);
        b[29] = (byte) (h >> 8);
        return b;
    }

    static byte[] webpLossless(int w, int h) {
        byte[] b = riffWebp("VP8L", 10);
        b[20] = 0x2F;
        int bits = (w - 1) | ((h - 1) << 14);
        b[21] = (byte) bits;
        b[22] = (byte) (bits >> 8);
        b[23] = (byte) (bits >> 16);
        b[24] = (byte) (bits >> 24);
        return b;
    }

    static byte[] webpExtended(int w, int h) {
        byte[] b = riffWebp("VP8X", 10);
        int cw = w - 1;
        int ch = h - 1;
        b[24] = (byte) cw;
        b[25] = (byte) (cw >> 8);
        b[26] = (byte) (cw >> 16);
        b[27] = (byte) ch;
        b[28] = (byte) (ch >> 8);
        b[29] = (byte) (ch >> 16);
        return b;
    }

    @Test
    void shouldDetectEachType_whenMagicBytesMatch() throws IOException {
        // Arrange
        byte[] jpeg = image("jpeg", 4, 4, BufferedImage.TYPE_INT_RGB);
        byte[] png = image("png", 4, 4, BufferedImage.TYPE_INT_RGB);
        byte[] gif = image("gif", 4, 4, BufferedImage.TYPE_INT_RGB);
        byte[] bmp = image("bmp", 4, 4, BufferedImage.TYPE_INT_RGB);
        byte[] tiff = image("tiff", 4, 4, BufferedImage.TYPE_INT_RGB);
        byte[] webp = webpLossy(4, 4);

        // Act & Assert
        assertThat(ImageInspector.detectType(jpeg)).contains(ImageInspector.ImageType.JPEG);
        assertThat(ImageInspector.detectType(png)).contains(ImageInspector.ImageType.PNG);
        assertThat(ImageInspector.detectType(gif)).contains(ImageInspector.ImageType.GIF);
        assertThat(ImageInspector.detectType(bmp)).contains(ImageInspector.ImageType.BMP);
        assertThat(ImageInspector.detectType(tiff)).contains(ImageInspector.ImageType.TIFF);
        assertThat(ImageInspector.detectType(webp)).contains(ImageInspector.ImageType.WEBP);
    }

    @Test
    void shouldReturnEmpty_whenTypeIsUnknown() {
        // Arrange
        byte[] garbage = "isto nao e uma imagem".getBytes(StandardCharsets.UTF_8);

        // Act
        var type = ImageInspector.detectType(garbage);

        // Assert
        assertThat(type).isEmpty();
    }

    @Test
    void shouldTrustMagicBytes_whenExtensionLies() throws IOException {
        // Arrange
        byte[] png = image("png", 5, 7, BufferedImage.TYPE_INT_RGB);

        // Act
        var result = ImageInspector.inspect(png, "x.jpg");

        // Assert
        assertThat(result.contentType()).isEqualTo("image/png");
        assertThat(result.extension()).isEqualTo("png");
    }

    @Test
    void shouldKeepOriginalBytes_whenImageIsJpegOrPng() throws IOException {
        // Arrange
        byte[] jpeg = image("jpeg", 30, 20, BufferedImage.TYPE_INT_RGB);
        byte[] png = image("png", 30, 20, BufferedImage.TYPE_INT_RGB);

        // Act
        var jpegResult = ImageInspector.inspect(jpeg, "a.jpg");
        var pngResult = ImageInspector.inspect(png, "a.png");

        // Assert
        assertThat(jpegResult.content()).isEqualTo(jpeg);
        assertThat(jpegResult.width()).isEqualTo(30);
        assertThat(jpegResult.height()).isEqualTo(20);
        assertThat(jpegResult.contentType()).isEqualTo("image/jpeg");
        assertThat(pngResult.content()).isEqualTo(png);
        assertThat(pngResult.width()).isEqualTo(30);
        assertThat(pngResult.height()).isEqualTo(20);
    }

    @Test
    void shouldReadWebpDimensions_whenLossy() {
        // Arrange
        byte[] webp = webpLossy(800, 1200);

        // Act
        var result = ImageInspector.inspect(webp, "a.webp");

        // Assert
        assertThat(result.width()).isEqualTo(800);
        assertThat(result.height()).isEqualTo(1200);
        assertThat(result.contentType()).isEqualTo("image/webp");
        assertThat(result.content()).isEqualTo(webp);
    }

    @Test
    void shouldReadWebpDimensions_whenLossless() {
        // Arrange
        byte[] webp = webpLossless(1024, 1536);

        // Act
        var result = ImageInspector.inspect(webp, "a.webp");

        // Assert
        assertThat(result.width()).isEqualTo(1024);
        assertThat(result.height()).isEqualTo(1536);
    }

    @Test
    void shouldReadWebpDimensions_whenExtended() {
        // Arrange
        byte[] webp = webpExtended(20000, 300);

        // Act
        var result = ImageInspector.inspect(webp, "a.webp");

        // Assert
        assertThat(result.width()).isEqualTo(20000);
        assertThat(result.height()).isEqualTo(300);
    }

    @Test
    void shouldThrow_whenWebpIsTruncated() {
        // Arrange
        byte[] truncated = java.util.Arrays.copyOf(webpLossy(10, 10), 20);

        // Act & Assert
        assertThatThrownBy(() -> ImageInspector.inspect(truncated, "a.webp"))
                .isInstanceOf(InvalidArchiveException.class);
    }

    @Test
    void shouldConvertToJpegKeepingDimensions_whenImageIsGifBmpOrTiff() throws IOException {
        // Arrange
        byte[] gif = image("gif", 40, 25, BufferedImage.TYPE_INT_RGB);
        byte[] bmp = image("bmp", 33, 17, BufferedImage.TYPE_INT_RGB);
        byte[] tiff = image("tiff", 12, 50, BufferedImage.TYPE_INT_RGB);

        // Act
        var gifResult = ImageInspector.inspect(gif, "a.gif");
        var bmpResult = ImageInspector.inspect(bmp, "a.bmp");
        var tiffResult = ImageInspector.inspect(tiff, "a.tif");

        // Assert
        assertThat(gifResult.contentType()).isEqualTo("image/jpeg");
        assertThat(gifResult.extension()).isEqualTo("jpg");
        assertThat(gifResult.width()).isEqualTo(40);
        assertThat(gifResult.height()).isEqualTo(25);
        assertThat(ImageInspector.detectType(gifResult.content())).contains(ImageInspector.ImageType.JPEG);
        assertThat(bmpResult.contentType()).isEqualTo("image/jpeg");
        assertThat(bmpResult.width()).isEqualTo(33);
        assertThat(bmpResult.height()).isEqualTo(17);
        assertThat(tiffResult.contentType()).isEqualTo("image/jpeg");
        assertThat(tiffResult.width()).isEqualTo(12);
        assertThat(tiffResult.height()).isEqualTo(50);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(gifResult.content()));
        assertThat(decoded.getWidth()).isEqualTo(40);
    }

    @Test
    void shouldUseWhiteBackground_whenGifHasTransparency() throws IOException {
        // Arrange
        // Paleta com um único índice totalmente transparente (cor preta com alfa 0)
        byte[] zero = {0, (byte) 255};
        var palette = new java.awt.image.IndexColorModel(1, 2, zero, zero, zero, 0);
        BufferedImage indexed = new BufferedImage(8, 8, BufferedImage.TYPE_BYTE_BINARY, palette);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(indexed, "gif", out);
        byte[] gif = out.toByteArray();

        // Act
        var result = ImageInspector.inspect(gif, "a.gif");

        // Assert
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(result.content()));
        int rgb = decoded.getRGB(4, 4);
        assertThat((rgb >> 16) & 0xFF).isGreaterThan(240);
        assertThat((rgb >> 8) & 0xFF).isGreaterThan(240);
        assertThat(rgb & 0xFF).isGreaterThan(240);
    }

    @Test
    void shouldThrow_whenContentIsNotAnImage() {
        // Arrange
        byte[] garbage = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9};

        // Act & Assert
        assertThatThrownBy(() -> ImageInspector.inspect(garbage, "lixo.bin"))
                .isInstanceOf(InvalidArchiveException.class)
                .hasMessageContaining("lixo.bin");
    }

    @Test
    void shouldThrow_whenImageIsCorrupted() throws IOException {
        // Arrange
        byte[] png = image("png", 10, 10, BufferedImage.TYPE_INT_RGB);
        byte[] corrupted = java.util.Arrays.copyOf(png, 12);

        // Act & Assert
        assertThatThrownBy(() -> ImageInspector.inspect(corrupted, "a.png"))
                .isInstanceOf(InvalidArchiveException.class);
    }
}
