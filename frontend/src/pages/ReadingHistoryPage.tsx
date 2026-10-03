import {useEffect, useState} from "react";
import {Link, useNavigate} from "react-router-dom";
import {getHistory} from "@/api/readingApi";
import type {HistoryEntry} from "@/types/reading";
import {Button} from "@/components/ui/button";
import {PageHeader} from "@/components/PageHeader";
import {EmptyState} from "@/components/EmptyState";
import {MangaCover} from "@/components/MangaCover";
import {toast} from "sonner";
import {ChevronRight} from "lucide-react";

function dayLabel(d: Date): string {
    const now = new Date();
    const yesterday = new Date(now);
    yesterday.setDate(now.getDate() - 1);
    if (d.toDateString() === now.toDateString()) return "Hoje";
    if (d.toDateString() === yesterday.toDateString()) return "Ontem";
    return d.toLocaleDateString("pt-BR", {day: "2-digit", month: "short", year: "numeric"});
}

/** Agrupa as entradas (já ordenadas da mais recente) por dia de leitura. */
function groupByDay(entries: HistoryEntry[]): {day: string; items: HistoryEntry[]}[] {
    const groups: {day: string; items: HistoryEntry[]}[] = [];
    for (const entry of entries) {
        const day = dayLabel(new Date(entry.readAt));
        const last = groups[groups.length - 1];
        if (last?.day === day) last.items.push(entry);
        else groups.push({day, items: [entry]});
    }
    return groups;
}

export function ReadingHistoryPage() {
    const navigate = useNavigate();
    const [entries, setEntries] = useState<HistoryEntry[]>([]);
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [loading, setLoading] = useState(true);
    const [loadingMore, setLoadingMore] = useState(false);

    async function fetchPage(pageNum: number, append = false) {
        if (pageNum === 0) setLoading(true); else setLoadingMore(true);
        try {
            const data = await getHistory(pageNum, 20);
            setEntries(prev => append ? [...prev, ...data.content] : data.content);
            setTotalPages(data.totalPages);
            setPage(data.number);
        } catch {
            toast.error("Erro ao carregar histórico");
        } finally {
            setLoading(false);
            setLoadingMore(false);
        }
    }

    useEffect(() => {
        fetchPage(0);
    }, []);

    return (
        <div className="max-w-3xl mx-auto px-4 md:px-8 py-8 md:py-10 flex flex-col gap-7">
            <PageHeader title="Histórico de leitura" description="Os volumes que você abriu, do mais recente."/>

            {loading ? (
                <div className="space-y-2">
                    {[...Array(5)].map((_, i) => (
                        <div key={i} className="h-[74px] bg-card screentone animate-pulse"/>
                    ))}
                </div>
            ) : entries.length === 0 ? (
                <EmptyState
                    title="Nenhuma leitura registrada ainda"
                    description="Os volumes que você abrir aparecem aqui, com a página onde parou."
                    action={<Button onClick={() => navigate("/biblioteca")}>Explorar a biblioteca</Button>}
                />
            ) : (
                <div className="flex flex-col gap-8">
                    {groupByDay(entries).map(({day, items}) => (
                        <section key={day} className="flex flex-col gap-2">
                            <h2 className="m-0 font-mono text-xs font-medium uppercase tracking-widest text-muted-foreground">{day}</h2>
                            <ul className="m-0 list-none divide-y border-y p-0">
                                {items.map((entry, idx) => (
                                    <li key={`${entry.volumeId}-${idx}`}>
                                        <Link
                                            to={`/leitor/${entry.volumeId}`}
                                            state={{
                                                mangaTitle: entry.mangaTitle,
                                                mangaId: entry.mangaId,
                                                volumeNumber: entry.volumeNumber,
                                                backUrl: "/historico",
                                            }}
                                            className="group flex items-center gap-4 py-3 pr-1 transition-colors hover:bg-muted/40"
                                        >
                                            <MangaCover title={entry.mangaTitle} coverUrl={entry.mangaCoverUrl} compact className="w-11 shrink-0 shadow-none"/>
                                            <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                                                <span className="truncate text-[15px] font-medium">{entry.mangaTitle}</span>
                                                <span className="font-mono text-xs text-muted-foreground">Vol. {String(entry.volumeNumber).padStart(2, "0")}</span>
                                            </div>
                                            <span className="shrink-0 font-mono text-xs text-muted-foreground">
                                                {new Date(entry.readAt).toLocaleTimeString("pt-BR", {hour: "2-digit", minute: "2-digit"})}
                                            </span>
                                            <ChevronRight aria-hidden="true" className="size-4 shrink-0 text-muted-foreground transition-transform group-hover:translate-x-0.5"/>
                                        </Link>
                                    </li>
                                ))}
                            </ul>
                        </section>
                    ))}

                    {page < totalPages - 1 && (
                        <Button
                            variant="outline"
                            className="h-11 self-center px-6"
                            onClick={() => fetchPage(page + 1, true)}
                            disabled={loadingMore}
                        >
                            {loadingMore ? "Carregando…" : "Carregar mais"}
                        </Button>
                    )}
                </div>
            )}
        </div>
    );
}