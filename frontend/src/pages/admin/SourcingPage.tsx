import {type FormEvent, useCallback, useEffect, useState} from "react";
import {Link} from "react-router-dom";
import {toast} from "sonner";
import {AlertCircle, ExternalLink, Loader2, RotateCcw, Search} from "lucide-react";
import {getCoverThumbnail, listTrackedWorks, requestSourcingRun, searchSource} from "@/api/sourcingApi";
import type {SourceSearchResult, TrackedWork} from "@/types/sourcing";
import {Badge} from "@/components/ui/badge";
import {Button} from "@/components/ui/button";
import {Input} from "@/components/ui/input";
import {PageHeader} from "@/components/PageHeader";
import {EmptyState} from "@/components/EmptyState";
import {Loading} from "@/components/Loading";
import {ImportDialog} from "@/components/sourcing/ImportDialog";
import {languageName} from "@/lib/chapterLabel";
import {apiErrorMessage} from "@/lib/apiError";

const POLL_INTERVAL_MS = 10_000;

const RATING_LABELS: Record<string, string> = {
    SAFE: "Livre",
    SUGGESTIVE: "Sugestivo",
    EROTICA: "Erótico",
    PORNOGRAPHIC: "Pornográfico",
};

function Cover({url, title}: { url: string | null; title: string }) {
    const [src, setSrc] = useState<string | null>(null);

    useEffect(() => {
        if (!url) return;
        let created: string | null = null;
        let cancelled = false;
        getCoverThumbnail(url)
            .then((objectUrl) => {
                created = objectUrl;
                if (!cancelled) setSrc(objectUrl);
            })
            .catch(() => undefined);
        return () => {
            cancelled = true;
            if (created) URL.revokeObjectURL(created);
        };
    }, [url]);

    return src
        ? <img src={src} alt={`Capa de ${title}`} className="aspect-[2/3] w-14 shrink-0 rounded border object-cover"/>
        : <div aria-hidden="true" className="aspect-[2/3] w-14 shrink-0 rounded border bg-muted"/>;
}

function plural(count: number, one: string, many: string): string {
    return `${count} ${count === 1 ? one : many}`;
}

function formatDate(value: string | null): string {
    return value ? new Date(value).toLocaleString("pt-BR", {dateStyle: "short", timeStyle: "short"}) : "nunca";
}

