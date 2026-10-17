import api from "@/lib/axios";
import type {
    ImportWorkRequest,
    SourceGroupSummary,
    SourceSearchResult,
    TrackedWork,
    WorkSource,
} from "@/types/sourcing";

export function searchSource(query: string): Promise<SourceSearchResult[]> {
    return api.get<SourceSearchResult[]>("/admin/sourcing/search", {params: {q: query}}).then((r) => r.data);
}

export function listSourceGroups(externalId: string, language: string): Promise<SourceGroupSummary[]> {
    return api.get<SourceGroupSummary[]>(`/admin/sourcing/works/${encodeURIComponent(externalId)}/groups`,
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
