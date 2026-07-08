import api from "@/lib/axios";
import type {ReadingListEntry, ReadingStatus, RatingResponse} from "@/types/engagement";

export function getReadingList(): Promise<ReadingListEntry[]> {
    return api.get<ReadingListEntry[]>("/reading-list").then((r) => r.data);
}

export function setReadingStatus(mangaId: string, status: ReadingStatus): Promise<ReadingListEntry> {
    return api.put<ReadingListEntry>(`/reading-list/${mangaId}`, {status}).then((r) => r.data);
}

export function removeFromReadingList(mangaId: string): Promise<void> {
    return api.delete(`/reading-list/${mangaId}`).then(() => undefined);
}

export function getMyRating(mangaId: string): Promise<RatingResponse | null> {
    return api.get<RatingResponse>(`/mangas/${mangaId}/rating`)
        .then((r) => (r.status === 200 ? r.data : null));
}

export function createRating(mangaId: string, score: number): Promise<RatingResponse> {
    return api.post<RatingResponse>(`/mangas/${mangaId}/rating`, {score}).then((r) => r.data);
}

export function updateRating(mangaId: string, score: number): Promise<RatingResponse> {
    return api.put<RatingResponse>(`/mangas/${mangaId}/rating`, {score}).then((r) => r.data);
}

export function deleteRating(mangaId: string): Promise<void> {
    return api.delete(`/mangas/${mangaId}/rating`).then(() => undefined);
}
