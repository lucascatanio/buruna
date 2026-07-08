export interface VolumeUrlResponse {
    url: string;
}

export interface ProgressResponse {
    currentPage: number;
}

export interface HistoryEntry {
    volumeId: string;
    volumeNumber: number;
    mangaId: string;
    mangaTitle: string;
    mangaCoverUrl: string | null;
    readAt: string;
}
