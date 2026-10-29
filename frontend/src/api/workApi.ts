import api from "@/lib/axios";
import type {Page} from "@/types/common";
import type {
    WorkCard,
    WorkDetail,
    WorkListFilters,
    WorkRequest,
    Tag,
    TagCategory,
    TagRequest,
    Volume,
    VolumeUploadUrlResponse,
} from "@/types/work";

export function listWorks(filters: WorkListFilters): Promise<Page<WorkCard>> {
    const params = new URLSearchParams();
    params.set("page", String(filters.page ?? 0));
    params.set("size", String(filters.size ?? 24));
    if (filters.title) params.set("title", filters.title);
    if (filters.format) params.set("format", filters.format);
    if (filters.statusOrigin) params.set("statusOrigin", filters.statusOrigin);
    if (filters.tagIds && filters.tagIds.length > 0) params.set("tagIds", filters.tagIds.join(","));
    return api.get<Page<WorkCard>>(`/works?${params}`).then((r) => r.data);
}

export function getWork(slugOrId: string): Promise<WorkDetail> {
    return api.get<WorkDetail>(`/works/${slugOrId}`).then((r) => r.data);
}

export function createWork(request: WorkRequest): Promise<WorkDetail> {
    return api.post<WorkDetail>("/works", request).then((r) => r.data);
}

export function updateWork(id: string, request: WorkRequest): Promise<WorkDetail> {
    return api.put<WorkDetail>(`/works/${id}`, request).then((r) => r.data);
}

export function deleteWork(id: string): Promise<void> {
    return api.delete(`/works/${id}`).then(() => undefined);
}

export function getVolumeUploadUrl(workId: string, volumeNumber: number): Promise<VolumeUploadUrlResponse> {
    return api.post<VolumeUploadUrlResponse>(`/works/${workId}/volumes/upload-url`, {volumeNumber}).then((r) => r.data);
}

export function finalizeVolumeUpload(workId: string, objectName: string, volumeNumber: number): Promise<Volume> {
    return api.post<Volume>(`/works/${workId}/volumes/finalize`, {objectName, volumeNumber}).then((r) => r.data);
}

export function deleteVolume(workId: string, volumeId: string): Promise<void> {
    return api.delete(`/works/${workId}/volumes/${volumeId}`).then(() => undefined);
}

export function listTagCategories(): Promise<TagCategory[]> {
    return api.get<TagCategory[]>("/tag-categories").then((r) => r.data);
}

export function listTags(): Promise<Tag[]> {
    return api.get<Tag[]>("/tags").then((r) => r.data);
}

export function createTagCategory(name: string): Promise<TagCategory> {
    return api.post<TagCategory>("/tag-categories", {name}).then((r) => r.data);
}

export function createTag(request: TagRequest): Promise<Tag> {
    return api.post<Tag>("/tags", request).then((r) => r.data);
}

export function updateTag(id: string, request: TagRequest): Promise<Tag> {
    return api.put<Tag>(`/tags/${id}`, request).then((r) => r.data);
}

export function deleteTag(id: string): Promise<void> {
    return api.delete(`/tags/${id}`).then(() => undefined);
}
