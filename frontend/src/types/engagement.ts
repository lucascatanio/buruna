export type ReadingStatus = "WANT_TO_READ" | "READING" | "COMPLETED" | "DROPPED";

export interface ReadingListEntry {
    workId: string;
    workSlug: string;
    workTitle: string;
    workCoverUrl: string | null;
    status: ReadingStatus;
    updatedAt: string;
}

export interface RatingResponse {
    workId: string;
    score: number;
    avgRating: number;
    ratingCount: number;
}
