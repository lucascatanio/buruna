import {useEffect, useRef, useState, useCallback} from "react";
import {useParams, useNavigate, useLocation} from "react-router-dom";
import * as pdfjsLib from "pdfjs-dist";
import type {PDFDocumentProxy, RenderTask} from "pdfjs-dist";
import {getManga} from "@/api/mangaApi";
import {getVolumeProgress, getVolumeUrl, saveProgress as saveProgressApi} from "@/api/readingApi";
import {getSignedUrl, setSignedUrl} from "@/lib/signedUrlCache";
import {Loading} from "@/components/Loading";
import {LogoMark} from "@/components/Logo";
import {
    ArrowLeft,
    ChevronLeft,
    ChevronRight,
    AlignJustify,
    BookOpen,
    SlidersHorizontal,
    X,
    Loader2,
} from "lucide-react";

pdfjsLib.GlobalWorkerOptions.workerSrc = "/pdf.worker.min.mjs";

type ReadMode = "paged" | "scroll";

interface ReaderState {
    mangaId?: string;
    mangaTitle?: string;
    mangaSlug?: string;
    volumeNumber?: number;
    backUrl?: string;
}

// salva progresso com debounce de 1.5s
let progressTimer: ReturnType<typeof setTimeout> | null = null;

function saveProgress(volumeId: string, page: number) {
    if (progressTimer) clearTimeout(progressTimer);
    progressTimer = setTimeout(() => {
        saveProgressApi(volumeId, page)
            .catch((e) => console.warn("Failed to save progress:", e));
    }, 1500);
}

// leitor modo página a página

interface PageJump {
    page: number;
    v: number;
}

interface PagedReaderProps {
    pdf: PDFDocumentProxy;
    initialPage: number;
    volumeId: string;
    brightness: number;
    contrast: number;
    onPageChange: (p: number) => void;
    onComplete?: () => void;
    pageJump?: PageJump | null;
}

