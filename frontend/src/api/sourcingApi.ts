import api from "@/lib/axios";
import type {
    ImportWorkRequest,
    LanguageAvailability,
    LanguageChoice,
    SourceAlert,
    TrackedGroup,
    SourceSearchResult,
    TrackedWork,
    WorkSource,
} from "@/types/sourcing";

export function searchSource(query: string): Promise<SourceSearchResult[]> {
    return api.get<SourceSearchResult[]>("/admin/sourcing/search", {params: {q: query}}).then((r) => r.data);
}

export function getLanguageAvailability(externalId: string, language: string): Promise<LanguageAvailability> {
    return api.get<LanguageAvailability>(`/admin/sourcing/works/${encodeURIComponent(externalId)}/availability`,
        {params: {language}}).then((r) => r.data);
}

/**
 * Miniatura da capa como blob: o MangaDex recusa hotlink, e o endpoint do backend precisa do
 * token, que um {@code <img src>} não manda. Quem chama revoga a URL criada.
 */
export function getCoverThumbnail(url: string): Promise<string> {
    return api.get<Blob>("/admin/sourcing/cover", {params: {url}, responseType: "blob"})
        .then((r) => URL.createObjectURL(r.data));
}

export function importWork(request: ImportWorkRequest): Promise<TrackedWork> {
    return api.post<TrackedWork>("/admin/sourcing/imports", request).then((r) => r.data);
}

/** Pede uma rodada do executor: retoma os downloads pendentes. */
export function requestSourcingRun(): Promise<void> {
    return api.post("/admin/sourcing/run").then(() => undefined);
}

export function listTrackedWorks(): Promise<TrackedWork[]> {
    return api.get<TrackedWork[]>("/admin/sourcing/tracked").then((r) => r.data);
}

/** Nulo quando a obra não veio de uma fonte externa (204). */
export function getWorkSource(workId: string): Promise<WorkSource | null> {
    return api.get<WorkSource>(`/works/${workId}/source`).then((r) => (r.status === 200 ? r.data : null));
}

export function checkTrackedWork(id: string): Promise<void> {
    return api.post(`/admin/sourcing/tracked/${id}/check`).then(() => undefined);
}

export function pauseTrackedWork(id: string): Promise<void> {
    return api.post(`/admin/sourcing/tracked/${id}/pause`).then(() => undefined);
}

export function resumeTrackedWork(id: string): Promise<void> {
    return api.post(`/admin/sourcing/tracked/${id}/resume`).then(() => undefined);
}

export function listSourceAlerts(id: string): Promise<SourceAlert[]> {
    return api.get<SourceAlert[]>(`/admin/sourcing/tracked/${id}/alerts`).then((r) => r.data);
}

export function listTrackedGroups(id: string): Promise<TrackedGroup[]> {
    return api.get<TrackedGroup[]>(`/admin/sourcing/tracked/${id}/groups`).then((r) => r.data);
}

/** Apaga os capítulos do grupo e traz a versão de outro grupo, quando houver. */
export function ignoreGroup(id: string, groupId: string, groupName: string): Promise<void> {
    return api.post(`/admin/sourcing/tracked/${id}/ignored-groups`, {groupId, groupName}).then(() => undefined);
}

export function stopIgnoringGroup(id: string, groupId: string): Promise<void> {
    return api.delete(`/admin/sourcing/tracked/${id}/ignored-groups/${encodeURIComponent(groupId)}`).then(() => undefined);
}

export type SourceChapterAction = "retry" | "redownload" | "use";

export function runSourceChapterAction(sourceChapterId: string, action: SourceChapterAction): Promise<void> {
    return api.post(`/admin/sourcing/source-chapters/${sourceChapterId}/${action}`).then(() => undefined);
}

/** Idiomas que o MangaDex tem desta obra e que ainda não são acompanhados. */
export function listUntrackedSourceLanguages(id: string): Promise<string[]> {
    return api.get<string[]>(`/admin/sourcing/tracked/${id}/source-languages`).then((r) => r.data);
}

/** Acrescenta um idioma à obra acompanhada (ou troca o grupo preferido dele) e verifica a obra. */
export function trackLanguage(id: string, choice: LanguageChoice): Promise<void> {
    return api.post(`/admin/sourcing/tracked/${id}/languages`, choice).then(() => undefined);
}
