import api from "@/lib/axios";
import type {Page} from "@/types/common";
import type {HistoryEntry, ProgressResponse, VolumeUrlResponse} from "@/types/reading";

export function getVolumeUrl(volumeId: string): Promise<VolumeUrlResponse> {
    return api.get<VolumeUrlResponse>(`/reader/${volumeId}/url`).then((r) => r.data);
}

export function saveProgress(volumeId: string, currentPage: number): Promise<ProgressResponse> {
    return api.post<ProgressResponse>(`/reader/${volumeId}/progress`, {currentPage}).then((r) => r.data);
}

export function getVolumeProgress(volumeId: string): Promise<ProgressResponse | null> {
    return api.get<ProgressResponse>(`/reader/${volumeId}/progress`)
        .then((r) => (r.status === 200 ? r.data : null));
}

export function getBatchProgress(volumeIds: string[]): Promise<Record<string, number>> {
    return api.get<Record<string, number>>(`/reader/progress/batch?volumeIds=${volumeIds.join(",")}`).then((r) => r.data);
}

export function getHistory(page: number, size = 20): Promise<Page<HistoryEntry>> {
    return api.get<Page<HistoryEntry>>(`/reader/history?page=${page}&size=${size}`).then((r) => r.data);
}
