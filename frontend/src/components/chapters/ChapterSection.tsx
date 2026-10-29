import {useCallback, useEffect, useMemo, useState} from "react";
import {useNavigate} from "react-router-dom";
import {toast} from "sonner";
import {AlertCircle, BookOpen, Check, Loader2, RotateCcw, Trash2, Upload} from "lucide-react";
import {
    deleteChapter,
    listChapterLanguages,
    listChapters,
    retryChapter,
    type ChapterScope,
} from "@/api/chapterApi";
import {getWorkChapterProgress} from "@/api/readingApi";
import type {ChapterListItem} from "@/types/chapter";
import type {ProgressResponse} from "@/types/reading";
import {Button} from "@/components/ui/button";
import {EmptyState} from "@/components/EmptyState";
import {Macron} from "@/components/Macron";
import {chapterName, languageName} from "@/lib/chapterLabel";
import {apiErrorMessage} from "@/lib/apiError";
import {ChapterUploadDialog} from "./ChapterUploadDialog";

const DEFAULT_LANGUAGE = "pt-BR";
const INITIAL_VISIBLE = 50;
const POLL_INTERVAL_MS = 5000;

interface ChapterSectionProps {
    scope: ChapterScope;
    workId: string;
    workTitle: string;
    workSlug?: string;
    format?: string;
    backUrl: string;
    /** Dono da obra (ou ADMIN no catálogo): envia capítulos e vê os não publicados. */
    canManage: boolean;
    /** Substitui o contêiner padrão (largura e margens da página pública). */
    className?: string;
}

function storedLanguage(workId: string): string | null {
    try {
        return localStorage.getItem(`chapter-lang:${workId}`);
    } catch {
        return null;
    }
}

function rememberLanguage(workId: string, language: string) {
    try {
        localStorage.setItem(`chapter-lang:${workId}`, language);
    } catch {
        // navegação privada: só não lembra
    }
}

/** Idioma escolhido: o último da pessoa nesta obra, senão pt-BR, senão o primeiro que existir. */
function pickLanguage(workId: string, available: string[]): string | null {
    if (available.length === 0) return null;
    const stored = storedLanguage(workId);
    if (stored && available.includes(stored)) return stored;
    if (available.includes(DEFAULT_LANGUAGE)) return DEFAULT_LANGUAGE;
    return available[0];
}

/** Andamento em %: pelo percentual no EPUB, pela página no resto. */
function progressPercent(p: ProgressResponse | undefined): number | undefined {
    if (!p) return undefined;
    if (p.percent != null) return Math.round(p.percent * 100);
    return p.totalPages ? Math.round((p.currentPage / p.totalPages) * 100) : undefined;
}

