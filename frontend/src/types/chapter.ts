export type ChapterStatus = "PROCESSING" | "PUBLISHED" | "FAILED" | "UNPUBLISHED";

/** Item da lista de capítulos de uma obra (sem as páginas). */
export interface ChapterListItem {
    id: string;
    language: string;
    /** Decimal (10.5 existe); nulo em extras, que usam o rótulo. */
    number: number | null;
    label: string | null;
    title: string | null;
    scanlationGroup: string | null;
    status: ChapterStatus;
    /** Motivo da falha, visível só para quem enviou. */
    failureReason: string | null;
    publishedAt: string | null;
    createdAt: string;
}

export interface ChapterLanguage {
    language: string;
    chapterCount: number;
}

export interface ChapterUploadUrlResponse {
    uploadUrl: string;
    objectName: string;
    /** Headers que o PUT precisa repetir para a assinatura bater, inclusive o Content-Type. */
    requiredHeaders: Record<string, string>;
}

export interface ChapterFinalizeRequest {
    objectName: string;
    language: string;
    number?: number | null;
    label?: string | null;
    title?: string | null;
    scanlationGroup?: string | null;
}

export interface ChapterResponse {
    id: string;
    workId: string;
    language: string;
    number: number | null;
    label: string | null;
    title: string | null;
    scanlationGroup: string | null;
    kind: "PAGES" | "FILE";
    status: ChapterStatus;
    pageCount: number;
    publishedAt: string | null;
}

export interface ChapterManifestPage {
    url: string;
    /** Variante de economia de dados, só quando a fonte fornece. */
    dataSaverUrl: string | null;
    width: number;
    height: number;
}

export interface ChapterManifest {
    chapterId: string;
    workId: string;
    language: string;
    number: number | null;
    label: string | null;
    title: string | null;
    scanlationGroup: string | null;
    pages: ChapterManifestPage[];
    previousChapterId: string | null;
    nextChapterId: string | null;
    /** Até quando as URLs das páginas valem; depois disso, peça o manifesto de novo. */
    urlsExpireAt: string;
}
