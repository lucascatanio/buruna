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
    /** Num site do manga-downloader: o código do idioma como o site o escreve ("" se ele não informa); null no MangaDex. */
    sourceLanguage?: string | null;
}

export type SourceName = "MANGADEX" | "MANGA_DOWNLOADER";

/**
 * Import de site (ADR-54): {@code externalId} é a URL da série, e a obra vem de
 * {@code linkWorkId} ou de {@code aniListId}, nunca dos dois.
 */
export interface ImportWorkRequest {
    source: SourceName;
    externalId: string;
    languages: LanguageChoice[];
    linkWorkId: string | null;
    aniListId: number | null;
    /** Sinopse revisada pelo ADM; vazia, fica a do AniList. */
    synopsis: string | null;
}

/** Resultado da busca no AniList. {@code coverThumbnailUrl} só abre pelo backend. */
export interface WorkMetadataSearchResult {
    externalId: string;
    title: string;
    year: number | null;
    contentRating: "SAFE" | "SUGGESTIVE" | "EROTICA" | "PORNOGRAPHIC" | null;
    originalLanguage: string | null;
    status: string | null;
    coverThumbnailUrl: string | null;
    sourceUrl: string;
}

/** A obra como o import vai criá-la. {@code catalogWork}: a obra do catálogo já ligada a este id. */
export interface WorkMetadataPreview {
    externalId: string;
    title: string;
    alternativeTitles: string[];
    synopsis: string | null;
    format: string;
    originCountry: string | null;
    statusOrigin: string;
    year: number | null;
    contentWarnings: string[];
    tags: { name: string; categoryName: string }[];
    coverUrl: string | null;
    sourceUrl: string;
    catalogWork: { id: string; slug: string; title: string } | null;
}

/** A série de um site, listada pelo manga-downloader. Idioma "" é o do site que não informa. */
export interface SiteSeriesListing {
    title: string;
    grabber: string;
    tracked: boolean;
    /** {@code chapterCount} nulo: o site oferece o idioma, mas a listagem padrão não o trouxe. */
    languages: { code: string; chapterCount: number | null }[];
    groups: { id: string; name: string; chapterCount: number }[];
    chapters: {
        number: string | null;
        title: string | null;
        language: string;
        groups: string[];
        volume: number | null;
        locked: boolean;
    }[];
}

export type SourceChapterState = "PENDING" | "READY" | "FAILED" | "IGNORED";

export interface TrackedWork {
    id: string;
    workId: string;
    workTitle: string;
    workSlug: string;
    source: SourceName;
    externalId: string;
    sourceUrl: string;
    status: "ACTIVE" | "PAUSED";
    languages: LanguageChoice[];
    chapterCounts: Partial<Record<SourceChapterState, number>>;
    lastCheckedAt: string | null;
    lastSuccessAt: string | null;
    consecutiveFailures: number;
    lastError: string | null;
    /** Aviso da última verificação: um idioma voltou sem capítulos enquanto o site listou outros. */
    listingNotice: string | null;
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
