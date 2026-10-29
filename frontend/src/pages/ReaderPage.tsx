import {useEffect, useRef, useState} from "react";
import {useParams, useNavigate, useLocation} from "react-router-dom";
import {getWork} from "@/api/workApi";
import {getVolumeProgress, getVolumeUrl, saveProgress as saveProgressApi} from "@/api/readingApi";
import {getSignedUrl, setSignedUrl} from "@/lib/signedUrlCache";
import {Loading} from "@/components/Loading";
import {LogoMark} from "@/components/Logo";
import {PdfDocumentReader} from "@/components/reader/PdfDocumentReader";

interface ReaderState {
    workId?: string;
    workTitle?: string;
    workSlug?: string;
    volumeNumber?: number;
    backUrl?: string;
}

// salva progresso com debounce de 1.5s
let progressTimer: ReturnType<typeof setTimeout> | null = null;

function saveProgress(volumeId: string, page: number, totalPages: number) {
    if (progressTimer) clearTimeout(progressTimer);
    progressTimer = setTimeout(() => {
        progressTimer = null;
        saveProgressApi(volumeId, page, totalPages)
            .catch((e) => console.warn("Failed to save progress:", e));
    }, 1500);
}

interface CompletionOverlayProps {
    state: ReaderState;
}

function CompletionOverlay({state}: CompletionOverlayProps) {
    const navigate = useNavigate();
    // undefined = loading, null = no next volume
    const [nextVol, setNextVol] = useState<{id: string; volumeNumber: number} | null | undefined>(undefined);

    useEffect(() => {
        if (!state.workSlug) {
            setNextVol(null);
            return;
        }
        getWork(state.workSlug)
            .then((data) => {
                const vols: {id: string; volumeNumber: number}[] = data.volumes ?? [];
                const sorted = [...vols].sort((a, b) => a.volumeNumber - b.volumeNumber);
                const next = sorted.find(v => v.volumeNumber > (state.volumeNumber ?? 0)) ?? null;
                setNextVol(next);
            })
            .catch(() => setNextVol(null));
    }, [state.workSlug, state.volumeNumber]);

    return (
        <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/80 backdrop-blur-sm">
            <div className="relative mx-4 flex w-full max-w-xs flex-col items-center gap-6 overflow-hidden rounded-lg border border-paper/10 bg-ink p-8 shadow-2xl">
                <span aria-hidden="true" className="screentone absolute inset-0 mask-[linear-gradient(to_bottom,#000,transparent_60%)]"/>
                <LogoMark className="relative h-12 w-auto text-paper" decorative/>
                <div className="relative text-center space-y-1">
                    <p className="text-paper text-lg font-semibold">Volume concluído</p>
                    {(state.workTitle || state.volumeNumber != null) && (
                        <p className="text-paper/50 text-sm">
                            {[state.workTitle, state.volumeNumber != null && `Vol. ${state.volumeNumber}`]
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
                                    workId: state.workId,
                                    workTitle: state.workTitle,
                                    workSlug: state.workSlug,
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

    const [opened, setOpened] = useState<{url: string; startPage: number} | null>(null);
    const [loadError, setLoadError] = useState<string | null>(null);
    const positionRef = useRef<{page: number; total: number} | null>(null);

    useEffect(() => {
        if (!volumeId) return;
        let cancelled = false;

        async function init() {
            const cached = getSignedUrl(volumeId!);
            const [urlRes, progressRes] = await Promise.allSettled([
                cached ? Promise.resolve(cached) : getVolumeUrl(volumeId!).then(r => r.url),
                getVolumeProgress(volumeId!),
            ]);
            if (cancelled) return;

            if (urlRes.status === "rejected") {
                setLoadError("Não foi possível carregar o volume. Verifique sua conexão.");
                return;
            }
            if (!cached) setSignedUrl(volumeId!, urlRes.value);

            let startPage = 1;
            // Volume já concluído reabre do começo; o "Lido" continua registrado.
            if (progressRes.status === "fulfilled" && progressRes.value && !progressRes.value.finished) {
                startPage = progressRes.value.currentPage ?? 1;
            }
            setOpened({url: urlRes.value, startPage});
        }

        init();
        return () => {
            cancelled = true;
        };
    }, [volumeId]);

    async function fetchFreshUrl(): Promise<string> {
        const {url} = await getVolumeUrl(volumeId!);
        setSignedUrl(volumeId!, url);
        return url;
    }

    function handlePageChange(page: number, total: number) {
        positionRef.current = {page, total};
        saveProgress(volumeId!, page, total);
    }

    function handleBack() {
        const position = positionRef.current;
        if (progressTimer && position) {
            clearTimeout(progressTimer);
            progressTimer = null;
            saveProgressApi(volumeId!, position.page, position.total)
                .catch((e) => console.warn("Failed to save progress:", e));
        }
        navigate(state.backUrl ?? -1 as any);
    }

    function handleVolumeComplete(total: number) {
        if (progressTimer) {
            clearTimeout(progressTimer);
            progressTimer = null;
        }
        // Grava a última página: o volume fica como lido. Reabrir começa da página 1.
        saveProgressApi(volumeId!, total, total)
            .catch((e) => console.warn("Failed to save progress:", e));
    }

    if (loadError) {
        return (
            <div className="fixed inset-0 bg-black flex items-center justify-center z-50">
                <div className="flex flex-col items-center gap-4 text-center px-6">
                    <p className="text-paper/80">{loadError}</p>
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

    if (!opened) {
        return (
            <div className="fixed inset-0 bg-black flex items-center justify-center z-50">
                <Loading className="text-paper" label="Carregando volume"/>
            </div>
        );
    }

    return (
        <PdfDocumentReader
            initialUrl={opened.url}
            fetchFreshUrl={fetchFreshUrl}
            initialPage={opened.startPage}
            title={state.workTitle}
            badge={state.volumeNumber != null ? `VOL. ${String(state.volumeNumber).padStart(2, "0")}` : undefined}
            loadingLabel="Carregando volume"
            onPageChange={handlePageChange}
            onComplete={handleVolumeComplete}
            onBack={handleBack}
            completion={<CompletionOverlay state={state}/>}
        />
    );
}
