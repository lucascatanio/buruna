package com.buruna.shared.media;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Confere que um livro enviado (PDF ou EPUB) abre, antes de publicar. Não converte nada: o livro
 * é lido como arquivo. Mensagens de erro vão para quem enviou.
 */
@Component
public class BookFileValidator {

    private static final String EPUB_MIMETYPE = "application/epub+zip";
    private static final String CONTAINER_PATH = "META-INF/container.xml";
    private static final int MAX_XML_BYTES = 1024 * 1024;

    /** Devolve o número de páginas do PDF. */
    public int validatePdf(Path file) {
        try (PDDocument document = Loader.loadPDF(file.toFile(), "", null, null,
                IOUtils.createTempFileOnlyStreamCache())) {
            int pages = document.getNumberOfPages();
            if (pages < 1) {
                throw new InvalidArchiveException("O PDF não tem nenhuma página");
            }
            return pages;
        } catch (InvalidPasswordException e) {
            throw new InvalidArchiveException("O PDF está protegido por senha", e);
        } catch (IOException e) {
            throw new InvalidArchiveException("O arquivo não é um PDF válido", e);
        }
    }

    /**
     * Confere a estrutura mínima de um EPUB: o {@code mimetype}, o {@code META-INF/container.xml}
     * e o pacote (OPF) que ele aponta. O conteúdo HTML não é inspecionado aqui; o leitor o isola
     * num iframe sem scripts (ADR-51).
     */
    public void validateEpub(Path file) {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            ZipEntry mimetype = zip.getEntry("mimetype");
            if (mimetype == null || !EPUB_MIMETYPE.equals(readText(zip, mimetype).trim())) {
                throw invalidEpub(null);
            }
            ZipEntry container = zip.getEntry(CONTAINER_PATH);
            if (container == null) {
                throw invalidEpub(null);
            }
            String packagePath = packagePath(zip, container);
            if (zip.getEntry(packagePath) == null) {
                throw invalidEpub(null);
            }
        } catch (IOException e) {
            throw invalidEpub(e);
        }
    }

    private static String packagePath(ZipFile zip, ZipEntry container) throws IOException {
        Document document = parseXml(zip, container);
        NodeList rootfiles = document.getElementsByTagNameNS("*", "rootfile");
        for (int i = 0; i < rootfiles.getLength(); i++) {
            String path = ((Element) rootfiles.item(i)).getAttribute("full-path");
            if (!path.isBlank()) {
                return path;
            }
        }
        throw invalidEpub(null);
    }

    // DTD e entidades externas desligadas: o XML vem de arquivo enviado por usuário (XXE)
    private static Document parseXml(ZipFile zip, ZipEntry entry) throws IOException {
        try (InputStream in = zip.getInputStream(entry)) {
            byte[] xml = in.readNBytes(MAX_XML_BYTES + 1);
            if (xml.length > MAX_XML_BYTES) {
                throw invalidEpub(null);
            }
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new java.io.ByteArrayInputStream(xml));
        } catch (ParserConfigurationException | SAXException e) {
            throw invalidEpub(e);
        }
    }

    private static String readText(ZipFile zip, ZipEntry entry) throws IOException {
        try (InputStream in = zip.getInputStream(entry)) {
            return new String(in.readNBytes(256), StandardCharsets.US_ASCII);
        }
    }

    private static InvalidArchiveException invalidEpub(Throwable cause) {
        return cause == null
                ? new InvalidArchiveException("O arquivo não é um EPUB válido")
                : new InvalidArchiveException("O arquivo não é um EPUB válido", cause);
    }
}
