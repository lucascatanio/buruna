package com.buruna.manga.domain;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nome do objeto de um volume no storage, vinculado ao mangá dono (ADR-40).
 *
 * <p>Fase 1 (upload): {@link #pendingFor(UUID)} gera um caminho em {@code pending/},
 * namespaced pelo {@code mangaId} — {@code pending/volumes/{mangaId}/{uuid}.pdf}.
 *
 * <p>Fase 2 (finalize): {@link #parsePending(String, UUID)} só aceita um caminho
 * pendente cujo {@code mangaId} no path bate com o mangá do request. Isso fecha o
 * vetor em que o objectName de um volume PÚBLICO (visível via URL assinada de leitura,
 * {@code /reader/{id}/url}) era reaproveitado para finalizar o upload de um mangá
 * PRIVADO qualquer — ao apagar esse volume forjado, o arquivo público real era apagado
 * junto (o finalize antigo aceitava qualquer objectName vindo do cliente sem checar
 * origem nem posse).
 *
 * <p>Depois do finalize, {@link #finalObjectName()} devolve o nome definitivo
 * {@code volumes/{mangaId}/{uuid}.pdf}, para onde o objeto é movido no storage.
 */
public final class VolumeObjectName {

    private static final Pattern PENDING_PATTERN = Pattern.compile(
            "^pending/volumes/([0-9a-fA-F-]{36})/([0-9a-fA-F-]{36})\\.pdf$"
    );

    private final UUID mangaId;
    private final UUID fileId;

    private VolumeObjectName(UUID mangaId, UUID fileId) {
        this.mangaId = mangaId;
        this.fileId = fileId;
    }

    public static String pendingFor(UUID mangaId) {
        if (mangaId == null) {
            throw new IllegalArgumentException("mangaId não pode ser nulo");
        }
        return "pending/volumes/" + mangaId + "/" + UUID.randomUUID() + ".pdf";
    }

    /**
     * Valida que {@code raw} é um caminho pendente bem formado E que o {@code mangaId}
     * do caminho é exatamente o do request. Regex ancorada: qualquer caractere fora do
     * formato exato (incluindo {@code ..} de path traversal, segmentos extras ou um
     * objectName já finalizado em {@code volumes/...}) não casa e é rejeitado.
     */
    public static VolumeObjectName parsePending(String raw, UUID mangaId) {
        if (raw == null || mangaId == null) {
            throw new InvalidVolumeObjectNameException(String.valueOf(raw));
        }

        Matcher matcher = PENDING_PATTERN.matcher(raw);
        if (!matcher.matches()) {
            throw new InvalidVolumeObjectNameException(raw);
        }

        UUID pathMangaId;
        UUID fileId;
        try {
            pathMangaId = UUID.fromString(matcher.group(1));
            fileId = UUID.fromString(matcher.group(2));
        } catch (IllegalArgumentException e) {
            throw new InvalidVolumeObjectNameException(raw);
        }

        if (!pathMangaId.equals(mangaId)) {
            throw new InvalidVolumeObjectNameException(raw);
        }

        return new VolumeObjectName(pathMangaId, fileId);
    }

    public String finalObjectName() {
        return "volumes/" + mangaId + "/" + fileId + ".pdf";
    }
}