function PagedReader({pdf, initialPage, volumeId, brightness, contrast, onPageChange, onComplete, pageJump}: PagedReaderProps) {
    const canvasRef = useRef<HTMLCanvasElement>(null);
    const renderTaskRef = useRef<RenderTask | null>(null);
    const [currentPage, setCurrentPage] = useState(initialPage);
    const [rendering, setRendering] = useState(false);
    const touchStartX = useRef<number | null>(null);

    const renderPage = useCallback(async (pageNum: number) => {
        if (!canvasRef.current) return;
        setRendering(true);
        try {
            // cancela render anterior se ainda estiver em andamento
            renderTaskRef.current?.cancel();
            const page = await pdf.getPage(pageNum);
            const container = canvasRef.current.parentElement!;
            const containerWidth = container.clientWidth;
            const dpr = Math.min(window.devicePixelRatio || 1, 3);
            const viewport = page.getViewport({scale: 1});
            const scale = containerWidth / viewport.width;
            const renderViewport = page.getViewport({scale: Math.max(scale, 1.5) * dpr});
            const displayViewport = page.getViewport({scale: scale});

            const canvas = canvasRef.current;
            canvas.width = renderViewport.width;
            canvas.height = renderViewport.height;
            canvas.style.width = `${displayViewport.width}px`;
            canvas.style.height = `${displayViewport.height}px`;

            const ctx = canvas.getContext("2d")!;
            const task = page.render({canvasContext: ctx, viewport: renderViewport});
            renderTaskRef.current = task;
            await task.promise;
        } catch (e: any) {
            if (e?.name !== "RenderingCancelledException") {
                console.error("Render error:", e);
            }
        } finally {
            setRendering(false);
        }
    }, [pdf]);

    useEffect(() => {
        renderPage(currentPage);
        onPageChange(currentPage);
        saveProgress(volumeId, currentPage);
    }, [currentPage, renderPage, volumeId, onPageChange]);

    function goTo(page: number) {
        const clamped = Math.max(1, Math.min(pdf.numPages, page));
        setCurrentPage(clamped);
    }

    function tryAdvance() {
        if (currentPage >= pdf.numPages) {
            onComplete?.();
        } else {
            goTo(currentPage + 1);
        }
    }

    useEffect(() => {
        if (pageJump != null) {
            const clamped = Math.max(1, Math.min(pdf.numPages, pageJump.page));
            setCurrentPage(clamped);
        }
    }, [pageJump, pdf.numPages]);

    // swipe mobile
    function handleTouchStart(e: React.TouchEvent) {
        touchStartX.current = e.touches[0].clientX;
    }

    function handleTouchEnd(e: React.TouchEvent) {
        if (touchStartX.current === null) return;
        const dx = e.changedTouches[0].clientX - touchStartX.current;
        if (Math.abs(dx) > 50) {
            if (dx < 0) tryAdvance();
            else goTo(currentPage - 1);
        }
        touchStartX.current = null;
    }

    useEffect(() => {
        function handleKey(e: KeyboardEvent) {
            if (e.key === "ArrowRight" || e.key === "ArrowDown") tryAdvance();
            if (e.key === "ArrowLeft" || e.key === "ArrowUp") goTo(currentPage - 1);
        }

        window.addEventListener("keydown", handleKey);
        return () => window.removeEventListener("keydown", handleKey);
    }, [currentPage]);

    return (
        <div className="flex-1 flex flex-col overflow-hidden">
            <div
                className="flex-1 flex items-center justify-center overflow-auto px-2 py-2"
                onTouchStart={handleTouchStart}
                onTouchEnd={handleTouchEnd}
            >
                <div className="relative w-full max-w-3xl">
                    {rendering && (
                        <div className="absolute inset-0 flex items-center justify-center z-10">
                            <Loader2 className="w-8 h-8 animate-spin text-white/60"/>
                        </div>
                    )}
                    <canvas
                        ref={canvasRef}
                        className="w-full h-auto block"
                        style={{
                            filter: `brightness(${brightness}%) contrast(${contrast}%)`,
                            opacity: rendering ? 0.4 : 1,
                            transition: "opacity 0.15s",
                        }}
                    />
                </div>
            </div>

            <div className="flex items-center justify-center gap-4 py-3 bg-black/40 backdrop-blur-sm shrink-0">
                <button
                    className="p-2 rounded-full hover:bg-white/10 disabled:opacity-30 transition-colors"
                    onClick={() => goTo(currentPage - 1)}
                    disabled={currentPage <= 1}
                >
                    <ChevronLeft className="w-6 h-6 text-white"/>
                </button>
                <span className="text-white text-sm min-w-[80px] text-center tabular-nums">
                    {currentPage} / {pdf.numPages}
                </span>
                <button
                    className="p-2 rounded-full hover:bg-white/10 transition-colors"
                    onClick={tryAdvance}
                >
                    <ChevronRight className="w-6 h-6 text-white"/>
                </button>
            </div>
        </div>
    );
}

// leitor modo scroll contínuo

interface ScrollPageProps {
    pdf: PDFDocumentProxy;
    pageNum: number;
    brightness: number;
    contrast: number;
    onVisible: (pageNum: number) => void;
}

function ScrollPage({pdf, pageNum, brightness, contrast, onVisible}: ScrollPageProps) {
    const containerRef = useRef<HTMLDivElement>(null);
    const canvasRef = useRef<HTMLCanvasElement>(null);
    const renderedRef = useRef(false);

    // IntersectionObserver: renderiza quando entra no viewport
    useEffect(() => {
        const el = containerRef.current;
        if (!el) return;

        const renderObserver = new IntersectionObserver(
            async (entries) => {
                if (entries[0].isIntersecting && !renderedRef.current) {
                    renderedRef.current = true;
                    renderObserver.disconnect();
                    try {
                        const page = await pdf.getPage(pageNum);
                        const containerWidth = el.clientWidth || 700;
                        const dpr = Math.min(window.devicePixelRatio || 1, 3);
                        const viewport = page.getViewport({scale: 1});
                        const scale = containerWidth / viewport.width;
                        const renderViewport = page.getViewport({scale: Math.max(scale, 1.5) * dpr});
                        const displayViewport = page.getViewport({scale: scale});
                        const canvas = canvasRef.current!;
                        canvas.width = renderViewport.width;
                        canvas.height = renderViewport.height;
                        canvas.style.width = `${displayViewport.width}px`;
                        canvas.style.height = `${displayViewport.height}px`;
                        const ctx2d = canvas.getContext("2d")!;
                        await page.render({canvasContext: ctx2d, viewport: renderViewport}).promise;
                    } catch (e) {
                        console.error(`Erro ao renderizar página ${pageNum}:`, e);
                    }
                }
            },
            {rootMargin: "300px 0px"}
        );
        renderObserver.observe(el);

        const visibleObserver = new IntersectionObserver(
            (entries) => {
                if (entries[0].isIntersecting) onVisible(pageNum);
            },
            {threshold: 0.5}
        );
        visibleObserver.observe(el);

        return () => {
            renderObserver.disconnect();
            visibleObserver.disconnect();
        };
    }, [pdf, pageNum, onVisible]);

    return (
        <div ref={containerRef} className="w-full max-w-3xl mx-auto mb-1">
            <canvas
                ref={canvasRef}
                className="w-full h-auto block"
                style={{filter: `brightness(${brightness}%) contrast(${contrast}%)`}}
            />
        </div>
    );
}