export function ChapterSection({scope, workId, workTitle, workSlug, format, backUrl, canManage, className}: ChapterSectionProps) {
    const navigate = useNavigate();
    // num livro, cada item é uma edição inteira (PDF ou EPUB), não um capítulo
    const isBook = format === "LIVRO";
    const [languages, setLanguages] = useState<string[]>([]);
    const [language, setLanguage] = useState<string | null>(null);
    // dono: todos os capítulos (de todos os idiomas, com status); leitor: só o idioma escolhido
    const [chapters, setChapters] = useState<ChapterListItem[]>([]);
    const [progress, setProgress] = useState<Record<string, ProgressResponse>>({});
    const [recentProgress, setRecentProgress] = useState<ProgressResponse[]>([]);
    const [loading, setLoading] = useState(true);
    const [showAll, setShowAll] = useState(false);
    const [showUpload, setShowUpload] = useState(false);
    const [busyId, setBusyId] = useState<string | null>(null);

    const loadManaged = useCallback(async () => {
        const all = await listChapters(scope, workId);
        setChapters(all);
        const langs = [...new Set(all.map((c) => c.language))].sort();
        setLanguages(langs);
        setLanguage((current) => current && langs.includes(current) ? current : pickLanguage(workId, langs));
    }, [scope, workId]);

    const loadForReader = useCallback(async () => {
        const langs = (await listChapterLanguages(workId)).map((l) => l.language);
        setLanguages(langs);
        const chosen = pickLanguage(workId, langs);
        setLanguage(chosen);
        setChapters(chosen ? await listChapters(scope, workId, chosen) : []);
    }, [scope, workId]);

    const loadProgress = useCallback(async () => {
        const entries = await getWorkChapterProgress(workId);
        setRecentProgress(entries);
        setProgress(Object.fromEntries(entries.filter((p) => p.chapterId).map((p) => [p.chapterId!, p])));
    }, [workId]);

    useEffect(() => {
        let cancelled = false;
        setLoading(true);
        Promise.all([canManage ? loadManaged() : loadForReader(), loadProgress()])
            .catch((e) => {
                if (!cancelled) toast.error(apiErrorMessage(e, "Não foi possível carregar os capítulos."));
            })
            .finally(() => {
                if (!cancelled) setLoading(false);
            });
        return () => {
            cancelled = true;
        };
    }, [canManage, loadManaged, loadForReader, loadProgress]);

    // capítulo enviado fica "processando" até o Job terminar: atualiza sozinho enquanto houver algum
    const hasProcessing = chapters.some((c) => c.status === "PROCESSING");
    useEffect(() => {
        if (!canManage || !hasProcessing) return;
        const timer = setInterval(() => {
            loadManaged().catch((e) => console.warn("[ChapterSection] falha ao atualizar capítulos:", e));
        }, POLL_INTERVAL_MS);
        return () => clearInterval(timer);
    }, [canManage, hasProcessing, loadManaged]);

    async function changeLanguage(next: string) {
        setLanguage(next);
        setShowAll(false);
        rememberLanguage(workId, next);
        if (!canManage) {
            try {
                setChapters(await listChapters(scope, workId, next));
            } catch (e) {
                toast.error(apiErrorMessage(e, "Não foi possível carregar os capítulos."));
            }
        }
    }

    const visibleChapters = useMemo(
        () => chapters.filter((c) => c.language === language),
        [chapters, language],
    );
    const readable = visibleChapters.filter((c) => c.status === "PUBLISHED");

    // "Continuar": o capítulo lido mais recentemente neste idioma; se já terminou, o seguinte.
    // No livro, a edição que a pessoa leu por último, senão a primeira.
    const continueTarget = useMemo(() => {
        if (readable.length === 0) return null;
        const recent = recentProgress.find((p) => readable.some((c) => c.id === p.chapterId));
        if (!recent) return {chapter: readable[0], label: "Começar a ler"};
        const index = readable.findIndex((c) => c.id === recent.chapterId);
        if (isBook) {
            const percent = progressPercent(recent);
            const label = recent.finished ? "Ler de novo" : percent ? `Continuar · ${percent}%` : "Continuar";
            return {chapter: readable[index], label};
        }
        if (!recent.finished) return {chapter: readable[index], label: `Continuar · ${chapterName(readable[index])}`};
        const next = readable[index + 1];
        // tudo lido: como nos volumes, o botão volta ao começo em vez de sumir
        return next ? {chapter: next, label: `Ler · ${chapterName(next)}`} : {chapter: readable[0], label: "Começar a ler"};
    }, [readable, recentProgress, isBook]);

    function openChapter(chapter: ChapterListItem) {
        navigate(`/leitor/capitulo/${chapter.id}`, {
            state: {workId, workTitle, workSlug, format, backUrl},
        });
    }

    async function handleRetry(chapter: ChapterListItem) {
        setBusyId(chapter.id);
        try {
            await retryChapter(scope, workId, chapter.id);
            await loadManaged();
        } catch (e) {
            toast.error(apiErrorMessage(e, "Não foi possível processar de novo."));
        } finally {
            setBusyId(null);
        }
    }

    async function handleDelete(chapter: ChapterListItem) {
        const removed = isBook ? "O arquivo será removido." : "As páginas serão removidas.";
        if (!confirm(`Apagar ${chapterName(chapter)}? ${removed}`)) return;
        setBusyId(chapter.id);
        try {
            await deleteChapter(scope, workId, chapter.id);
            await loadManaged();
            toast.success(isBook ? "Edição apagada." : "Capítulo apagado.");
        } catch (e) {
            toast.error(apiErrorMessage(e, isBook ? "Não foi possível apagar a edição." : "Não foi possível apagar o capítulo."));
        } finally {
            setBusyId(null);
        }
    }

    const shown = showAll ? visibleChapters : visibleChapters.slice(0, INITIAL_VISIBLE);

    return (
        <section className={className ?? "max-w-7xl w-full mx-auto px-4 md:px-8 pt-9 flex flex-col gap-5"}>
            {showUpload && (
                <ChapterUploadDialog
                    scope={scope}
                    workId={workId}
                    workFormat={format}
                    defaultLanguage={language ?? DEFAULT_LANGUAGE}
                    onClose={() => setShowUpload(false)}
                    onUploaded={() => loadManaged().catch(() => undefined)}
                />
            )}

            <div className="flex flex-wrap items-end justify-between gap-4">
                <div className="flex flex-col gap-2">
                    <Macron className="w-6"/>
                    <h2 className="m-0 text-[22px] font-semibold tracking-[-0.02em]">
                        {isBook ? "Edições" : "Capítulos"} <span className="font-mono text-sm font-medium text-muted-foreground">{readable.length}</span>
                    </h2>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                    {languages.length > 1 && (
                        <label className="flex items-center gap-2 text-sm text-muted-foreground">
                            <span>Idioma</span>
                            <select
                                value={language ?? ""}
                                onChange={(e) => changeLanguage(e.target.value)}
                                className="h-10 rounded-md border bg-transparent px-2 text-sm text-foreground"
                            >
                                {languages.map((l) => <option key={l} value={l}>{languageName(l)}</option>)}
                            </select>
                        </label>
                    )}
                    {languages.length === 1 && language && (
                        <span className="text-sm text-muted-foreground">{languageName(language)}</span>
                    )}
                    {continueTarget && (
                        <Button className="h-10" onClick={() => openChapter(continueTarget.chapter)}>
                            <BookOpen className="size-4"/>
                            {continueTarget.label}
                        </Button>
                    )}
                    {canManage && (
                        <Button variant="outline" className="h-10" onClick={() => setShowUpload(true)}>
                            <Upload className="size-4"/>
                            {isBook ? "Adicionar edição" : "Adicionar capítulo"}
                        </Button>
                    )}
                </div>
            </div>

            {loading ? (
                <div className="flex items-center gap-2 text-sm text-muted-foreground">
                    <Loader2 className="size-4 animate-spin"/> {isBook ? "Carregando edições…" : "Carregando capítulos…"}
                </div>
            ) : visibleChapters.length === 0 ? (
                isBook
                    ? <EmptyState title="Nenhuma edição ainda" description="As edições aparecem aqui assim que forem publicadas."/>
                    : <EmptyState title="Nenhum capítulo ainda" description="Os capítulos aparecem aqui assim que forem publicados."/>
            ) : (
                <ul className="flex flex-col divide-y rounded-lg border bg-card">
                    {shown.map((chapter) => {
                        const p = progress[chapter.id];
                        const finished = p?.finished ?? false;
                        const percent = progressPercent(p);
                        const published = chapter.status === "PUBLISHED";
                        return (
                            <li key={chapter.id} className="flex items-center gap-3 px-4 py-3">
                                <button
                                    className="flex min-h-11 min-w-0 flex-1 flex-col items-start text-left disabled:cursor-default"
                                    onClick={() => published && openChapter(chapter)}
                                    disabled={!published}
                                >
                                    <span className={`flex items-center gap-2 text-[15px] ${finished ? "text-muted-foreground" : ""}`}>
                                        <span className="font-medium">{chapterName(chapter)}</span>
                                        {chapter.fileFormat && (
                                            <span className="font-mono text-[11px] text-muted-foreground">{chapter.fileFormat}</span>
                                        )}
                                        {chapter.title && <span className="truncate text-muted-foreground">· {chapter.title}</span>}
                                    </span>
                                    {chapter.scanlationGroup && (
                                        <span className="text-xs text-muted-foreground">
                                            {isBook ? `Tradução: ${chapter.scanlationGroup}` : chapter.scanlationGroup}
                                        </span>
                                    )}
                                    {chapter.status === "FAILED" && (
                                        <span className="mt-1 flex items-center gap-1 text-xs text-destructive">
                                            <AlertCircle className="size-3.5"/>
                                            {chapter.failureReason ?? "O processamento falhou."}
                                        </span>
                                    )}
                                </button>

                                {chapter.status === "PROCESSING" && (
                                    <span className="flex items-center gap-1.5 font-mono text-[11px] text-muted-foreground">
                                        <Loader2 className="size-3.5 animate-spin"/> processando
                                    </span>
                                )}
                                {chapter.status === "UNPUBLISHED" && (
                                    <span className="font-mono text-[11px] text-muted-foreground">fora do ar</span>
                                )}
                                {published && finished && (
                                    <span className="flex items-center gap-1 font-mono text-[11px] text-muted-foreground">
                                        <Check className="size-3.5"/> lido
                                    </span>
                                )}
                                {published && !finished && percent !== undefined && (
                                    <span className="font-mono text-[11px] text-shu">{percent}%</span>
                                )}

                                {canManage && chapter.status === "FAILED" && (
                                    <Button variant="outline" size="icon" className="size-11"
                                            aria-label={`Processar ${chapterName(chapter)} de novo`}
                                            disabled={busyId === chapter.id}
                                            onClick={() => handleRetry(chapter)}>
                                        <RotateCcw className="size-4"/>
                                    </Button>
                                )}
                                {canManage && (
                                    <Button variant="ghost" size="icon" className="size-11"
                                            aria-label={`Apagar ${chapterName(chapter)}`}
                                            disabled={busyId === chapter.id}
                                            onClick={() => handleDelete(chapter)}>
                                        <Trash2 className="size-4"/>
                                    </Button>
                                )}
                            </li>
                        );
                    })}
                </ul>
            )}

            {!showAll && visibleChapters.length > INITIAL_VISIBLE && (
                <Button variant="outline" className="h-10 self-start" onClick={() => setShowAll(true)}>
                    Mostrar todos ({visibleChapters.length})
                </Button>
            )}
        </section>
    );
}
