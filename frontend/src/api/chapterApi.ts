import api from "@/lib/axios";
import type {
    ChapterFinalizeRequest,
    ChapterLanguage,
    ChapterListItem,
    ChapterResponse,
    ChapterUploadUrlResponse,
} from "@/types/chapter";

/**
 * Capítulos de uma obra. {@code scope} escolhe a rota: "public" para o catálogo
 * (/works, colaborador dono ou ADMIN para enviar) e "private" para a coleção (/my/works).
 */
export type ChapterScope = "public" | "private";

function base(scope: ChapterScope, workId: string): string {
    return scope === "public" ? `/works/${workId}/chapters` : `/my/works/${workId}/chapters`;
}

export function listChapters(scope: ChapterScope, workId: string, language?: string): Promise<ChapterListItem[]> {
    const params = language ? `?language=${encodeURIComponent(language)}` : "";
    return api.get<ChapterListItem[]>(`${base(scope, workId)}${params}`).then((r) => r.data);
}

export function listChapterLanguages(workId: string): Promise<ChapterLanguage[]> {
    return api.get<ChapterLanguage[]>(`/works/${workId}/chapters/languages`).then((r) => r.data);
}

export type ChapterSourceFormat = "cbz" | "cbr" | "pdf";

export function getChapterUploadUrl(scope: ChapterScope, workId: string, language: string,
                                    number: number | null, format: ChapterSourceFormat): Promise<ChapterUploadUrlResponse> {
    return api.post<ChapterUploadUrlResponse>(`${base(scope, workId)}/upload-url`, {language, number, format})
        .then((r) => r.data);
}

export function finalizeChapterUpload(scope: ChapterScope, workId: string,
                                      request: ChapterFinalizeRequest): Promise<ChapterResponse> {
    return api.post<ChapterResponse>(`${base(scope, workId)}/finalize`, request).then((r) => r.data);
}

export function retryChapter(scope: ChapterScope, workId: string, chapterId: string): Promise<ChapterResponse> {
    return api.post<ChapterResponse>(`${base(scope, workId)}/${chapterId}/retry`).then((r) => r.data);
}

export function deleteChapter(scope: ChapterScope, workId: string, chapterId: string): Promise<void> {
    return api.delete(`${base(scope, workId)}/${chapterId}`).then(() => undefined);
}
