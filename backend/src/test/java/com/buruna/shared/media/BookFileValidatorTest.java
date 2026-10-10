package com.buruna.shared.media;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookFileValidatorTest {

    static final String CONTAINER = "<?xml version=\"1.0\"?><container version=\"1.0\" "
            + "xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\"><rootfiles>"
            + "<rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/>"
            + "</rootfiles></container>";

    @TempDir
    Path dir;

    final BookFileValidator validator = new BookFileValidator();

    Path pdf(int pages, boolean withPassword) throws IOException {
        Path file = dir.resolve("livro.pdf");
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                doc.addPage(new PDPage());
            }
            if (withPassword) {
                StandardProtectionPolicy policy = new StandardProtectionPolicy("dono", "leitor", new AccessPermission());
                doc.protect(policy);
            }
            doc.save(file.toFile());
        }
        return file;
    }

    Path epub(Map<String, String> entries) throws IOException {
        Path file = dir.resolve("livro.epub");
        try (OutputStream out = Files.newOutputStream(file); ZipOutputStream zip = new ZipOutputStream(out)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes());
                zip.closeEntry();
            }
        }
        return file;
    }

    @Test
    void shouldReturnPageCount_whenPdfOpens() throws IOException {
        assertThat(validator.validatePdf(pdf(4, false))).isEqualTo(4);
    }

    @Test
    void shouldRejectWithPasswordMessage_whenPdfNeedsAPassword() throws IOException {
        Path file = pdf(1, true);

        assertThatThrownBy(() -> validator.validatePdf(file))
                .isInstanceOf(InvalidArchiveException.class)
                .hasMessage("O PDF está protegido por senha");
    }

    @Test
    void shouldReject_whenFileIsNotAPdf() throws IOException {
        Path file = Files.writeString(dir.resolve("x.pdf"), "não é pdf");

        assertThatThrownBy(() -> validator.validatePdf(file)).hasMessage("O arquivo não é um PDF válido");
    }

    @Test
    void shouldAccept_whenEpubHasMimetypeContainerAndPackage() throws IOException {
        Path file = epub(Map.of("mimetype", "application/epub+zip",
                "META-INF/container.xml", CONTAINER,
                "OEBPS/content.opf", "<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\"/>"));

        validator.validateEpub(file);
    }

    @Test
    void shouldReject_whenEpubLacksMimetype() throws IOException {
        Path file = epub(Map.of("META-INF/container.xml", CONTAINER, "OEBPS/content.opf", "<package/>"));

        assertThatThrownBy(() -> validator.validateEpub(file)).hasMessage("O arquivo não é um EPUB válido");
    }

    @Test
    void shouldReject_whenContainerPointsToAMissingPackage() throws IOException {
        Path file = epub(Map.of("mimetype", "application/epub+zip", "META-INF/container.xml", CONTAINER));

        assertThatThrownBy(() -> validator.validateEpub(file)).hasMessage("O arquivo não é um EPUB válido");
    }

    @Test
    void shouldRejectWithoutResolvingEntities_whenContainerHasADoctype() throws IOException {
        // XXE: um DOCTYPE com entidade externa não pode ser processado
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE c [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>"
                + "<container><rootfiles><rootfile full-path=\"&x;\"/></rootfiles></container>";
        Path file = epub(Map.of("mimetype", "application/epub+zip", "META-INF/container.xml", xxe));

        assertThatThrownBy(() -> validator.validateEpub(file)).hasMessage("O arquivo não é um EPUB válido");
    }
}