interface ScrollReaderProps {
    pdf: PDFDocumentProxy;
    initialPage: number;
    volumeId: string;
    brightness: number;
    contrast: number;
    onPageChange: (p: number) => void;
    onComplete?: () => void;
    pageJump?: PageJump | null;
}

function ScrollReader({pdf, initialPage, volumeId, brightness, contrast, onPageChange, onComplete, pageJump}: ScrollReaderProps) {
    const pages = Array.from({length: pdf.numPages}, (_, i) => i + 1);
    const initialScrollRef = useRef(false);
    const pageRefs = useRef<(HTMLDivElement | null)[]>([]);
    const currentPageRef = useRef(initialPage);
    const endRef = useRef<HTMLDivElement>(null);

    const handleVisible = useCallback((pageNum: number) => {
        if (pageNum !== currentPageRef.current) {
            currentPageRef.current = pageNum;
            onPageChange(pageNum);
            saveProgress(volumeId, pageNum);
        }
    }, [volumeId, onPageChange]);

    useEffect(() => {
        if (initialScrollRef.current || initialPage <= 1) return;
        const timer = setTimeout(() => {
            const el = pageRefs.current[initialPage - 1];
            if (el) {
                el.scrollIntoView({behavior: "instant"});
                initialScrollRef.current = true;
            }
        }, 500);
        return () => clearTimeout(timer);
    }, [initialPage]);

    // jump to page from slider/input
    useEffect(() => {
        if (pageJump != null) {
            const el = pageRefs.current[pageJump.page - 1];
            if (el) el.scrollIntoView({behavior: "smooth"});
        }
    }, [pageJump]);

    // completion: sentinel at bottom becomes visible after last page
    useEffect(() => {
        const el = endRef.current;
        if (!el || !onComplete) return;
        const observer = new IntersectionObserver(
            (entries) => {
                if (entries[0].isIntersecting && currentPageRef.current >= pdf.numPages) {
                    onComplete();
                }
            },
            {threshold: 1.0}
        );
        observer.observe(el);
        return () => observer.disconnect();
    }, [pdf.numPages, onComplete]);

    return (
        <div className="flex-1 overflow-y-auto px-2 py-2">
            {pages.map((pageNum) => (
                <div key={pageNum} ref={(el) => {
                    pageRefs.current[pageNum - 1] = el;
                }}>
                    <ScrollPage
                        pdf={pdf}
                        pageNum={pageNum}
                        brightness={brightness}
                        contrast={contrast}
                        onVisible={handleVisible}
                    />
                </div>
            ))}
            <div ref={endRef} className="h-px"/>
        </div>
    );
}

interface CompletionOverlayProps {
    state: ReaderState;
}

