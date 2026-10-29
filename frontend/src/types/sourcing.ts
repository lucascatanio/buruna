/** Resultado da busca no MangaDex. {@code tracked}: a obra já é acompanhada. */
export interface SourceSearchResult {
    externalId: string;
    title: string;
    year: number | null;
    contentRating: "SAFE" | "SUGGESTIVE" | "EROTICA" | "PORNOGRAPHIC" | null;
    originalLanguage: string | null;
    status: string | null;
    availableLanguages: string[];
    /** Endereço do MangaDex; só abre pelo backend ({@link getCoverThumbnail}). */
    coverThumbnailUrl: string | null;
    sourceUrl: string;
    tracked: boolean;
}

export interface SourceGroupSummary {
    id: string;
    name: string;
    chapterCount: number;
}

/** O que o MangaDex tem num idioma: disponíveis, já publicados (inclui retirados) e grupos. */
export interface LanguageAvailability {
    language: string;
    availableChapters: number;
    publishedChapters: number;
    groups: SourceGroupSummary[];
}

export interface LanguageChoice {
    language: string;
    preferredGroupId: string | null;
    preferredGroupName: string | null;
}

export interface ImportWorkRequest {
    externalId: string;
    languages: LanguageChoice[];
    linkWorkId: string | null;
}

export type SourceChapterState = "PENDING" | "READY" | "FAILED" | "IGNORED";

export interface TrackedWork {
    id: string;
    workId: string;
    workTitle: string;
    workSlug: string;
    source: string;
    externalId: string;
    sourceUrl: string;
    status: "ACTIVE" | "PAUSED";
    languages: LanguageChoice[];
    chapterCounts: Partial<Record<SourceChapterState, number>>;
    lastCheckedAt: string | null;
    lastSuccessAt: string | null;
    consecutiveFailures: number;
    lastError: string | null;
    pausedAt: string | null;
    /** Só na pausa automática (falhas seguidas ao verificar). */
    pausedReason: string | null;
    ignoredGroups: { id: string; name: string | null }[];
    alertCount: number;
    createdAt: string;
}

export type SourceAlertType = "FAILED" | "REMOVED_AT_SOURCE" | "PAGES_CHANGED" | "PREFERRED_AVAILABLE";

/** Algo numa versão que pede decisão do ADM; as ações agem sobre {@code sourceChapterId}. */
export interface SourceAlert {
    sourceChapterId: string;
    trackedWorkId: string;
    type: SourceAlertType;
    language: string;
    number: string | null;
    title: string | null;
    groupNames: string | null;
    /** FAILED: o erro; PREFERRED_AVAILABLE: o grupo da versão que está no capítulo. */
    detail: string | null;
    detectedAt: string | null;
}

export interface TrackedGroup {
    id: string;
    name: string;
    versionCount: number;
    ignored: boolean;
}

/** Crédito da fonte na página da obra. */
export interface WorkSource {
    source: string;
    url: string;
}
