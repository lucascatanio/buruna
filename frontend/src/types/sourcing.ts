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
    createdAt: string;
}

/** Crédito da fonte na página da obra. */
export interface WorkSource {
    source: string;
    url: string;
}
