package com.buruna.shared.media;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Extrai as páginas de um CBZ na ordem de leitura, uma por vez, sem gravar nada em disco.
 * O arquivo vem de usuários, então todo limite é aplicado sobre os bytes realmente lidos.
 */
@Component
public class ComicArchiveExtractor {

    private static final Set<String> NON_IMAGE_EXTENSIONS =
            Set.of("xml", "txt", "nfo", "json", "url", "html", "htm", "db");
    private static final Set<String> JUNK_NAMES = Set.of("thumbs.db", "desktop.ini");

    public void extractCbz(Path cbzFile, ArchiveLimits limits, Consumer<ExtractedPage> sink) {
        try (ZipFile zip = new ZipFile(cbzFile.toFile())) {
            List<? extends ZipEntry> entries = zip.stream()
                    .filter(ComicArchiveExtractor::isPageCandidate)
                    .sorted(Comparator.comparing(ZipEntry::getName, ComicArchiveExtractor::naturalCompare))
                    .toList();

            if (entries.isEmpty()) {
                throw new InvalidArchiveException("O arquivo não tem nenhuma página");
            }
            if (entries.size() > limits.maxPages()) {
                throw new InvalidArchiveException("O arquivo tem mais de " + limits.maxPages() + " páginas");
            }

            long totalBytes = 0;
            int position = 0;
            for (ZipEntry entry : entries) {
                position++;
                byte[] content = readLimited(zip, entry, position, limits.maxPageBytes());
                totalBytes += content.length;
                if (totalBytes > limits.maxTotalBytes()) {
                    throw new InvalidArchiveException("O arquivo é grande demais depois de descompactado");
                }
                sink.accept(toPage(position, entry.getName(), content));
            }
        } catch (IOException e) {
            throw new InvalidArchiveException("O arquivo não é um CBZ válido", e);
        }
    }

    private ExtractedPage toPage(int position, String entryName, byte[] content) {
        try {
            ImageInspector.InspectedImage image = ImageInspector.inspect(content, entryName);
            return new ExtractedPage(position, image.content(), image.contentType(), image.extension(),
                    image.width(), image.height());
        } catch (InvalidArchiveException e) {
            throw new InvalidArchiveException(
                    "A página " + position + " (" + entryName + ") não é uma imagem válida", e);
        }
    }

    // Não confia no tamanho declarado no zip: lê no máximo maxPageBytes + 1 bytes reais.
    private byte[] readLimited(ZipFile zip, ZipEntry entry, int position, long maxPageBytes) throws IOException {
        try (InputStream in = zip.getInputStream(entry)) {
            byte[] content = in.readNBytes((int) Math.min(maxPageBytes + 1, Integer.MAX_VALUE - 8));
            if (content.length > maxPageBytes) {
                throw new InvalidArchiveException(
                        "A página " + position + " (" + entry.getName() + ") é grande demais");
            }
            return content;
        }
    }

    private static boolean isPageCandidate(ZipEntry entry) {
        if (entry.isDirectory()) {
            return false;
        }
        String[] segments = entry.getName().replace('\\', '/').split("/");
        for (String segment : segments) {
            if (segment.equals("__MACOSX")) {
                return false;
            }
        }
        String fileName = segments[segments.length - 1];
        if (fileName.isEmpty() || fileName.startsWith(".") || JUNK_NAMES.contains(fileName.toLowerCase(Locale.ROOT))) {
            return false;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot >= 0) {
            String ext = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
            return !NON_IMAGE_EXTENSIONS.contains(ext);
        }
        return true;
    }

    /** Ordem natural, case-insensitive: "page2" vem antes de "page10". */
    static int naturalCompare(String a, String b) {
        String x = a.toLowerCase(Locale.ROOT);
        String y = b.toLowerCase(Locale.ROOT);
        int i = 0;
        int j = 0;
        while (i < x.length() && j < y.length()) {
            char cx = x.charAt(i);
            char cy = y.charAt(j);
            if (Character.isDigit(cx) && Character.isDigit(cy)) {
                int endX = digitRunEnd(x, i);
                int endY = digitRunEnd(y, j);
                int cmp = compareDigitRuns(x.substring(i, endX), y.substring(j, endY));
                if (cmp != 0) {
                    return cmp;
                }
                i = endX;
                j = endY;
            } else {
                if (cx != cy) {
                    return Character.compare(cx, cy);
                }
                i++;
                j++;
            }
        }
        int cmp = Integer.compare(x.length() - i, y.length() - j);
        return cmp != 0 ? cmp : a.compareTo(b);
    }

    private static int digitRunEnd(String s, int start) {
        int end = start;
        while (end < s.length() && Character.isDigit(s.charAt(end))) {
            end++;
        }
        return end;
    }

    private static int compareDigitRuns(String a, String b) {
        String na = stripLeadingZeros(a);
        String nb = stripLeadingZeros(b);
        if (na.length() != nb.length()) {
            return Integer.compare(na.length(), nb.length());
        }
        return na.compareTo(nb);
    }

    private static String stripLeadingZeros(String s) {
        int k = 0;
        while (k < s.length() - 1 && s.charAt(k) == '0') {
            k++;
        }
        return s.substring(k);
    }
}
