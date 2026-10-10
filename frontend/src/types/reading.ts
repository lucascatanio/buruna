export interface VolumeUrlResponse {
    url: string;
}

/** {@code volumeId} (volume antigo) ou {@code chapterId}: só um dos dois vem preenchido. */
export interface ProgressResponse {
    volumeId: string | null;
    chapterId: string | null;
    currentPage: number;
    /** Total de páginas do volume; nulo enquanto o volume não for aberto com o leitor atual. */
    totalPages: number | null;
    finished: boolean;
    updatedAt: string;
}

/** Entrada do histórico: de um volume antigo ou de um capítulo. */
export interface HistoryEntry {
    volumeId: string | null;
    volumeNumber: number | null;
    chapterId: string | null;
    chapterNumber: number | null;
    chapterLabel: string | null;
    language: string | null;
    workId: string;
    workTitle: string;
    workCoverUrl: string | null;
    readAt: string;
}
