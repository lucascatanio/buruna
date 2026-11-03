import {type FormEvent, useCallback, useEffect, useState} from "react";
import {toast} from "sonner";
import {ExternalLink, Loader2, RotateCcw, Search} from "lucide-react";
import {listTrackedWorks, requestSourcingRun, searchMetadata} from "@/api/sourcingApi";
import type {TrackedWork, WorkMetadataSearchResult} from "@/types/sourcing";
import {Badge} from "@/components/ui/badge";
import {Button} from "@/components/ui/button";
import {Input} from "@/components/ui/input";
import {PageHeader} from "@/components/PageHeader";
import {EmptyState} from "@/components/EmptyState";
import {Loading} from "@/components/Loading";
import {MetadataCover} from "@/components/sourcing/MetadataCover";
import {SiteImportDialog} from "@/components/sourcing/SiteImportDialog";
import {TrackedWorkItem} from "@/components/sourcing/TrackedWorkItem";
import {apiErrorMessage} from "@/lib/apiError";

const POLL_INTERVAL_MS = 10_000;

const RATING_LABELS: Record<string, string> = {
    SAFE: "Livre",
    SUGGESTIVE: "Sugestivo",
    EROTICA: "Erótico",
    PORNOGRAPHIC: "Pornográfico",
};

const STATUS_LABELS: Record<string, string> = {
    ONGOING: "Em andamento",
    COMPLETED: "Completo",
    HIATUS: "Hiato",
    CANCELLED: "Cancelado",
};

/**
 * Importação de sites pelo manga-downloader, com a obra vinda do AniList (ADR-54), e andamento
 * das obras acompanhadas, inclusive as antigas do MangaDex.
 */
export function SourcingPage() {
    const [query, setQuery] = useState("");
    const [results, setResults] = useState<WorkMetadataSearchResult[] | null>(null);
    const [searching, setSearching] = useState(false);
    const [importing, setImporting] = useState<WorkMetadataSearchResult | null>(null);
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
            setResults(await searchMetadata(q));
        } catch (err) {
            toast.error(apiErrorMessage(err, "A busca no AniList falhou."));
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


    return (
        <div className="mx-auto max-w-4xl space-y-10 px-4 py-8 md:px-6">
            {importing && <SiteImportDialog work={importing} onClose={() => setImporting(null)} onImported={loadTracked}/>}

            <section className="space-y-4">
                <PageHeader title="Importar de um site"
                            description="Busque a obra no AniList e informe o endereço da série num site que o manga-downloader baixa. Os capítulos ficam com o crédito do grupo que traduziu."/>
                <form onSubmit={handleSearch} className="flex gap-2">
                    <Input aria-label="Título no AniList" placeholder="Título da obra" value={query}
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
                                <MetadataCover url={result.coverThumbnailUrl} title={result.title}/>
                                <div className="min-w-0 flex-1 space-y-1">
                                    <p className="truncate font-medium">{result.title}</p>
                                    <div className="flex flex-wrap items-center gap-1.5">
                                        {result.year && <Badge variant="meta">{result.year}</Badge>}
                                        {result.status && <Badge variant="meta">{STATUS_LABELS[result.status] ?? result.status}</Badge>}
                                        {result.contentRating && result.contentRating !== "SAFE" && (
                                            <Badge variant="destructive">{RATING_LABELS[result.contentRating]}</Badge>
                                        )}
                                    </div>
                                    <a href={result.sourceUrl} target="_blank" rel="noopener noreferrer"
                                       className="inline-flex items-center gap-1 text-xs text-muted-foreground underline-offset-4 hover:underline">
                                        Ver no AniList <ExternalLink className="size-3"/>
                                    </a>
                                </div>
                                <Button variant="outline" onClick={() => setImporting(result)}>Importar</Button>
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
                        {tracked.map((t) => <TrackedWorkItem key={t.id} tracked={t} onChanged={loadTracked}/>)}
                    </ul>
                )}
            </section>
        </div>
    );
}
