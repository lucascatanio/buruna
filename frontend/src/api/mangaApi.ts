import api from "@/lib/axios";
import type {Page} from "@/types/common";
import type {
    MangaCard,
    MangaDetail,
    MangaListFilters,
    MangaRequest,
    Tag,
    TagCategory,
    TagRequest,
    Volume,
    VolumeUploadUrlResponse,
} from "@/types/manga";

export function listMangas(filters: MangaListFilters): Promise<Page<MangaCard>> {
    const params = new URLSearchParams();
    params.set("page", String(filters.page ?? 0));
    params.set("size", String(filters.size ?? 24));
    if (filters.title) params.set("title", filters.title);
    if (filters.format) params.set("format", filters.format);
    if (filters.statusOrigin) params.set("statusOrigin", filters.statusOrigin);
    if (filters.tagIds && filters.tagIds.length > 0) params.set("tagIds", filters.tagIds.join(","));
    return api.get<Page<MangaCard>>(`/mangas?${params}`).then((r) => r.data);
}

export function getManga(slugOrId: string): Promise<MangaDetail> {
    return api.get<MangaDetail>(`/mangas/${slugOrId}`).then((r) => r.data);
}

export function createManga(request: MangaRequest): Promise<MangaDetail> {
    return api.post<MangaDetail>("/mangas", request).then((r) => r.data);
}

export function updateManga(id: string, request: MangaRequest): Promise<MangaDetail> {
    return api.put<MangaDetail>(`/mangas/${id}`, request).then((r) => r.data);
}

export function deleteManga(id: string): Promise<void> {
    return api.delete(`/mangas/${id}`).then(() => undefined);
}

export function getVolumeUploadUrl(mangaId: string, volumeNumber: number): Promise<VolumeUploadUrlResponse> {
    return api.post<VolumeUploadUrlResponse>(`/mangas/${mangaId}/volumes/upload-url`, {volumeNumber}).then((r) => r.data);
}

export function finalizeVolumeUpload(mangaId: string, objectName: string, volumeNumber: number): Promise<Volume> {
    return api.post<Volume>(`/mangas/${mangaId}/volumes/finalize`, {objectName, volumeNumber}).then((r) => r.data);
}

export function deleteVolume(mangaId: string, volumeId: string): Promise<void> {
    return api.delete(`/mangas/${mangaId}/volumes/${volumeId}`).then(() => undefined);
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
