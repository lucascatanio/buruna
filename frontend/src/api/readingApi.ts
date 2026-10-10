import api from "@/lib/axios";
import type {Page} from "@/types/common";
import type {HistoryEntry, ProgressResponse, VolumeUrlResponse} from "@/types/reading";
import type {ChapterManifest} from "@/types/chapter";

export function getVolumeUrl(volumeId: string): Promise<VolumeUrlResponse> {
    return api.get<VolumeUrlResponse>(`/reader/${volumeId}/url`).then((r) => r.data);
}

export function saveProgress(volumeId: string, currentPage: number, totalPages?: number): Promise<ProgressResponse> {
    return api.post<ProgressResponse>(`/reader/${volumeId}/progress`, {currentPage, totalPages}).then((r) => r.data);
}

export function getVolumeProgress(volumeId: string): Promise<ProgressResponse | null> {
    return api.get<ProgressResponse>(`/reader/${volumeId}/progress`)
        .then((r) => (r.status === 200 ? r.data : null));
}

export function getBatchProgress(volumeIds: string[]): Promise<Record<string, ProgressResponse>> {
    return api.get<Record<string, ProgressResponse>>(`/reader/progress/batch?volumeIds=${volumeIds.join(",")}`).then((r) => r.data);
}

export function getHistory(page: number, size = 20): Promise<Page<HistoryEntry>> {
    return api.get<Page<HistoryEntry>>(`/reader/history?page=${page}&size=${size}`).then((r) => r.data);
}

/**
 * {@code prefetch}: pré-carregamento do próximo capítulo. O backend devolve as páginas sem
 * gravar histórico nem contar visualização, porque o leitor ainda não abriu o capítulo.
 */
export function getChapterManifest(chapterId: string, prefetch = false): Promise<ChapterManifest> {
    return api.get<ChapterManifest>(`/reader/chapters/${chapterId}${prefetch ? "?prefetch=true" : ""}`)
        .then((r) => r.data);
}

/** O total de páginas vem do servidor; só a página atual é enviada. */
export function saveChapterProgress(chapterId: string, currentPage: number): Promise<ProgressResponse> {
    return api.post<ProgressResponse>(`/reader/chapters/${chapterId}/progress`, {currentPage}).then((r) => r.data);
}

/** EPUB: a posição (CFI) e o andamento de 0 a 1, no lugar da página. */
export function saveChapterPosition(chapterId: string, position: string, percent: number): Promise<ProgressResponse> {
    return api.post<ProgressResponse>(`/reader/chapters/${chapterId}/progress`, {position, percent}).then((r) => r.data);
}

export function getChapterProgress(chapterId: string): Promise<ProgressResponse | null> {
    return api.get<ProgressResponse>(`/reader/chapters/${chapterId}/progress`)
        .then((r) => (r.status === 200 ? r.data : null));
}

/** Progresso em todos os capítulos da obra, do mais recente para o mais antigo. */
export function getWorkChapterProgress(workId: string): Promise<ProgressResponse[]> {
    return api.get<ProgressResponse[]>(`/reader/works/${workId}/chapter-progress`).then((r) => r.data);
}
