import api from "@/lib/axios";
import type {Page} from "@/types/common";
import type {PrivateWork, PrivateWorkRequest, QuotaInfo, VolumeUploadUrlResponse} from "@/types/work";

export function listMyWorks(size = 50): Promise<Page<PrivateWork>> {
    return api.get<Page<PrivateWork>>(`/my/works?size=${size}`).then((r) => r.data);
}

export function getMyWork(id: string): Promise<PrivateWork> {
    return api.get<PrivateWork>(`/my/works/${id}`).then((r) => r.data);
}

export function getMyQuota(): Promise<QuotaInfo> {
    return api.get<QuotaInfo>("/my/works/quota").then((r) => r.data);
}

export function createMyWork(request: PrivateWorkRequest): Promise<PrivateWork> {
    return api.post<PrivateWork>("/my/works", request).then((r) => r.data);
}

export function updateMyWork(id: string, request: PrivateWorkRequest): Promise<PrivateWork> {
    return api.put<PrivateWork>(`/my/works/${id}`, request).then((r) => r.data);
}

export function deleteMyWork(id: string): Promise<void> {
    return api.delete(`/my/works/${id}`).then(() => undefined);
}

export function getMyVolumeUploadUrl(workId: string, volumeNumber: number): Promise<VolumeUploadUrlResponse> {
    return api.post<VolumeUploadUrlResponse>(`/my/works/${workId}/volumes/upload-url`, {volumeNumber}).then((r) => r.data);
}

export function finalizeMyVolumeUpload(workId: string, objectName: string, volumeNumber: number): Promise<PrivateWork> {
    return api.post<PrivateWork>(`/my/works/${workId}/volumes/finalize`, {objectName, volumeNumber}).then((r) => r.data);
}

export function deleteMyVolume(workId: string, volumeId: string): Promise<PrivateWork> {
    return api.delete<PrivateWork>(`/my/works/${workId}/volumes/${volumeId}`).then((r) => r.data);
}

export function submitForApproval(workId: string): Promise<PrivateWork> {
    return api.post<PrivateWork>(`/my/works/${workId}/submit`).then((r) => r.data);
}

export function promoteWork(workId: string): Promise<PrivateWork> {
    return api.post<PrivateWork>(`/my/works/${workId}/promote`).then((r) => r.data);
}
