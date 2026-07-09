import {useEffect, useState} from "react";
import {useNavigate} from "react-router-dom";
import {getReadingList, removeFromReadingList} from "@/api/engagementApi";
import type {ReadingListEntry, ReadingStatus} from "@/types/engagement";
import {Button} from "@/components/ui/button";
import {Badge} from "@/components/ui/badge";
import {Card, CardContent} from "@/components/ui/card";
import {toast} from "sonner";
import {BookOpen, ArrowLeft, X} from "lucide-react";

const STATUS_LABELS: Record<ReadingStatus, string> = {
    WANT_TO_READ: "Quero ler",
    READING: "Lendo",
    COMPLETED: "Concluído",
    DROPPED: "Dropei",
};

const STATUS_ORDER: ReadingStatus[] = ["READING", "WANT_TO_READ", "COMPLETED", "DROPPED"];

const STATUS_BADGE_VARIANT: Record<ReadingStatus, "default" | "secondary" | "outline" | "destructive"> = {
    READING: "default",
    WANT_TO_READ: "secondary",
    COMPLETED: "outline",
    DROPPED: "destructive",
};

export function ReadingListPage() {
    const navigate = useNavigate();
    const [entries, setEntries] = useState<ReadingListEntry[]>([]);
    const [loading, setLoading] = useState(true);
    const [removing, setRemoving] = useState<string | null>(null);

    useEffect(() => {
        getReadingList()
            .then((data) => setEntries(data))
            .catch(() => toast.error("Erro ao carregar lista de leitura"))
            .finally(() => setLoading(false));
    }, []);

    async function handleRemove(mangaId: string, title: string) {
        if (!window.confirm(`Remover "${title}" da lista?`)) return;
        setRemoving(mangaId);
        try {
            await removeFromReadingList(mangaId);
            setEntries(prev => prev.filter(e => e.mangaId !== mangaId));
            toast.success("Removido da lista");
        } catch {
            toast.error("Erro ao remover");
        } finally {
            setRemoving(null);
        }
    }

    const grouped = STATUS_ORDER.reduce<Record<ReadingStatus, ReadingListEntry[]>>((acc, status) => {
        acc[status] = entries.filter(e => e.status === status);
        return acc;
    }, {} as Record<ReadingStatus, ReadingListEntry[]>);

    return (
        <div className="max-w-3xl mx-auto px-4 md:px-6 py-8 space-y-8">
            <div className="flex items-center gap-3">
                <Button variant="ghost" size="icon" onClick={() => navigate(-1)}>
                    <ArrowLeft className="w-4 h-4"/>
                </Button>
                <h1 className="text-2xl font-bold">Lista de leitura</h1>
                {!loading && entries.length > 0 && (
                    <span className="text-sm text-muted-foreground">
                        {entries.length} {entries.length === 1 ? "título" : "títulos"}
                    </span>
                )}
            </div>

            {loading ? (
                <div className="space-y-6">
                    {[...Array(2)].map((_, i) => (
                        <div key={i} className="space-y-2">
                            <div className="h-4 w-24 bg-muted rounded animate-pulse"/>
                            {[...Array(3)].map((_, j) => (
                                <div key={j} className="h-16 bg-muted rounded-lg animate-pulse"/>
                            ))}
                        </div>
                    ))}
                </div>
            ) : entries.length === 0 ? (
                <div className="text-center py-20 space-y-3">
                    <BookOpen className="w-12 h-12 mx-auto text-muted-foreground/30"/>
                    <p className="text-muted-foreground">Sua lista está vazia.</p>
                    <Button variant="outline" onClick={() => navigate("/biblioteca")}>
                        Ir para a biblioteca
                    </Button>
                </div>
            ) : (
                <div className="space-y-8">
                    {STATUS_ORDER.map(status => {
                        const items = grouped[status];
                        if (items.length === 0) return null;
                        return (
                            <div key={status} className="space-y-2">
                                <div className="flex items-center gap-2">
                                    <h2 className="text-sm font-semibold uppercase tracking-wide text-muted-foreground">
                                        {STATUS_LABELS[status]}
                                    </h2>
                                    <Badge variant={STATUS_BADGE_VARIANT[status]} className="text-xs">
                                        {items.length}
                                    </Badge>
                                </div>

                                <div className="space-y-2">
                                    {items.map(entry => (
                                        <Card
                                            key={entry.mangaId}
                                            className="cursor-pointer hover:bg-muted/40 transition-colors"
                                            onClick={() => navigate(`/biblioteca/${entry.mangaSlug}`)}
                                        >
                                            <CardContent className="p-3 flex items-center gap-3">
                                                <div className="w-10 h-14 shrink-0 rounded overflow-hidden bg-muted flex items-center justify-center">
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
                                                    <p className="font-medium text-sm truncate">
                                                        {entry.mangaTitle}
                                                    </p>
                                                    <p className="text-xs text-muted-foreground">
                                                        {new Date(entry.updatedAt).toLocaleDateString("pt-BR", {
                                                            day: "2-digit", month: "short", year: "numeric"
                                                        })}
                                                    </p>
                                                </div>

                                                <button
                                                    className="shrink-0 p-1.5 rounded-md text-muted-foreground hover:text-destructive hover:bg-destructive/10 transition-colors"
                                                    onClick={(e) => {
                                                        e.stopPropagation();
                                                        handleRemove(entry.mangaId, entry.mangaTitle);
                                                    }}
                                                    disabled={removing === entry.mangaId}
                                                >
                                                    <X className="w-4 h-4"/>
                                                </button>
                                            </CardContent>
                                        </Card>
                                    ))}
                                </div>
                            </div>
                        );
                    })}
                </div>
            )}
        </div>
    );
}