export type ReadingStatus = "WANT_TO_READ" | "READING" | "COMPLETED" | "DROPPED";

export interface ReadingListEntry {
    mangaId: string;
    mangaSlug: string;
    mangaTitle: string;
    mangaCoverUrl: string | null;
    status: ReadingStatus;
    updatedAt: string;
}

export interface RatingResponse {
    mangaId: string;
    score: number;
    avgRating: number;
    ratingCount: number;
}