function CompletionOverlay({state}: CompletionOverlayProps) {
    const navigate = useNavigate();
    // undefined = loading, null = no next volume
    const [nextVol, setNextVol] = useState<{id: string; volumeNumber: number} | null | undefined>(undefined);

    useEffect(() => {
        if (!state.mangaSlug) {
            setNextVol(null);
            return;
        }
        getManga(state.mangaSlug)
            .then((data) => {
                const vols: {id: string; volumeNumber: number}[] = data.volumes ?? [];
                const sorted = [...vols].sort((a, b) => a.volumeNumber - b.volumeNumber);
                const next = sorted.find(v => v.volumeNumber > (state.volumeNumber ?? 0)) ?? null;
                setNextVol(next);
            })
            .catch(() => setNextVol(null));
    }, [state.mangaSlug, state.volumeNumber]);

    return (
        <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/80 backdrop-blur-sm">
            <div className="relative mx-4 flex w-full max-w-xs flex-col items-center gap-6 overflow-hidden rounded-lg border border-paper/10 bg-ink p-8 shadow-2xl">
                <span aria-hidden="true" className="screentone absolute inset-0 mask-[linear-gradient(to_bottom,#000,transparent_60%)]"/>
                <LogoMark className="relative h-12 w-auto text-paper" decorative/>
                <div className="relative text-center space-y-1">
                    <p className="text-paper text-lg font-semibold">Volume concluído</p>
                    {(state.mangaTitle || state.volumeNumber != null) && (
                        <p className="text-paper/50 text-sm">
                            {[state.mangaTitle, state.volumeNumber != null && `Vol. ${state.volumeNumber}`]
                                .filter(Boolean).join(" — ")}
                        </p>
                    )}
                </div>
                <div className="relative flex flex-col gap-3 w-full">
                    {nextVol === undefined ? (
                        <Loading className="py-1"/>
                    ) : nextVol !== null && (
                        <button
                            className="w-full h-11 bg-paper text-ink rounded-sm text-sm font-semibold hover:bg-paper/90 transition-colors"
                            onClick={() => navigate(`/leitor/${nextVol.id}`, {
                                state: {
                                    mangaId: state.mangaId,
                                    mangaTitle: state.mangaTitle,
                                    mangaSlug: state.mangaSlug,
                                    volumeNumber: nextVol.volumeNumber,
                                    backUrl: state.backUrl,
                                }
                            })}
                        >
                            Próximo volume — Vol. {nextVol.volumeNumber}
                        </button>
                    )}
                    <button
                        className="w-full h-11 border border-paper/20 text-paper rounded-sm text-sm hover:bg-paper/10 transition-colors"
                        onClick={() => navigate(state.backUrl ?? "/")}
                    >
                        Voltar aos detalhes
                    </button>
                </div>
            </div>
        </div>
    );
}

