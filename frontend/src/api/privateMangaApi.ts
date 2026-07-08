import api from "@/lib/axios";
import type {Page} from "@/types/common";
import type {PrivateManga, PrivateMangaRequest, QuotaInfo, VolumeUploadUrlResponse} from "@/types/manga";

export function listMyMangas(size = 50): Promise<Page<PrivateManga>> {
    return api.get<Page<PrivateManga>>(`/my/mangas?size=${size}`).then((r) => r.data);
}

export function getMyManga(id: string): Promise<PrivateManga> {
    return api.get<PrivateManga>(`/my/mangas/${id}`).then((r) => r.data);
}

export function getMyQuota(): Promise<QuotaInfo> {
    return api.get<QuotaInfo>("/my/mangas/quota").then((r) => r.data);
}

export function createMyManga(request: PrivateMangaRequest): Promise<PrivateManga> {
    return api.post<PrivateManga>("/my/mangas", request).then((r) => r.data);
}

export function updateMyManga(id: string, request: PrivateMangaRequest): Promise<PrivateManga> {
    return api.put<PrivateManga>(`/my/mangas/${id}`, request).then((r) => r.data);
}

export function deleteMyManga(id: string): Promise<void> {
    return api.delete(`/my/mangas/${id}`).then(() => undefined);
}

export function getMyVolumeUploadUrl(mangaId: string, volumeNumber: number): Promise<VolumeUploadUrlResponse> {
    return api.post<VolumeUploadUrlResponse>(`/my/mangas/${mangaId}/volumes/upload-url`, {volumeNumber}).then((r) => r.data);
}

export function finalizeMyVolumeUpload(mangaId: string, objectName: string, volumeNumber: number): Promise<PrivateManga> {
    return api.post<PrivateManga>(`/my/mangas/${mangaId}/volumes/finalize`, {objectName, volumeNumber}).then((r) => r.data);
}

export function deleteMyVolume(mangaId: string, volumeId: string): Promise<PrivateManga> {
    return api.delete<PrivateManga>(`/my/mangas/${mangaId}/volumes/${volumeId}`).then((r) => r.data);
}

export function submitForApproval(mangaId: string): Promise<PrivateManga> {
    return api.post<PrivateManga>(`/my/mangas/${mangaId}/submit`).then((r) => r.data);
}

export function promoteManga(mangaId: string): Promise<PrivateManga> {
    return api.post<PrivateManga>(`/my/mangas/${mangaId}/promote`).then((r) => r.data);
}
