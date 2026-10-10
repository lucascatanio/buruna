import {useCallback, useEffect, useLayoutEffect, useRef, useState} from "react";
import {useLocation, useNavigate, useParams} from "react-router-dom";
import {getChapterManifest, getChapterProgress, saveChapterProgress} from "@/api/readingApi";
import {chapterName, defaultReadMode} from "@/lib/chapterLabel";
import type {ChapterManifest, ChapterManifestPage} from "@/types/chapter";
import {Loading} from "@/components/Loading";
import {LogoMark} from "@/components/Logo";
import {
    AlignJustify,
    ArrowLeft,
    BookOpen,
    ChevronLeft,
    ChevronRight,
    Loader2,
    SkipBack,
    SlidersHorizontal,
    X,
} from "lucide-react";

type ReadMode = "paged" | "scroll";

interface ChapterReaderState {
    workId?: string;
    workTitle?: string;
    workSlug?: string;
    format?: string;
    backUrl?: string;
}

interface PageJump {
    page: number;
    v: number;
}

const PROGRESS_DEBOUNCE_MS = 1500;
const CONTROLS_HIDE_MS = 3000;
// intervalo mínimo entre duas recargas do manifesto: se as URLs novas também falharem,
// o problema não é expiração e insistir só repetiria o erro
const RELOAD_COOLDOWN_MS = 60 * 1000;
// quantas páginas à frente são baixadas no modo página a página
const PRELOAD_AHEAD = 3;
// a que distância do fim o próximo capítulo começa a ser preparado
const NEXT_CHAPTER_PREFETCH_DISTANCE = 3;

// localStorage pode lançar exceção (navegação privada, dados bloqueados)
function readStorage(key: string): string | null {
    try {
        return localStorage.getItem(key);
    } catch {
        return null;
    }
}

function writeStorage(key: string, value: string) {
    try {
        localStorage.setItem(key, value);
    } catch {
        // sem persistência: a escolha vale só nesta sessão
    }
}

function initialDataSaver(): boolean {
    const stored = readStorage("reader-data-saver");
    if (stored === "1") return true;
    if (stored === "0") return false;
    // em tela de toque (celular, geralmente em 4G) o padrão é economizar dados
    try {
        return window.matchMedia("(pointer: coarse)").matches;
    } catch {
        return false;
    }
}

function initialMode(workId: string, format: string | undefined): ReadMode {
    const stored = readStorage(`reader-mode:${workId}`);
    return stored === "paged" || stored === "scroll" ? stored : defaultReadMode(format);
}

function pageSrc(page: ChapterManifestPage, dataSaver: boolean): string {
    return dataSaver && page.dataSaverUrl ? page.dataSaverUrl : page.url;
}

function aspectRatioOf(page: ChapterManifestPage): string {
    return page.width > 0 && page.height > 0 ? `${page.width} / ${page.height}` : "2 / 3";
}

function clampPage(page: number, total: number): number {
    return Math.max(1, Math.min(total, page));
}

// imagem de uma página, com o espaço reservado pelo aspect-ratio antes de carregar

interface PageImageProps {
    page: ChapterManifestPage;
    pageNumber: number;
    src: string;
    filter: string;
    errored: boolean;
    onError: () => void;
    onRetry: () => void;
}

function PageImage({page, pageNumber, src, filter, errored, onError, onRetry}: PageImageProps) {
    const [loaded, setLoaded] = useState(false);

    return (
        <div className="relative w-full bg-paper/5" style={{aspectRatio: aspectRatioOf(page)}}>
            {!loaded && !errored && (
                <div className="absolute inset-0 flex items-center justify-center">
                    <Loader2 className="size-8 animate-spin text-white/40"/>
                </div>
            )}
            {errored ? (
                <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 px-4 text-center">
                    <p className="text-sm text-paper/70">Não foi possível carregar esta página.</p>
                    <button
                        className="min-h-11 rounded-sm border border-paper/20 px-4 text-sm text-paper transition-colors hover:bg-paper/10"
                        onClick={(e) => {
                            e.stopPropagation();
                            onRetry();
                        }}
                    >
                        Tentar de novo
                    </button>
                </div>
            ) : (
                <img
                    src={src}
                    alt={`Página ${pageNumber}`}
                    className="block size-full object-contain"
                    style={{filter, opacity: loaded ? 1 : 0, transition: "opacity 0.15s"}}
                    draggable={false}
                    onLoad={() => setLoaded(true)}
                    onError={onError}
                />
            )}
        </div>
    );
}

