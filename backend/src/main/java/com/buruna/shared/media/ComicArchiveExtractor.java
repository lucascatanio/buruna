package com.buruna.shared.media;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Extrai as páginas de um CBZ na ordem de leitura, uma por vez, sem gravar nada em disco.
 * O arquivo vem de usuários, então todo limite é aplicado sobre os bytes realmente lidos.
 */
@Component
public class ComicArchiveExtractor {

    public void extractCbz(Path cbzFile, ArchiveLimits limits, Consumer<ExtractedPage> sink) {
        try (ZipFile zip = new ZipFile(cbzFile.toFile())) {
            List<? extends ZipEntry> entries = ArchivePages.selectPages(
                    zip.stream().filter(entry -> !entry.isDirectory()).toList(), ZipEntry::getName, limits);

            ArchivePages.Emitter emitter = new ArchivePages.Emitter(limits, sink);
            for (ZipEntry entry : entries) {
                emitter.emit(entry.getName(), readLimited(zip, entry, limits.maxPageBytes()));
            }
        } catch (IOException e) {
            throw new InvalidArchiveException("O arquivo não é um CBZ válido", e);
        }
    }

    // Não confia no tamanho declarado no zip: lê no máximo maxPageBytes + 1 bytes reais.
    private byte[] readLimited(ZipFile zip, ZipEntry entry, long maxPageBytes) throws IOException {
        try (InputStream in = zip.getInputStream(entry)) {
            return in.readNBytes((int) Math.min(maxPageBytes + 1, Integer.MAX_VALUE - 8));
        }
    }

    static int naturalCompare(String a, String b) {
        return ArchivePages.naturalCompare(a, b);
    }
}
