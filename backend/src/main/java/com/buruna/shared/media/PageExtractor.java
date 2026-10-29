package com.buruna.shared.media;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.function.Consumer;

/** Ponto de entrada da extração de páginas: escolhe o extrator pelo formato do arquivo. */
@Component
public class PageExtractor {

    private final ComicArchiveExtractor cbzExtractor;
    private final RarArchiveExtractor cbrExtractor;
    private final PdfPageExtractor pdfExtractor;

    public PageExtractor(ComicArchiveExtractor cbzExtractor, RarArchiveExtractor cbrExtractor,
                         PdfPageExtractor pdfExtractor) {
        this.cbzExtractor = cbzExtractor;
        this.cbrExtractor = cbrExtractor;
        this.pdfExtractor = pdfExtractor;
    }

    /**
     * Entrega as páginas de {@code file} ao {@code sink}, uma por vez e na ordem de leitura.
     *
     * @throws InvalidArchiveException arquivo inválido ou fora dos limites (mensagem própria para o usuário)
     */
    public void extract(Path file, ArchiveFormat format, ArchiveLimits limits, Consumer<ExtractedPage> sink) {
        switch (format) {
            case CBZ -> cbzExtractor.extractCbz(file, limits, sink);
            case CBR -> cbrExtractor.extractCbr(file, limits, sink);
            case PDF -> pdfExtractor.extractPdf(file, limits, sink);
        }
    }
}
