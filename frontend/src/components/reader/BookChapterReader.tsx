import {lazy, Suspense, useCallback, useEffect, useRef} from "react";
import {useNavigate} from "react-router-dom";
import {getChapterManifest, saveChapterPosition, saveChapterProgress} from "@/api/readingApi";
import {chapterName} from "@/lib/chapterLabel";
import type {ChapterManifest, ChapterManifestFile} from "@/types/chapter";
import type {ProgressResponse} from "@/types/reading";
import {Loading} from "@/components/Loading";
import {LogoMark} from "@/components/Logo";
import {PdfDocumentReader} from "./PdfDocumentReader";

// o leitor de EPUB (e o foliate-js) só é baixado por quem abre um EPUB
const EpubReader = lazy(() => import("./EpubReader").then((m) => ({default: m.EpubReader})));

const PROGRESS_DEBOUNCE_MS = 1500;

type Pending = { page: number } | { position: string; percent: number };

interface BookChapterReaderProps {
    chapterId: string;
    manifest: ChapterManifest & { file: ChapterManifestFile };
    progress: ProgressResponse | null;
    workTitle?: string;
    detailsUrl: string;
    onBack: () => void;
}

/** Livro lido como arquivo inteiro: PDF pelo pdf.js, EPUB pelo foliate-js. */
export function BookChapterReader({chapterId, manifest, progress, workTitle, detailsUrl, onBack}: BookChapterReaderProps) {
    const pending = useRef<Pending | null>(null);
    const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
    // livro já concluído reabre do começo; o "Lido" continua registrado
    const resume = progress && !progress.finished ? progress : null;

    const flush = useCallback(() => {
        if (timer.current) clearTimeout(timer.current);
        timer.current = null;
        const next = pending.current;
        pending.current = null;
        if (!next) return;
        const request = "page" in next
            ? saveChapterProgress(chapterId, next.page)
            : saveChapterPosition(chapterId, next.position, next.percent);
        request.catch((e) => console.warn("Failed to save progress:", e));
    }, [chapterId]);

    useEffect(() => () => flush(), [flush]);

    function queue(next: Pending) {
        pending.current = next;
        if (timer.current) clearTimeout(timer.current);
        timer.current = setTimeout(flush, PROGRESS_DEBOUNCE_MS);
    }

    // a URL nova vem do manifesto de prefetch, que não grava histórico de novo
    const fetchFreshUrl = useCallback(
        () => getChapterManifest(chapterId, true).then((fresh) => fresh.file?.url ?? Promise.reject(new Error("sem arquivo"))),
        [chapterId],
    );

    function handleBack() {
        flush();
        onBack();
    }

    const edition = chapterName(manifest);

    if (manifest.file.format === "EPUB") {
        return (
            <Suspense fallback={
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black">
                    <Loading className="text-paper" label="Carregando livro"/>
                </div>
            }>
                <EpubReader
                    url={manifest.file.url}
                    fetchFreshUrl={fetchFreshUrl}
                    initialPosition={resume?.position ?? null}
                    title={workTitle}
                    badge={edition}
                    // NUMERIC(5,4) no banco
                    onRelocate={(position, percent) => queue({position, percent: Math.round(percent * 10000) / 10000})}
                    onBack={handleBack}
                />
            </Suspense>
        );
    }

    return (
        <PdfDocumentReader
            initialUrl={manifest.file.url}
            fetchFreshUrl={fetchFreshUrl}
            initialPage={resume?.currentPage ?? 1}
            title={workTitle}
            badge={edition}
            loadingLabel="Carregando livro"
            onPageChange={(page) => queue({page})}
            onComplete={(total) => {
                // a última página vai na hora: o livro fica como lido
                pending.current = {page: total};
                flush();
            }}
            onBack={handleBack}
            completion={<BookCompletion title={workTitle} edition={edition} detailsUrl={detailsUrl}/>}
        />
    );
}

function BookCompletion({title, edition, detailsUrl}: { title?: string; edition: string; detailsUrl: string }) {
    const navigate = useNavigate();
    return (
        <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/80 backdrop-blur-sm">
            <div className="relative mx-4 flex w-full max-w-xs flex-col items-center gap-6 overflow-hidden rounded-lg border border-paper/10 bg-ink p-8 shadow-2xl">
                <span aria-hidden="true" className="screentone absolute inset-0 mask-[linear-gradient(to_bottom,#000,transparent_60%)]"/>
                <LogoMark className="relative h-12 w-auto text-paper" decorative/>
                <div className="relative space-y-1 text-center">
                    <p className="text-lg font-semibold text-paper">Livro concluído</p>
                    <p className="text-sm text-paper/50">{[title, edition].filter(Boolean).join(" · ")}</p>
                </div>
                <button
                    className="relative h-11 w-full rounded-sm border border-paper/20 text-sm text-paper transition-colors hover:bg-paper/10"
                    onClick={() => navigate(detailsUrl)}
                >
                    Voltar aos detalhes
                </button>
            </div>
        </div>
    );
}