/** Importação do MangaDex e andamento das obras acompanhadas (ADR-46). */
export function SourcingPage() {
    const [query, setQuery] = useState("");
    const [results, setResults] = useState<SourceSearchResult[] | null>(null);
    const [searching, setSearching] = useState(false);
    const [importing, setImporting] = useState<SourceSearchResult | null>(null);
    const [tracked, setTracked] = useState<TrackedWork[] | null>(null);
    const [resuming, setResuming] = useState(false);

    const loadTracked = useCallback(() => {
        listTrackedWorks()
            .then(setTracked)
            .catch((e) => toast.error(apiErrorMessage(e, "Não foi possível carregar as obras acompanhadas.")));
    }, []);

    useEffect(loadTracked, [loadTracked]);

    // enquanto houver capítulo para baixar, a lista se atualiza sozinha
    const hasPending = (tracked ?? []).some((t) => (t.chapterCounts.PENDING ?? 0) > 0 || !t.lastCheckedAt);
    useEffect(() => {
        if (!hasPending) return;
        const timer = setInterval(loadTracked, POLL_INTERVAL_MS);
        return () => clearInterval(timer);
    }, [hasPending, loadTracked]);

    async function handleSearch(e: FormEvent) {
        e.preventDefault();
        const q = query.trim();
        if (!q) return;
        setSearching(true);
        try {
            setResults(await searchSource(q));
        } catch (err) {
            toast.error(apiErrorMessage(err, "A busca no MangaDex falhou."));
        } finally {
            setSearching(false);
        }
    }

    async function handleResume() {
        setResuming(true);
        try {
            await requestSourcingRun();
            toast.success("Downloads retomados.");
            loadTracked();
        } catch (err) {
            toast.error(apiErrorMessage(err, "Não foi possível retomar os downloads."));
        } finally {
            setResuming(false);
        }
    }

    function handleImported() {
        loadTracked();
        setResults((current) => current?.map((r) => r.externalId === importing?.externalId ? {...r, tracked: true} : r) ?? null);
    }

    return (
        <div className="mx-auto max-w-4xl space-y-10 px-4 py-8 md:px-6">
            {importing && <ImportDialog work={importing} onClose={() => setImporting(null)} onImported={handleImported}/>}

            <section className="space-y-4">
                <PageHeader title="Importar do MangaDex"
                            description="Os capítulos ficam com o crédito do grupo que traduziu, e a obra, com o link do MangaDex."/>
                <form onSubmit={handleSearch} className="flex gap-2">
                    <Input aria-label="Título no MangaDex" placeholder="Título da obra" value={query}
                           onChange={(e) => setQuery(e.target.value)} maxLength={200}/>
                    <Button type="submit" disabled={searching || !query.trim()}>
                        {searching ? <Loader2 className="size-4 animate-spin"/> : <Search className="size-4"/>}
                        Buscar
                    </Button>
                </form>

                {results && results.length === 0 && (
                    <EmptyState title="Nada encontrado" description="Tente outro título, ou o nome em inglês ou romanizado."/>
                )}
                {results && results.length > 0 && (
                    <ul className="divide-y rounded-lg border bg-card">
                        {results.map((result) => (
                            <li key={result.externalId} className="flex items-center gap-4 p-3">
                                <Cover url={result.coverThumbnailUrl} title={result.title}/>
                                <div className="min-w-0 flex-1 space-y-1">
                                    <p className="truncate font-medium">{result.title}</p>
                                    <div className="flex flex-wrap items-center gap-1.5">
                                        {result.year && <Badge variant="meta">{result.year}</Badge>}
                                        {result.contentRating && (
                                            <Badge variant={result.contentRating === "SAFE" ? "meta" : "destructive"}>
                                                {RATING_LABELS[result.contentRating]}
                                            </Badge>
                                        )}
                                        <span className="text-xs text-muted-foreground">
                                            {result.availableLanguages.includes("pt-BR") ? "Tem pt-BR · " : ""}
                                            {result.availableLanguages.length} idiomas
                                        </span>
                                    </div>
                                    <a href={result.sourceUrl} target="_blank" rel="noopener noreferrer"
                                       className="inline-flex items-center gap-1 text-xs text-muted-foreground underline-offset-4 hover:underline">
                                        Ver no MangaDex <ExternalLink className="size-3"/>
                                    </a>
                                </div>
                                {result.tracked
                                    ? <span className="font-mono text-[11px] text-muted-foreground">já acompanhada</span>
                                    : <Button variant="outline" onClick={() => setImporting(result)}>Importar</Button>}
                            </li>
                        ))}
                    </ul>
                )}
            </section>

            <section className="space-y-4">
                <div className="flex flex-wrap items-end justify-between gap-3">
                    <h2 className="m-0 text-[22px] font-semibold tracking-[-0.02em]">Obras acompanhadas</h2>
                    <Button variant="outline" onClick={handleResume} disabled={resuming}>
                        <RotateCcw className="size-4"/>
                        {resuming ? "Retomando…" : "Retomar downloads"}
                    </Button>
                </div>

                {tracked === null && <Loading/>}
                {tracked?.length === 0 && (
                    <EmptyState title="Nenhuma obra acompanhada" description="As obras importadas aparecem aqui."/>
                )}
                {tracked && tracked.length > 0 && (
                    <ul className="divide-y rounded-lg border bg-card">
                        {tracked.map((t) => {
                            const counts = t.chapterCounts;
                            const pending = counts.PENDING ?? 0;
                            const versions = Object.values(counts).reduce((sum, n) => sum + (n ?? 0), 0);
                            return (
                                <li key={t.id} className="space-y-1.5 p-4">
                                    <div className="flex flex-wrap items-baseline justify-between gap-2">
                                        <Link to={`/biblioteca/${t.workSlug}`} className="font-medium hover:underline">{t.workTitle}</Link>
                                        <a href={t.sourceUrl} target="_blank" rel="noopener noreferrer"
                                           className="inline-flex items-center gap-1 text-xs text-muted-foreground hover:underline">
                                            MangaDex <ExternalLink className="size-3"/>
                                        </a>
                                    </div>
                                    <p className="text-sm text-muted-foreground">
                                        {t.languages.map((l) => languageName(l.language)
                                            + (l.preferredGroupName ? ` (${l.preferredGroupName})` : "")).join(" · ")}
                                    </p>
                                    <p className="flex flex-wrap items-center gap-x-3 gap-y-1 font-mono text-[11px] text-muted-foreground">
                                        {t.lastCheckedAt && versions === 0
                                            ? <span>nenhum capítulo disponível na fonte</span>
                                            : <span>{plural(counts.READY ?? 0, "baixado", "baixados")}</span>}
                                        {pending > 0 && (
                                            <span className="flex items-center gap-1 text-foreground">
                                                <Loader2 className="size-3 animate-spin"/> {pending} na fila
                                            </span>
                                        )}
                                        {(counts.FAILED ?? 0) > 0 && <span className="text-destructive">{counts.FAILED} com falha</span>}
                                        <span>{plural(counts.IGNORED ?? 0, "versão de outro grupo", "versões de outros grupos")}</span>
                                        <span>verificada {formatDate(t.lastCheckedAt)}</span>
                                    </p>
                                    {t.lastError && (
                                        <p className="flex items-center gap-1 text-xs text-destructive">
                                            <AlertCircle className="size-3.5"/> {t.lastError}
                                        </p>
                                    )}
                                </li>
                            );
                        })}
                    </ul>
                )}
            </section>
        </div>
    );
}
