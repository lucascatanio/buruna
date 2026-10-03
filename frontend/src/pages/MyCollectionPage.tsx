import {useEffect, useState, useCallback} from "react";
import {Link, useNavigate} from "react-router-dom";
import {deleteMyManga, getMyQuota, listMyMangas} from "@/api/privateMangaApi";
import type {PrivateManga, QuotaInfo} from "@/types/manga";
import {Button} from "@/components/ui/button";
import {PageHeader} from "@/components/PageHeader";
import {EmptyState} from "@/components/EmptyState";
import {MangaCover} from "@/components/MangaCover";
import {toast} from "sonner";
import {Plus, HardDrive, ChevronRight, Trash2, Upload} from "lucide-react";

function formatBytes(bytes: number): string {
    if (bytes === 0) return "0 B";
    const k = 1024;
    const sizes = ["B", "KB", "MB", "GB"];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return `${parseFloat((bytes / Math.pow(k, i)).toFixed(1))} ${sizes[i]}`;
}

export function MyCollectionPage() {
    const navigate = useNavigate();
    const [mangas, setMangas] = useState<PrivateManga[]>([]);
    const [quota, setQuota] = useState<QuotaInfo | null>(null);
    const [loading, setLoading] = useState(true);
    const [deletingId, setDeletingId] = useState<string | null>(null);

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const [mangasRes, quotaRes] = await Promise.all([
                listMyMangas(50),
                getMyQuota(),
            ]);
            setMangas(mangasRes.content);
            setQuota(quotaRes);
        } catch {
            toast.error("Erro ao carregar coleção");
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        load();
    }, [load]);

    async function handleDelete(manga: PrivateManga) {
        if (!confirm(`Deletar "${manga.title}"? Esta ação não pode ser desfeita.`)) return;
        setDeletingId(manga.id);
        try {
            await deleteMyManga(manga.id);
            toast.success("Mangá removido");
            setMangas((prev) => prev.filter((m) => m.id !== manga.id));
            // atualiza quota
            const data = await getMyQuota();
            setQuota(data);
        } catch (err: any) {
            toast.error(err.response?.data?.message ?? "Erro ao deletar");
        } finally {
            setDeletingId(null);
        }
    }

    const usedPercent = quota
        ? Math.min(100, (quota.usedBytes / quota.quotaBytes) * 100)
        : 0;

    const quotaColor =
        usedPercent >= 90 ? "bg-destructive" :
            usedPercent >= 70 ? "bg-shu" :
                "bg-foreground/70";

    return (
        <div className="max-w-3xl mx-auto px-4 md:px-6 py-8 space-y-6">

            <PageHeader
                title="Minha Coleção"
                description="Mangás particulares, visíveis só para você."
                actions={
                    <Button onClick={() => navigate("/colecao/novo")}>
                        <Plus className="size-4"/>
                        Adicionar
                    </Button>
                }
            />

            {quota && (
                <div className="space-y-1.5">
                    <div className="flex items-center justify-between text-sm">
                        <span className="flex items-center gap-1.5 text-muted-foreground">
                            <HardDrive className="w-3.5 h-3.5"/>
                            Armazenamento
                        </span>
                        <span className="text-muted-foreground">
                            {formatBytes(quota.usedBytes)} / {formatBytes(quota.quotaBytes)}
                        </span>
                    </div>
                    <div className="h-1 w-full bg-muted overflow-hidden">
                        <div
                            className={`h-full transition-all ${quotaColor}`}
                            style={{width: `${usedPercent}%`}}
                        />
                    </div>
                </div>
            )}

            {loading ? (
                <div className="space-y-3">
                    {[...Array(3)].map((_, i) => (
                        <div key={i} className="h-20 rounded-lg bg-card screentone animate-pulse"/>
                    ))}
                </div>
            ) : mangas.length === 0 ? (
                <EmptyState
                    title="Nenhum mangá na coleção ainda"
                    description="Envie seus próprios volumes; eles ficam só com você."
                    action={
                        <Button onClick={() => navigate("/colecao/novo")}>
                            <Upload className="size-4"/>
                            Fazer o primeiro upload
                        </Button>
                    }
                />
            ) : (
                <ul className="m-0 list-none divide-y border-y p-0">
                    {mangas.map((manga) => {
                        const size = manga.volumes.reduce((acc, v) => acc + v.fileSizeBytes, 0);
                        return (
                            <li key={manga.id} className="flex items-center gap-2">
                                <Link to={`/colecao/${manga.id}`} className="group flex min-w-0 flex-1 items-center gap-4 py-3 transition-colors hover:bg-muted/40">
                                    <MangaCover title={manga.title} coverUrl={manga.coverUrl} compact className="w-12 shrink-0 shadow-none"/>
                                    <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                                        <span className="truncate text-[15px] font-medium">{manga.title}</span>
                                        <span className="font-mono text-xs text-muted-foreground">
                                            {manga.volumes.length === 0
                                                ? "Sem volumes"
                                                : `${manga.volumes.length} ${manga.volumes.length === 1 ? "volume" : "volumes"} · ${formatBytes(size)}`}
                                        </span>
                                    </div>
                                    <ChevronRight aria-hidden="true" className="size-4 shrink-0 text-muted-foreground transition-transform group-hover:translate-x-0.5"/>
                                </Link>
                                <Button
                                    variant="ghost"
                                    size="icon"
                                    aria-label={`Excluir ${manga.title}`}
                                    className="shrink-0 text-muted-foreground hover:text-destructive"
                                    disabled={deletingId === manga.id}
                                    onClick={() => handleDelete(manga)}
                                >
                                    <Trash2 className="size-4"/>
                                </Button>
                            </li>
                        );
                    })}
                </ul>
            )}
        </div>
    );
}