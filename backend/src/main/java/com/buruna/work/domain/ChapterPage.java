package com.buruna.work.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;
import java.util.Optional;

/**
 * Uma página de um capítulo de imagens. Guarda largura e altura para o leitor reservar o
 * espaço antes da imagem chegar, sem a página "pular" no scroll. A variante de economia de
 * dados ({@code dataSaverObjectName}) só existe quando a fonte já a fornece.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChapterPage {

    @Column(name = "page_index", nullable = false)
    private int position;

    @Column(name = "object_name", nullable = false, length = 500)
    private String objectName;

    @Column(name = "data_saver_object_name", length = 500)
    private String dataSaverObjectName;

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    private ChapterPage(int position, String objectName, String dataSaverObjectName, int width, int height, long sizeBytes) {
        this.position = position;
        this.objectName = objectName;
        this.dataSaverObjectName = dataSaverObjectName;
        this.width = width;
        this.height = height;
        this.sizeBytes = sizeBytes;
    }

    /** {@code position} começa em 1. */
    public static ChapterPage of(int position, String objectName, String dataSaverObjectName,
                          int width, int height, long sizeBytes) {
        if (position < 1) {
            throw new InvalidChapterException("Posição de página precisa ser >= 1: " + position);
        }
        if (objectName == null || objectName.isBlank()) {
            throw new InvalidChapterException("Página " + position + " sem objeto no storage");
        }
        if (width < 1 || height < 1) {
            throw new InvalidChapterException("Página " + position + " com dimensões inválidas: " + width + "x" + height);
        }
        if (sizeBytes < 1) {
            throw new InvalidChapterException("Página " + position + " vazia");
        }
        return new ChapterPage(position, objectName, dataSaverObjectName, width, height, sizeBytes);
    }

    public Optional<String> getDataSaverObjectName() {
        return Optional.ofNullable(dataSaverObjectName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChapterPage that)) return false;
        return position == that.position && objectName.equals(that.objectName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(position, objectName);
    }
}
