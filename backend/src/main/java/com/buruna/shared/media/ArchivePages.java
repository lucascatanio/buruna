package com.buruna.shared.media;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/** Regras de páginas que CBZ, CBR e PDF compartilham: seleção, ordem natural e limites. */
final class ArchivePages {

    private static final Set<String> NON_IMAGE_EXTENSIONS =
            Set.of("xml", "txt", "nfo", "json", "url", "html", "htm", "db");
    private static final Set<String> JUNK_NAMES = Set.of("thumbs.db", "desktop.ini");

    private ArchivePages() {
    }

    /** Filtra o lixo, ordena em ordem natural e recusa arquivo vazio ou com páginas demais. */
    static <T> List<T> selectPages(Collection<T> items, Function<T, String> nameOf, ArchiveLimits limits) {
        List<T> pages = items.stream()
                .filter(item -> isPageCandidate(nameOf.apply(item)))
                .sorted(Comparator.comparing(nameOf, ArchivePages::naturalCompare))
                .toList();
        if (pages.isEmpty()) {
            throw new InvalidArchiveException("O arquivo não tem nenhuma página");
        }
        checkPageCount(pages.size(), limits);
        return pages;
    }

    static void checkPageCount(int count, ArchiveLimits limits) {
        if (count > limits.maxPages()) {
            throw new InvalidArchiveException("O arquivo tem mais de " + limits.maxPages() + " páginas");
        }
    }

    /** {@code entryName} é o caminho dentro do arquivo, com {@code /} ou {@code \}. Diretórios não são candidatos. */
    static boolean isPageCandidate(String entryName) {
        String[] segments = entryName.replace('\\', '/').split("/");
        for (String segment : segments) {
            if (segment.equals("__MACOSX")) {
                return false;
            }
        }
        String fileName = segments.length == 0 ? "" : segments[segments.length - 1];
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

    /**
     * Entrega as páginas ao {@code sink} na ordem, aplicando {@code maxPageBytes} e {@code maxTotalBytes}
     * sobre os bytes realmente recebidos e inspecionando cada imagem.
     */
    static final class Emitter {

        private final ArchiveLimits limits;
        private final Consumer<ExtractedPage> sink;
        private int position;
        private long totalBytes;

        Emitter(ArchiveLimits limits, Consumer<ExtractedPage> sink) {
            this.limits = limits;
            this.sink = sink;
        }

        void emit(String name, byte[] content) {
            position++;
            if (content.length > limits.maxPageBytes()) {
                throw new InvalidArchiveException("A página " + position + " (" + name + ") é grande demais");
            }
            totalBytes += content.length;
            if (totalBytes > limits.maxTotalBytes()) {
                throw new InvalidArchiveException("O arquivo é grande demais depois de descompactado");
            }
            sink.accept(toPage(name, content));
        }

        private ExtractedPage toPage(String name, byte[] content) {
            try {
                ImageInspector.InspectedImage image = ImageInspector.inspect(content, name);
                return new ExtractedPage(position, image.content(), image.contentType(), image.extension(),
                        image.width(), image.height());
            } catch (InvalidArchiveException e) {
                throw new InvalidArchiveException(
                        "A página " + position + " (" + name + ") não é uma imagem válida", e);
            }
        }
    }
}
