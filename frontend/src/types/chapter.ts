export type ChapterStatus = "PROCESSING" | "PUBLISHED" | "FAILED" | "UNPUBLISHED";

/** PAGES: imagens por página (quadrinhos). FILE: livro lido como arquivo inteiro. */
export type ChapterKind = "PAGES" | "FILE";

export type ChapterFileFormat = "PDF" | "EPUB";

/** Item da lista de capítulos de uma obra (sem as páginas). */
export interface ChapterListItem {
    id: string;
    language: string;
    /** Decimal (10.5 existe); nulo em extras, que usam o rótulo. */
    number: number | null;
    label: string | null;
    title: string | null;
    scanlationGroup: string | null;
    kind: ChapterKind;
    /** PDF ou EPUB num livro; nulo num capítulo de imagens. */
    fileFormat: ChapterFileFormat | null;
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
    kind: ChapterKind;
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

/** Livro: o arquivo inteiro. {@code pageCount} só no PDF. */
export interface ChapterManifestFile {
    url: string;
    format: ChapterFileFormat;
    pageCount: number | null;
}

export interface ChapterManifest {
    chapterId: string;
    workId: string;
    language: string;
    number: number | null;
    label: string | null;
    title: string | null;
    scanlationGroup: string | null;
    kind: ChapterKind;
    /** Vazio num livro. */
    pages: ChapterManifestPage[];
    /** Só num livro. */
    file: ChapterManifestFile | null;
    previousChapterId: string | null;
    nextChapterId: string | null;
    /** Até quando as URLs das páginas valem; depois disso, peça o manifesto de novo. */
    urlsExpireAt: string;
}
