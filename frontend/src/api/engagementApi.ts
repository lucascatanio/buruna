import api from "@/lib/axios";
import type {ReadingListEntry, ReadingStatus, RatingResponse} from "@/types/engagement";

export function getReadingList(): Promise<ReadingListEntry[]> {
    return api.get<ReadingListEntry[]>("/reading-list").then((r) => r.data);
}

export function setReadingStatus(workId: string, status: ReadingStatus): Promise<ReadingListEntry> {
    return api.put<ReadingListEntry>(`/reading-list/${workId}`, {status}).then((r) => r.data);
}

export function removeFromReadingList(workId: string): Promise<void> {
    return api.delete(`/reading-list/${workId}`).then(() => undefined);
}

export function getMyRating(workId: string): Promise<RatingResponse | null> {
    return api.get<RatingResponse>(`/works/${workId}/rating`)
        .then((r) => (r.status === 200 ? r.data : null));
}

export function createRating(workId: string, score: number): Promise<RatingResponse> {
    return api.post<RatingResponse>(`/works/${workId}/rating`, {score}).then((r) => r.data);
}

export function updateRating(workId: string, score: number): Promise<RatingResponse> {
    return api.put<RatingResponse>(`/works/${workId}/rating`, {score}).then((r) => r.data);
}

export function deleteRating(workId: string): Promise<void> {
    return api.delete(`/works/${workId}/rating`).then(() => undefined);
}
