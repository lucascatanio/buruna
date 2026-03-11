import {useEffect, useState} from "react";
import {useNavigate} from "react-router-dom";
import api from "@/lib/axios";
import {Button} from "@/components/ui/button";
import {Card, CardContent} from "@/components/ui/card";
import {toast} from "sonner";
import {BookOpen, ArrowLeft, ChevronRight} from "lucide-react";

interface HistoryEntry {
    volumeId: string;
    volumeNumber: number;
    mangaId: string;
    mangaTitle: string;
    mangaCoverUrl: string | null;
    readAt: string;
}

interface Page<T> {
    content: T[];
    totalElements: number;
    totalPages: number;
    number: number;
}

function formatDate(iso: string): string {
    const d = new Date(iso);
    const now = new Date();
    const diffMs = now.getTime() - d.getTime();
    const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));

    if (diffDays === 0) return "Hoje";
    if (diffDays === 1) return "Ontem";
    if (diffDays < 7) return `${diffDays} dias atrás`;
    return d.toLocaleDateString("pt-BR", {day: "2-digit", month: "short", year: "numeric"});
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
            const {data} = await api.get<Page<HistoryEntry>>(
                `/reader/history?page=${pageNum}&size=20`
            );
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
            <div className="flex items-center gap-3">
                <Button variant="ghost" size="icon" onClick={() => navigate(-1)}>
                    <ArrowLeft className="w-4 h-4"/>
                </Button>
                <h1 className="text-2xl font-bold">Histórico de leitura</h1>
            </div>

            {loading ? (
                <div className="space-y-2">
                    {[...Array(5)].map((_, i) => (
                        <div key={i} className="h-16 rounded-lg bg-muted animate-pulse"/>
                    ))}
                </div>
            ) : entries.length === 0 ? (
                <div className="text-center py-16 space-y-3">
                    <BookOpen className="w-12 h-12 mx-auto text-muted-foreground/40"/>
                    <p className="text-muted-foreground">Nenhuma leitura registrada ainda.</p>
                    <Button variant="outline" onClick={() => navigate("/biblioteca")}>
                        Ir para a biblioteca
                    </Button>
                </div>
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