// modo página a página

const PAGED_CHROME_PX = 72;

function fitToScreenWidth(page: ChapterManifestPage): string {
    const ratio = page.width > 0 && page.height > 0 ? page.width / page.height : 2 / 3;
    return `min(48rem, calc((100dvh - ${PAGED_CHROME_PX}px) * ${ratio.toFixed(4)}))`;
}

interface PagedReaderProps {
    pages: ChapterManifestPage[];
    page: number;
    version: number;
    dataSaver: boolean;
    filter: string;
    errorPages: Set<number>;
    onPageChange: (page: number) => void;
    onComplete: () => void;
    onImageError: (index: number) => void;
    onRetry: (index: number) => void;
    blocked: boolean;
}

function PagedReader({pages, page, version, dataSaver, filter, errorPages, onPageChange, onComplete, onImageError, onRetry, blocked}: PagedReaderProps) {
    const total = pages.length;
    const scrollerRef = useRef<HTMLDivElement>(null);
    const touchStartX = useRef<number | null>(null);
    // referências às imagens pré-carregadas: sem elas o navegador pode descartar o download
    const preloaded = useRef<HTMLImageElement[]>([]);

    // baixa só as próximas páginas, nunca o capítulo inteiro
    useEffect(() => {
        for (let i = 1; i <= PRELOAD_AHEAD; i++) {
            const next = pages[page - 1 + i];
            if (!next) break;
            const img = new Image();
            img.src = pageSrc(next, dataSaver);
            preloaded.current.push(img);
        }
        if (preloaded.current.length > 20) preloaded.current.splice(0, preloaded.current.length - 20);
    }, [pages, page, dataSaver]);

    useEffect(() => {
        scrollerRef.current?.scrollTo({top: 0});
    }, [page]);

    function tryAdvance() {
        if (page >= total) onComplete();
        else onPageChange(page + 1);
    }

    function goBack() {
        onPageChange(clampPage(page - 1, total));
    }

    useEffect(() => {
        if (blocked) return;

        function handleKey(e: KeyboardEvent) {
            if (e.target instanceof HTMLInputElement) return;
            if (e.key === "ArrowRight" || e.key === "ArrowDown") tryAdvance();
            if (e.key === "ArrowLeft" || e.key === "ArrowUp") goBack();
        }

        window.addEventListener("keydown", handleKey);
        return () => window.removeEventListener("keydown", handleKey);
    });

    function handleTouchStart(e: React.TouchEvent) {
        touchStartX.current = e.touches[0].clientX;
    }

    function handleTouchEnd(e: React.TouchEvent) {
        if (touchStartX.current === null) return;
        const dx = e.changedTouches[0].clientX - touchStartX.current;
        if (Math.abs(dx) > 50) {
            if (dx < 0) tryAdvance();
            else goBack();
        }
        touchStartX.current = null;
    }

    const current = pages[page - 1];
    const src = pageSrc(current, dataSaver);

    return (
        <div className="flex flex-1 flex-col overflow-hidden">
            <div
                ref={scrollerRef}
                className="flex flex-1 items-start justify-center overflow-auto px-2 py-2"
                onTouchStart={handleTouchStart}
                onTouchEnd={handleTouchEnd}
            >
                {/* a página inteira cabe na tela: largura limitada pela altura disponível, descontada a
                    barra de navegação de baixo */}
                <div className="my-auto w-full" style={{maxWidth: fitToScreenWidth(current)}}>
                    <PageImage
                        key={`${page}-${version}-${src}`}
                        page={current}
                        pageNumber={page}
                        src={src}
                        filter={filter}
                        errored={errorPages.has(page - 1)}
                        onError={() => onImageError(page - 1)}
                        onRetry={() => onRetry(page - 1)}
                    />
                </div>
            </div>

            <div className="flex shrink-0 items-center justify-center gap-4 bg-black/40 py-3 backdrop-blur-sm">
                <button
                    className="flex size-11 items-center justify-center rounded-sm transition-colors hover:bg-paper/10 disabled:opacity-30"
                    onClick={goBack}
                    disabled={page <= 1}
                    aria-label="Página anterior"
                >
                    <ChevronLeft className="size-6 text-white"/>
                </button>
                <span className="min-w-[80px] text-center font-mono text-sm text-paper">
                    {page} / {total}
                </span>
                <button
                    className="flex size-11 items-center justify-center rounded-sm transition-colors hover:bg-paper/10"
                    onClick={tryAdvance}
                    aria-label="Próxima página"
                >
                    <ChevronRight className="size-6 text-white"/>
                </button>
            </div>
        </div>
    );
}

