export interface VolumeUrlResponse {
    url: string;
}

export interface ProgressResponse {
    volumeId: string;
    currentPage: number;
    /** Total de páginas do volume; nulo enquanto o volume não for aberto com o leitor atual. */
    totalPages: number | null;
    finished: boolean;
    updatedAt: string;
}

export interface HistoryEntry {
    volumeId: string;
    volumeNumber: number;
    mangaId: string;
    mangaTitle: string;
    mangaCoverUrl: string | null;
    readAt: string;
}
