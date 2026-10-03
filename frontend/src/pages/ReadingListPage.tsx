import {useEffect, useState} from "react";
import {Link, useNavigate} from "react-router-dom";
import {getReadingList, removeFromReadingList} from "@/api/engagementApi";
import type {ReadingListEntry, ReadingStatus} from "@/types/engagement";
import {Button} from "@/components/ui/button";
import {PageHeader} from "@/components/PageHeader";
import {EmptyState} from "@/components/EmptyState";
import {MangaCover} from "@/components/MangaCover";
import {toast} from "sonner";
import {X} from "lucide-react";

const STATUS_LABELS: Record<ReadingStatus, string> = {
    WANT_TO_READ: "Quero ler",
    READING: "Lendo",
    COMPLETED: "Concluído",
    DROPPED: "Dropei",
};

const STATUS_ORDER: ReadingStatus[] = ["READING", "WANT_TO_READ", "COMPLETED", "DROPPED"];

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
        <div className="max-w-5xl mx-auto px-4 md:px-8 py-8 md:py-10 flex flex-col gap-7">
            <PageHeader
                title="Lista de leitura"
                description={!loading && entries.length > 0
                    ? `${entries.length} ${entries.length === 1 ? "título" : "títulos"}, organizados pelo status de leitura.`
                    : "Organize o que quer ler, está lendo e já leu."}
            />

            {loading ? (
                <div className="flex flex-col gap-4 animate-pulse">
                    <div className="h-5 w-24 bg-card"/>
                    <div className="grid grid-cols-3 gap-x-3 sm:grid-cols-[repeat(auto-fill,minmax(140px,1fr))] sm:gap-x-5">
                        {[...Array(5)].map((_, i) => (
                            <div key={i} className="aspect-[2/3] rounded-lg bg-card screentone"/>
                        ))}
                    </div>
                </div>
            ) : entries.length === 0 ? (
                <EmptyState
                    title="Sua lista está vazia"
                    description="Na página de um mangá, use “Adicionar à lista” para acompanhar a leitura."
                    action={<Button onClick={() => navigate("/biblioteca")}>Explorar a biblioteca</Button>}
                />
            ) : (
                <div className="flex flex-col gap-10">
                    {STATUS_ORDER.map(status => {
                        const items = grouped[status];
                        if (items.length === 0) return null;
                        return (
                            <section key={status} className="flex flex-col gap-4">
                                <div className="flex items-baseline gap-2.5 border-b pb-2.5">
                                    {status === "READING" && <span aria-hidden="true" className="h-1 w-3 self-center -skew-x-15 bg-shu"/>}
                                    <h2 className="m-0 text-lg font-semibold tracking-[-0.01em]">{STATUS_LABELS[status]}</h2>
                                    <span className="font-mono text-xs text-muted-foreground">{items.length}</span>
                                </div>

                                <ul className="m-0 grid list-none grid-cols-3 gap-x-3 gap-y-6 p-0 sm:grid-cols-[repeat(auto-fill,minmax(140px,1fr))] sm:gap-x-5">
                                    {items.map(entry => (
                                        <li key={entry.mangaId} className="flex flex-col gap-1">
                                            <Link to={`/biblioteca/${entry.mangaSlug}`} className="group flex flex-col gap-2">
                                                <MangaCover
                                                    title={entry.mangaTitle}
                                                    coverUrl={entry.mangaCoverUrl}
                                                    className="transition-transform duration-200 group-hover:-translate-y-[3px]"
                                                />
                                                <span className="text-[13px] sm:text-sm font-medium leading-snug line-clamp-2">{entry.mangaTitle}</span>
                                            </Link>
                                            <div className="flex items-center justify-between gap-1">
                                                <span className="font-mono text-[11px] text-muted-foreground">
                                                    {new Date(entry.updatedAt).toLocaleDateString("pt-BR", {day: "2-digit", month: "short"})}
                                                </span>
                                                <button
                                                    type="button"
                                                    aria-label={`Remover ${entry.mangaTitle} da lista`}
                                                    className="-mr-1.5 flex size-8 items-center justify-center rounded-sm text-muted-foreground transition-colors hover:bg-destructive/10 hover:text-destructive disabled:opacity-50"
                                                    onClick={() => handleRemove(entry.mangaId, entry.mangaTitle)}
                                                    disabled={removing === entry.mangaId}
                                                >
                                                    <X className="size-4"/>
                                                </button>
                                            </div>
                                        </li>
                                    ))}
                                </ul>
                            </section>
                        );
                    })}
                </div>
            )}
        </div>
    );
}