export function ReaderPage() {
    const {volumeId} = useParams<{ volumeId: string }>();
    const navigate = useNavigate();
    const location = useLocation();
    const state = (location.state ?? {}) as ReaderState;

    const [pdf, setPdf] = useState<PDFDocumentProxy | null>(null);
    const [loadError, setLoadError] = useState<string | null>(null);
    const [loadingPdf, setLoadingPdf] = useState(true);

    const [mode, setMode] = useState<ReadMode>("paged");
    const [currentPage, setCurrentPage] = useState(1);
    const [initialPage, setInitialPage] = useState(1);
    const [progressLoaded, setProgressLoaded] = useState(false);

    const [showControls, setShowControls] = useState(true);
    const [showSettings, setShowSettings] = useState(false);
    const [brightness, setBrightness] = useState(100);
    const [contrast, setContrast] = useState(100);

    const [showCompletion, setShowCompletion] = useState(false);
    const [pageJump, setPageJump] = useState<PageJump | null>(null);
    const [pageInputValue, setPageInputValue] = useState("1");
    const [pageInputFocused, setPageInputFocused] = useState(false);

    const controlsTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

    // keep input in sync with current page when not typing
    useEffect(() => {
        if (!pageInputFocused) setPageInputValue(String(currentPage));
    }, [currentPage, pageInputFocused]);

    useEffect(() => {
        if (!volumeId) return;

        async function init() {
            try {
                const cached = getSignedUrl(volumeId!);
                const [urlRes, progressRes] = await Promise.allSettled([
                    cached ? Promise.resolve(cached) : getVolumeUrl(volumeId!).then(r => r.url),
                    getVolumeProgress(volumeId!),
                ]);

                if (urlRes.status === "rejected") {
                    setLoadError("Não foi possível carregar o volume. Verifique sua conexão.");
                    setLoadingPdf(false);
                    return;
                }

                const signedUrl = urlRes.value as string;
                if (!cached) setSignedUrl(volumeId!, signedUrl);

                let startPage = 1;
                if (progressRes.status === "fulfilled" && progressRes.value) {
                    startPage = progressRes.value.currentPage ?? 1;
                }
                setInitialPage(startPage);
                setCurrentPage(startPage);
                setProgressLoaded(true);

                const loadingTask = pdfjsLib.getDocument({
                    url: signedUrl,
                    withCredentials: false,
                    cMapUrl: "/cmaps/",
                    cMapPacked: true,
                    rangeChunkSize: 131072,
                });
                const doc = await loadingTask.promise;
                setPdf(doc);
            } catch (e) {
                setLoadError("Erro ao carregar o PDF.");
            } finally {
                setLoadingPdf(false);
            }
        }

        init();
    }, [volumeId]);

    // controles desaparecem após 3s sem interação
    function showControlsTemporarily() {
        setShowControls(true);
        if (controlsTimer.current) clearTimeout(controlsTimer.current);
        controlsTimer.current = setTimeout(() => setShowControls(false), 3000);
    }

    function toggleControls() {
        if (showControls) {
            setShowControls(false);
            if (controlsTimer.current) clearTimeout(controlsTimer.current);
        } else {
            showControlsTemporarily();
        }
    }

    function handleBack() {
        if (progressTimer) {
            clearTimeout(progressTimer);
            saveProgressApi(volumeId!, currentPage)
                .catch((e) => console.warn("Failed to save progress:", e));
        }
        navigate(state.backUrl ?? -1 as any);
    }

    function handleVolumeComplete() {
        if (progressTimer) {
            clearTimeout(progressTimer);
            progressTimer = null;
        }
        setShowCompletion(true);
        saveProgressApi(volumeId!, 1)
            .catch((e) => console.warn("Failed to reset progress:", e));
    }

    function handlePageJump(page: number) {
        if (!pdf) return;
        const clamped = Math.max(1, Math.min(pdf.numPages, page));
        setPageJump(prev => ({page: clamped, v: (prev?.v ?? 0) + 1}));
    }

    function handlePageInputConfirm() {
        const page = parseInt(pageInputValue, 10);
        if (!isNaN(page)) handlePageJump(page);
    }

    if (loadingPdf || !progressLoaded) {
        return (
            <div className="fixed inset-0 bg-black flex items-center justify-center z-50">
                <Loading className="text-paper" label="Carregando volume"/>
            </div>
        );
    }

    if (loadError || !pdf) {
        return (
            <div className="fixed inset-0 bg-black flex items-center justify-center z-50">
                <div className="flex flex-col items-center gap-4 text-center px-6">
                    <p className="text-paper/80">{loadError ?? "Erro desconhecido."}</p>
                    <button
                        className="min-h-11 px-3 text-sm text-paper/60 underline underline-offset-4 hover:text-paper"
                        onClick={handleBack}
                    >
                        Voltar
                    </button>
                </div>
            </div>
        );
    }

    return (
        <div
            className="fixed inset-0 bg-[#111] flex flex-col z-50 select-none"
            onClick={toggleControls}
        >

            {/* Navbar overlay — absolute so it never occupies space in the flex flow */}
            <div className="absolute top-0 left-0 right-0 z-10 pointer-events-none">
                <div
                    className={`
                        flex flex-col
                        bg-ink/85 backdrop-blur-sm border-b border-paper/10
                        transition-all duration-200
                        ${showControls ? "opacity-100 pointer-events-auto" : "opacity-0 pointer-events-none"}
                    `}
                    onClick={(e) => e.stopPropagation()}
                >
                    {/* row 1: back + mode/settings */}
                    <div className="flex items-center justify-between px-3 py-2">
                        <button
                            className="flex min-h-11 min-w-0 items-center gap-2.5 px-1.5 text-paper/85 transition-colors hover:text-paper"
                            onClick={handleBack}
                            aria-label={`Voltar${state.mangaTitle ? ` para ${state.mangaTitle}` : ""}`}
                        >
                            <ArrowLeft className="size-5 shrink-0"/>
                            <span className="truncate text-sm font-medium max-w-[45vw] sm:max-w-[320px]">
                                {state.mangaTitle ?? "Voltar"}
                            </span>
                            {state.volumeNumber != null && (
                                <span className="shrink-0 font-mono text-xs text-paper/50">
                                    VOL. {String(state.volumeNumber).padStart(2, "0")}
                                </span>
                            )}
                        </button>

                        <div className="flex items-center gap-1">
                            <button
                                className="flex size-11 items-center justify-center rounded-sm text-paper/70 transition-colors hover:bg-paper/10 hover:text-paper"
                                aria-label={mode === "paged" ? "Mudar para scroll contínuo" : "Mudar para página a página"}
                                title={mode === "paged" ? "Mudar para scroll contínuo" : "Mudar para página a página"}
                                onClick={() => setMode(m => m === "paged" ? "scroll" : "paged")}
                            >
                                {mode === "paged"
                                    ? <AlignJustify className="w-5 h-5"/>
                                    : <BookOpen className="w-5 h-5"/>
                                }
                            </button>

                            <button
                                className={`flex size-11 items-center justify-center rounded-sm transition-colors hover:bg-paper/10 ${showSettings ? "bg-paper/10 text-paper" : "text-paper/70 hover:text-paper"}`}
                                aria-label="Ajustes de imagem"
                                aria-expanded={showSettings}
                                onClick={() => setShowSettings(s => !s)}
                            >
                                <SlidersHorizontal className="w-5 h-5"/>
                            </button>
                        </div>
                    </div>

                    {/* row 2: page slider + numeric input */}
                    <div className="flex items-center gap-2 px-4 pb-2.5">
                        <input
                            type="range"
                            aria-label="Página"
                            min={1}
                            max={pdf.numPages}
                            value={currentPage}
                            onChange={(e) => handlePageJump(Number(e.target.value))}
                            className="flex-1 accent-shu cursor-pointer"
                        />
                        <div className="flex items-center gap-1 shrink-0">
                            <input
                                type="number"
                                min={1}
                                max={pdf.numPages}
                                value={pageInputValue}
                                onChange={(e) => setPageInputValue(e.target.value)}
                                onFocus={() => setPageInputFocused(true)}
                                onBlur={() => { setPageInputFocused(false); handlePageInputConfirm(); }}
                                onKeyDown={(e) => { if (e.key === "Enter") { handlePageInputConfirm(); (e.target as HTMLInputElement).blur(); } }}
                                aria-label="Ir para a página"
                                className="w-12 rounded-sm bg-paper/10 px-1 py-1 text-center font-mono text-xs text-paper focus:bg-paper/20 focus:outline-none"
                            />
                            <span className="font-mono text-xs text-paper/40">/ {pdf.numPages}</span>
                        </div>
                    </div>
                </div>

                {showSettings && (
                    <div
                        className={`
                            bg-ink/90 backdrop-blur-sm border-b border-paper/10 px-4 py-3 space-y-3
                            transition-all duration-200
                            ${showControls ? "opacity-100 pointer-events-auto" : "opacity-0 pointer-events-none"}
                        `}
                        onClick={(e) => e.stopPropagation()}
                    >
                        <div className="flex items-center justify-between">
                            <span className="font-mono text-[11px] uppercase tracking-widest text-paper/60">Ajustes de imagem</span>
                            <button aria-label="Fechar ajustes" className="flex size-9 items-center justify-center" onClick={() => setShowSettings(false)}>
                                <X className="size-4 text-paper/40 transition-colors hover:text-paper"/>
                            </button>
                        </div>
                        <div className="grid grid-cols-2 gap-4">
                            <label className="space-y-1.5">
                                <span className="font-mono text-xs text-paper/60">Brilho {brightness}%</span>
                                <input
                                    type="range" min={30} max={200} value={brightness}
                                    onChange={(e) => setBrightness(Number(e.target.value))}
                                    className="w-full accent-white"
                                />
                            </label>
                            <label className="space-y-1.5">
                                <span className="font-mono text-xs text-paper/60">Contraste {contrast}%</span>
                                <input
                                    type="range" min={30} max={200} value={contrast}
                                    onChange={(e) => setContrast(Number(e.target.value))}
                                    className="w-full accent-white"
                                />
                            </label>
                        </div>
                    </div>
                )}
            </div>

            {mode === "paged" ? (
                <PagedReader
                    pdf={pdf}
                    initialPage={initialPage}
                    volumeId={volumeId!}
                    brightness={brightness}
                    contrast={contrast}
                    onPageChange={setCurrentPage}
                    onComplete={handleVolumeComplete}
                    pageJump={pageJump}
                />
            ) : (
                <ScrollReader
                    pdf={pdf}
                    initialPage={initialPage}
                    volumeId={volumeId!}
                    brightness={brightness}
                    contrast={contrast}
                    onPageChange={setCurrentPage}
                    onComplete={handleVolumeComplete}
                    pageJump={pageJump}
                />
            )}

            <div
                aria-hidden="true"
                className="pointer-events-none absolute inset-x-0 bottom-0 z-10 h-0.5 bg-paper/10"
            >
                <div className="h-full bg-shu transition-[width] duration-200" style={{width: `${(currentPage / pdf.numPages) * 100}%`}}/>
            </div>

            {showCompletion && <CompletionOverlay state={state}/>}
        </div>
    );
}