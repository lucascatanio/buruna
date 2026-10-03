import {useEffect, useState} from "react";
import {useNavigate} from "react-router-dom";
import {getHistory} from "@/api/readingApi";
import type {HistoryEntry} from "@/types/reading";
import {Button} from "@/components/ui/button";
import {Card, CardContent} from "@/components/ui/card";
import {PageHeader} from "@/components/PageHeader";
import {EmptyState} from "@/components/EmptyState";
import {toast} from "sonner";
import {BookOpen, ChevronRight} from "lucide-react";

function formatDate(iso: string): string {
    const d = new Date(iso);
    const now = new Date();
    const isToday = d.toDateString() === now.toDateString();
    const yesterday = new Date(now);
    yesterday.setDate(now.getDate() - 1);
    const isYesterday = d.toDateString() === yesterday.toDateString();

    const time = d.toLocaleTimeString("pt-BR", {hour: "2-digit", minute: "2-digit"});

    if (isToday) return `Hoje, ${time}`;
    if (isYesterday) return `Ontem, ${time}`;
    return d.toLocaleDateString("pt-BR", {day: "2-digit", month: "short", year: "numeric"}) + `, ${time}`;
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
        <div className="max-w-2xl mx-auto px-4 md:px-6 py-8 space-y-6">
            <PageHeader title="Histórico de leitura" description="Os volumes que você abriu, do mais recente."/>

            {loading ? (
                <div className="space-y-2">
                    {[...Array(5)].map((_, i) => (
                        <div key={i} className="h-16 rounded-lg bg-card screentone animate-pulse"/>
                    ))}
                </div>
            ) : entries.length === 0 ? (
                <EmptyState
                    title="Nenhuma leitura registrada ainda"
                    description="Os volumes que você abrir aparecem aqui, com a página onde parou."
                    action={<Button onClick={() => navigate("/biblioteca")}>Explorar a biblioteca</Button>}
                />
            ) : (
                <div className="space-y-2">
                    {entries.map((entry, idx) => (
                        <Card
                            key={`${entry.volumeId}-${idx}`}
                            className="cursor-pointer hover:bg-muted/40 transition-colors"
                            onClick={() => navigate(`/leitor/${entry.volumeId}`, {
                                state: {
                                    mangaTitle: entry.mangaTitle,
                                    mangaId: entry.mangaId,
                                    volumeNumber: entry.volumeNumber,
                                    backUrl: "/historico",
                                }
                            })}
                        >
                            <CardContent className="p-3 flex items-center gap-3">
                                <div
                                    className="w-10 h-14 shrink-0 rounded overflow-hidden bg-muted flex items-center justify-center">
                                    {entry.mangaCoverUrl ? (
                                        <img
                                            src={entry.mangaCoverUrl}
                                            alt={entry.mangaTitle}
                                            className="w-full h-full object-cover"
                                        />
                                    ) : (
                                        <BookOpen className="w-4 h-4 text-muted-foreground"/>
                                    )}
                                </div>

                                <div className="flex-1 min-w-0">
                                    <p className="font-medium text-sm truncate">{entry.mangaTitle}</p>
                                    <p className="text-xs text-muted-foreground">
                                        Volume {entry.volumeNumber}
                                    </p>
                                </div>

                                {/* data + chevron */}
                                <div className="shrink-0 flex items-center gap-1.5 text-muted-foreground">
                                    <span className="text-xs">{formatDate(entry.readAt)}</span>
                                    <ChevronRight className="w-4 h-4"/>
                                </div>
                            </CardContent>
                        </Card>
                    ))}

                    {page < totalPages - 1 && (
                        <Button
                            variant="outline"
                            className="w-full"
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