// modo rolagem contínua

interface ScrollReaderProps {
    pages: ChapterManifestPage[];
    initialPage: number;
    version: number;
    dataSaver: boolean;
    filter: string;
    errorPages: Set<number>;
    jump: PageJump | null;
    onVisible: (page: number) => void;
    onEnd: () => void;
    onImageError: (index: number) => void;
    onRetry: (index: number) => void;
}

// limiares finos: numa página muito alta a razão visível é minúscula, e só assim
// o callback dispara perto do ponto em que ela passa de metade da tela
const VISIBILITY_THRESHOLDS = Array.from({length: 51}, (_, i) => i / 50);

function ScrollReader({pages, initialPage, version, dataSaver, filter, errorPages, jump, onVisible, onEnd, onImageError, onRetry}: ScrollReaderProps) {
    const scrollRef = useRef<HTMLDivElement>(null);
    const pageRefs = useRef<(HTMLDivElement | null)[]>([]);
    const endRef = useRef<HTMLDivElement | null>(null);
    const readyRef = useRef(false);
    // páginas que já chegaram perto da tela; continuam renderizadas depois disso
    const [near, setNear] = useState<Set<number>>(() => new Set());

    const onVisibleRef = useRef(onVisible);
    const onEndRef = useRef(onEnd);
    useEffect(() => {
        onVisibleRef.current = onVisible;
        onEndRef.current = onEnd;
    });

    // o espaço de cada página já está reservado, então o salto inicial cai no lugar certo
    useLayoutEffect(() => {
        if (initialPage > 1) pageRefs.current[initialPage - 1]?.scrollIntoView({behavior: "instant"});
        readyRef.current = true;
        // só na montagem
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    // Cada página se registra nos observers pela própria ref (attachPage). Observar a lista de
    // refs dentro do efeito falha quando o efeito roda antes de as refs serem religadas, o que o
    // StrictMode do React 19 faz em dev ao desmontar e remontar.
    const observers = useRef<{load: IntersectionObserver; visible: IntersectionObserver; end: IntersectionObserver} | null>(null);

    useEffect(() => {
        const root = scrollRef.current;
        if (!root) return;

        const load = new IntersectionObserver(
            (entries) => {
                const entered = entries.filter((e) => e.isIntersecting).map((e) => Number((e.target as HTMLElement).dataset.index));
                if (entered.length === 0) return;
                setNear((prev) => {
                    const next = new Set(prev);
                    entered.forEach((i) => next.add(i));
                    return next;
                });
            },
            {root, rootMargin: "200% 0px"}
        );

        const visible = new IntersectionObserver(
            (entries) => {
                if (!readyRef.current) return;
                for (const e of entries) {
                    const occupiesHalf = e.intersectionRatio >= 0.5 || e.intersectionRect.height >= root.clientHeight / 2;
                    if (e.isIntersecting && occupiesHalf) {
                        onVisibleRef.current(Number((e.target as HTMLElement).dataset.index) + 1);
                    }
                }
            },
            {root, threshold: VISIBILITY_THRESHOLDS}
        );

        const end = new IntersectionObserver(
            (entries) => {
                if (readyRef.current && entries[0].isIntersecting) onEndRef.current();
            },
            {root, threshold: 1.0}
        );

        observers.current = {load, visible, end};
        pageRefs.current.forEach((el) => {
            if (!el) return;
            load.observe(el);
            visible.observe(el);
        });
        if (endRef.current) end.observe(endRef.current);

        return () => {
            load.disconnect();
            visible.disconnect();
            end.disconnect();
            observers.current = null;
        };
    }, [pages.length]);

    const attachPage = useCallback((index: number, el: HTMLDivElement | null) => {
        pageRefs.current[index] = el;
        if (el && observers.current) {
            observers.current.load.observe(el);
            observers.current.visible.observe(el);
        }
    }, []);

    const attachEnd = useCallback((el: HTMLDivElement | null) => {
        endRef.current = el;
        if (el && observers.current) observers.current.end.observe(el);
    }, []);

    useEffect(() => {
        if (jump) pageRefs.current[jump.page - 1]?.scrollIntoView({behavior: "smooth"});
    }, [jump]);

    return (
        <div ref={scrollRef} className="flex-1 overflow-y-auto px-2 py-2">
            {pages.map((page, index) => {
                const src = pageSrc(page, dataSaver);
                return (
                    <div
                        key={index}
                        ref={(el) => attachPage(index, el)}
                        data-index={index}
                        className="mx-auto mb-1 w-full max-w-3xl"
                        style={{aspectRatio: aspectRatioOf(page)}}
                    >
                        {near.has(index) && (
                            <PageImage
                                key={`${version}-${src}`}
                                page={page}
                                pageNumber={index + 1}
                                src={src}
                                filter={filter}
                                errored={errorPages.has(index)}
                                onError={() => onImageError(index)}
                                onRetry={() => onRetry(index)}
                            />
                        )}
                    </div>
                );
            })}
            <div ref={attachEnd} className="h-px"/>
        </div>
    );
}

// overlay de conclusão

interface NextChapterInfo {
    id: string;
    name: string;
}

interface CompletionOverlayProps {
    manifest: ChapterManifest;
    state: ChapterReaderState;
    nextInfo: NextChapterInfo | null;
    detailsUrl: string;
}

function CompletionOverlay({manifest, state, nextInfo, detailsUrl}: CompletionOverlayProps) {
    const navigate = useNavigate();

    return (
        <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/80 backdrop-blur-sm">
            <div className="relative mx-4 flex w-full max-w-xs flex-col items-center gap-6 overflow-hidden rounded-lg border border-paper/10 bg-ink p-8 shadow-2xl">
                <span aria-hidden="true" className="screentone absolute inset-0 mask-[linear-gradient(to_bottom,#000,transparent_60%)]"/>
                <LogoMark className="relative h-12 w-auto text-paper" decorative/>
                <div className="relative space-y-1 text-center">
                    <p className="text-lg font-semibold text-paper">Capítulo concluído</p>
                    <p className="text-sm text-paper/50">
                        {[state.workTitle, chapterName(manifest)].filter(Boolean).join(" · ")}
                    </p>
                </div>
                <div className="relative flex w-full flex-col gap-3">
                    {manifest.nextChapterId && (
                        <button
                            className="h-11 w-full rounded-sm bg-paper text-sm font-semibold text-ink transition-colors hover:bg-paper/90"
                            onClick={() => navigate(`/leitor/capitulo/${manifest.nextChapterId}`, {state})}
                        >
                            {nextInfo ? `Próximo: ${nextInfo.name}` : "Próximo capítulo"}
                        </button>
                    )}
                    <button
                        className="h-11 w-full rounded-sm border border-paper/20 text-sm text-paper transition-colors hover:bg-paper/10"
                        onClick={() => navigate(detailsUrl)}
                    >
                        Voltar aos detalhes
                    </button>
                </div>
            </div>
        </div>
    );
}

// leitor, já com o manifesto carregado

interface ChapterReaderProps {
    chapterId: string;
    initialManifest: ChapterManifest;
    startPage: number;
    state: ChapterReaderState;
}

function ChapterReader({chapterId, initialManifest, startPage, state}: ChapterReaderProps) {
    const navigate = useNavigate();

    const [manifest, setManifest] = useState(initialManifest);
    // sobe a cada recarga do manifesto, para remontar as imagens mesmo se a URL repetir
    const [version, setVersion] = useState(0);
    const [errorPages, setErrorPages] = useState<Set<number>>(() => new Set());
    const total = manifest.pages.length;

    const [mode, setMode] = useState<ReadMode>(() => initialMode(initialManifest.workId, state.format));
    const [page, setPage] = useState(clampPage(startPage, initialManifest.pages.length));
    // página em que o leitor do modo atual foi montado
    const [anchorPage, setAnchorPage] = useState(page);
    const [jump, setJump] = useState<PageJump | null>(null);

    const [dataSaver, setDataSaver] = useState(initialDataSaver);
    const [brightness, setBrightness] = useState(100);
    const [contrast, setContrast] = useState(100);

    const [showControls, setShowControls] = useState(true);
    const [showSettings, setShowSettings] = useState(false);
    const [showCompletion, setShowCompletion] = useState(false);
    const [pageInputValue, setPageInputValue] = useState(String(page));
    const [pageInputFocused, setPageInputFocused] = useState(false);
    const [nextInfo, setNextInfo] = useState<NextChapterInfo | null>(null);

    const manifestRef = useRef(manifest);
    const pageRef = useRef(page);
    const showSettingsRef = useRef(false);
    const controlsTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
    const progressTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
    const pendingPage = useRef<number | null>(null);
    const lastQueuedPage = useRef(page);
    const completedRef = useRef(false);
    const reloadingRef = useRef(false);
    const lastReloadAt = useRef(0);
    const prefetchedFor = useRef<string | null>(null);
    const prefetchedImage = useRef<HTMLImageElement | null>(null);

    useEffect(() => {
        manifestRef.current = manifest;
        pageRef.current = page;
        showSettingsRef.current = showSettings;
    });

    const detailsUrl = state.backUrl ?? (state.workSlug ? `/biblioteca/${state.workSlug}` : "/");
    const filter = `brightness(${brightness}%) contrast(${contrast}%)`;

    // progresso

    const flushProgress = useCallback(() => {
        if (progressTimer.current) {
            clearTimeout(progressTimer.current);
            progressTimer.current = null;
        }
        const pending = pendingPage.current;
        if (pending == null) return;
        pendingPage.current = null;
        saveChapterProgress(chapterId, pending)
            .catch((e) => console.warn("Failed to save progress:", e));
    }, [chapterId]);

    // ao desmontar, o que estiver pendente é enviado na hora
    useEffect(() => () => flushProgress(), [flushProgress]);

    useEffect(() => {
        if (page === lastQueuedPage.current) return;
        lastQueuedPage.current = page;
        pendingPage.current = page;
        if (progressTimer.current) clearTimeout(progressTimer.current);
        progressTimer.current = setTimeout(flushProgress, PROGRESS_DEBOUNCE_MS);
    }, [page, flushProgress]);

    // URLs vencidas

    const reloadManifest = useCallback(async (failedIndex?: number, force = false) => {
        if (reloadingRef.current) return;
        const markFailed = () => {
            if (failedIndex != null) setErrorPages((prev) => new Set(prev).add(failedIndex));
        };
        if (!force && Date.now() - lastReloadAt.current < RELOAD_COOLDOWN_MS) {
            markFailed();
            return;
        }
        reloadingRef.current = true;
        lastReloadAt.current = Date.now();
        try {
            const fresh = await getChapterManifest(chapterId);
            setManifest(fresh);
            setVersion((v) => v + 1);
            setErrorPages(new Set());
        } catch {
            markFailed();
        } finally {
            reloadingRef.current = false;
        }
    }, [chapterId]);

    const handleImageError = useCallback((index: number) => {
        // várias imagens falham juntas quando a URL vence; a recarga em andamento resolve todas
        if (reloadingRef.current) return;
        void reloadManifest(index);
    }, [reloadManifest]);

    const handleRetry = useCallback((index: number) => {
        void reloadManifest(index, true);
    }, [reloadManifest]);

    // trocou de página depois de as URLs vencerem
    useEffect(() => {
        if (Date.parse(manifestRef.current.urlsExpireAt) < Date.now()) void reloadManifest();
    }, [page, reloadManifest]);

    // próximo capítulo: manifesto de prefetch (não conta leitura) e só a primeira imagem
    useEffect(() => {
        const nextId = manifest.nextChapterId;
        if (!nextId || page < total - NEXT_CHAPTER_PREFETCH_DISTANCE || prefetchedFor.current === nextId) return;
        prefetchedFor.current = nextId;
        getChapterManifest(nextId, true)
            .then((next) => {
                setNextInfo({id: nextId, name: chapterName(next)});
                const first = next.pages[0];
                if (first) {
                    const img = new Image();
                    img.src = pageSrc(first, dataSaver);
                    prefetchedImage.current = img;
                }
            })
            .catch(() => undefined);
    }, [page, total, manifest.nextChapterId, dataSaver]);

    // controles

    const showControlsTemporarily = useCallback(() => {
        setShowControls(true);
        if (controlsTimer.current) clearTimeout(controlsTimer.current);
        controlsTimer.current = setTimeout(() => {
            if (!showSettingsRef.current) setShowControls(false);
        }, CONTROLS_HIDE_MS);
    }, []);

    useEffect(() => {
        showControlsTemporarily();
        return () => {
            if (controlsTimer.current) clearTimeout(controlsTimer.current);
        };
    }, [showControlsTemporarily]);

    function toggleControls() {
        if (showControls) {
            setShowControls(false);
            if (controlsTimer.current) clearTimeout(controlsTimer.current);
        } else {
            showControlsTemporarily();
        }
    }

    useEffect(() => {
        if (!pageInputFocused) setPageInputValue(String(page));
    }, [page, pageInputFocused]);

    // ações

    function handleBack() {
        flushProgress();
        if (state.backUrl || state.workSlug) navigate(detailsUrl);
        else navigate(-1);
    }

    const handleComplete = useCallback(() => {
        if (completedRef.current) return;
        completedRef.current = true;
        // a última página é gravada por aqui; o pendente anterior não deve sobrescrevê-la
        if (progressTimer.current) clearTimeout(progressTimer.current);
        progressTimer.current = null;
        pendingPage.current = null;
        setShowCompletion(true);
        saveChapterProgress(chapterId, manifestRef.current.pages.length)
            .catch((e) => console.warn("Failed to save progress:", e));
    }, [chapterId]);

    // na rolagem, o sentinela só vale como fim se a última página já foi a atual
    const handleScrollEnd = useCallback(() => {
        if (pageRef.current >= manifestRef.current.pages.length) handleComplete();
    }, [handleComplete]);

    function jumpTo(target: number) {
        const clamped = clampPage(target, total);
        if (mode === "paged") setPage(clamped);
        else setJump((prev) => ({page: clamped, v: (prev?.v ?? 0) + 1}));
    }

    function handlePageInputConfirm() {
        const parsed = parseInt(pageInputValue, 10);
        if (!isNaN(parsed)) jumpTo(parsed);
    }

    function toggleMode() {
        const next: ReadMode = mode === "paged" ? "scroll" : "paged";
        writeStorage(`reader-mode:${manifest.workId}`, next);
        setAnchorPage(page);
        setJump(null);
        setMode(next);
    }

    function toggleDataSaver(enabled: boolean) {
        writeStorage("reader-data-saver", enabled ? "1" : "0");
        setDataSaver(enabled);
    }

    function goToChapter(id: string) {
        navigate(`/leitor/capitulo/${id}`, {state});
    }

    const title = manifest.title ? `${chapterName(manifest)} · ${manifest.title}` : chapterName(manifest);

    return (
        <div
            className="fixed inset-0 z-50 flex select-none flex-col bg-[#111]"
            onClick={toggleControls}
        >
            <div className="pointer-events-none absolute left-0 right-0 top-0 z-10">
                <div
                    className={`flex flex-col border-b border-paper/10 bg-ink/85 backdrop-blur-sm transition-all duration-200 ${showControls ? "pointer-events-auto opacity-100" : "pointer-events-none opacity-0"}`}
                    onClick={(e) => e.stopPropagation()}
                >
                    <div className="flex items-center justify-between px-3 py-2">
                        <button
                            className="flex min-h-11 min-w-0 items-center gap-2.5 px-1.5 text-paper/85 transition-colors hover:text-paper"
                            onClick={handleBack}
                            aria-label={`Voltar${state.workTitle ? ` para ${state.workTitle}` : ""}`}
                        >
                            <ArrowLeft className="size-5 shrink-0"/>
                            <span className="max-w-[45vw] truncate text-sm font-medium sm:max-w-[320px]">
                                {state.workTitle ?? "Voltar"}
                            </span>
                        </button>

                        <div className="flex items-center gap-1">
                            {manifest.previousChapterId && (
                                <button
                                    className="flex size-11 items-center justify-center rounded-sm text-paper/50 transition-colors hover:bg-paper/10 hover:text-paper"
                                    aria-label="Capítulo anterior"
                                    title="Capítulo anterior"
                                    onClick={() => {
                                        flushProgress();
                                        goToChapter(manifest.previousChapterId!);
                                    }}
                                >
                                    <SkipBack className="size-5"/>
                                </button>
                            )}
                            <button
                                className="flex size-11 items-center justify-center rounded-sm text-paper/70 transition-colors hover:bg-paper/10 hover:text-paper"
                                aria-label={mode === "paged" ? "Mudar para rolagem contínua" : "Mudar para página a página"}
                                title={mode === "paged" ? "Mudar para rolagem contínua" : "Mudar para página a página"}
                                onClick={toggleMode}
                            >
                                {mode === "paged" ? <AlignJustify className="size-5"/> : <BookOpen className="size-5"/>}
                            </button>
                            <button
                                className={`flex size-11 items-center justify-center rounded-sm transition-colors hover:bg-paper/10 ${showSettings ? "bg-paper/10 text-paper" : "text-paper/70 hover:text-paper"}`}
                                aria-label="Ajustes de leitura"
                                aria-expanded={showSettings}
                                onClick={() => setShowSettings((s) => !s)}
                            >
                                <SlidersHorizontal className="size-5"/>
                            </button>
                        </div>
                    </div>

                    <div className="px-4 pb-1">
                        <p className="truncate text-xs text-paper/70">{title}</p>
                        {manifest.scanlationGroup && (
                            <p className="truncate text-[11px] text-paper/40">Tradução: {manifest.scanlationGroup}</p>
                        )}
                    </div>

                    <div className="flex items-center gap-2 px-4 pb-2.5 pt-1">
                        <input
                            type="range"
                            aria-label="Página"
                            min={1}
                            max={total}
                            value={page}
                            onChange={(e) => jumpTo(Number(e.target.value))}
                            className="flex-1 cursor-pointer accent-shu"
                        />
                        <div className="flex shrink-0 items-center gap-1">
                            <input
                                type="number"
                                min={1}
                                max={total}
                                value={pageInputValue}
                                onChange={(e) => setPageInputValue(e.target.value)}
                                onFocus={() => setPageInputFocused(true)}
                                onBlur={() => {
                                    setPageInputFocused(false);
                                    handlePageInputConfirm();
                                }}
                                onKeyDown={(e) => {
                                    if (e.key === "Enter") (e.target as HTMLInputElement).blur();
                                }}
                                aria-label="Ir para a página"
                                className="w-12 rounded-sm bg-paper/10 px-1 py-1 text-center font-mono text-xs text-paper focus:bg-paper/20 focus:outline-none"
                            />
                            <span className="font-mono text-xs text-paper/40">/ {total}</span>
                        </div>
                    </div>
                </div>

                {showSettings && (
                    <div
                        className={`space-y-3 border-b border-paper/10 bg-ink/90 px-4 py-3 backdrop-blur-sm transition-all duration-200 ${showControls ? "pointer-events-auto opacity-100" : "pointer-events-none opacity-0"}`}
                        onClick={(e) => e.stopPropagation()}
                    >
                        <div className="flex items-center justify-between">
                            <span className="font-mono text-[11px] uppercase tracking-widest text-paper/60">Ajustes de leitura</span>
                            <button aria-label="Fechar ajustes" className="flex size-11 items-center justify-center" onClick={() => setShowSettings(false)}>
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
                        <label className="flex min-h-11 cursor-pointer items-center gap-3">
                            <input
                                type="checkbox"
                                checked={dataSaver}
                                onChange={(e) => toggleDataSaver(e.target.checked)}
                                className="size-5 accent-shu"
                            />
                            <span className="text-sm text-paper/80">Economia de dados</span>
                        </label>
                    </div>
                )}
            </div>

            {mode === "paged" ? (
                <PagedReader
                    key={`paged-${anchorPage}`}
                    pages={manifest.pages}
                    page={page}
                    version={version}
                    dataSaver={dataSaver}
                    filter={filter}
                    errorPages={errorPages}
                    onPageChange={setPage}
                    onComplete={handleComplete}
                    onImageError={handleImageError}
                    onRetry={handleRetry}
                    blocked={showCompletion}
                />
            ) : (
                <ScrollReader
                    key={`scroll-${anchorPage}`}
                    pages={manifest.pages}
                    initialPage={anchorPage}
                    version={version}
                    dataSaver={dataSaver}
                    filter={filter}
                    errorPages={errorPages}
                    jump={jump}
                    onVisible={setPage}
                    onEnd={handleScrollEnd}
                    onImageError={handleImageError}
                    onRetry={handleRetry}
                />
            )}

            <div
                aria-hidden="true"
                className="pointer-events-none absolute inset-x-0 bottom-0 z-10 h-0.5 bg-paper/10"
            >
                <div className="h-full bg-shu transition-[width] duration-200" style={{width: `${(page / total) * 100}%`}}/>
            </div>

            {showCompletion && (
                <CompletionOverlay manifest={manifest} state={state} nextInfo={nextInfo} detailsUrl={detailsUrl}/>
            )}
        </div>
    );
}

// carregamento inicial

export function ChapterReaderPage() {
    const {chapterId} = useParams<{ chapterId: string }>();
    const navigate = useNavigate();
    const location = useLocation();
    const state = (location.state ?? {}) as ChapterReaderState;

    const [loaded, setLoaded] = useState<{ manifest: ChapterManifest; startPage: number } | null>(null);
    const [loadError, setLoadError] = useState<string | null>(null);

    useEffect(() => {
        if (!chapterId) return;
        let cancelled = false;

        Promise.all([getChapterManifest(chapterId), getChapterProgress(chapterId).catch(() => null)])
            .then(([manifest, progress]) => {
                if (cancelled) return;
                // capítulo já concluído reabre do começo; o "Lido" continua registrado
                const startPage = progress && !progress.finished ? progress.currentPage ?? 1 : 1;
                if (manifest.pages.length === 0) {
                    setLoadError("Este capítulo não tem páginas.");
                    return;
                }
                setLoaded({manifest, startPage});
            })
            .catch(() => {
                if (!cancelled) setLoadError("Não foi possível carregar o capítulo. Verifique sua conexão.");
            });

        return () => {
            cancelled = true;
        };
    }, [chapterId]);

    if (loadError || !chapterId) {
        return (
            <div className="fixed inset-0 z-50 flex items-center justify-center bg-black">
                <div className="flex flex-col items-center gap-4 px-6 text-center">
                    <p className="text-paper/80">{loadError ?? "Capítulo não encontrado."}</p>
                    <button
                        className="min-h-11 px-3 text-sm text-paper/60 underline underline-offset-4 hover:text-paper"
                        onClick={() => navigate(state.backUrl ?? (state.workSlug ? `/biblioteca/${state.workSlug}` : -1 as never))}
                    >
                        Voltar
                    </button>
                </div>
            </div>
        );
    }

    if (!loaded) {
        return (
            <div className="fixed inset-0 z-50 flex items-center justify-center bg-black">
                <Loading className="text-paper" label="Carregando capítulo"/>
            </div>
        );
    }

    return <ChapterReader chapterId={chapterId} initialManifest={loaded.manifest} startPage={loaded.startPage} state={state}/>;
}
