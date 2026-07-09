import {useEffect, useState, useCallback} from "react";
import {useNavigate} from "react-router-dom";
import {deleteMyManga, getMyQuota, listMyMangas} from "@/api/privateMangaApi";
import type {PrivateManga, QuotaInfo} from "@/types/manga";
import {Button} from "@/components/ui/button";
import {Card, CardContent} from "@/components/ui/card";
import {toast} from "sonner";
import {Plus, BookOpen, HardDrive, ChevronRight, Trash2, Upload} from "lucide-react";

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
            usedPercent >= 70 ? "bg-yellow-500" :
                "bg-primary";

    return (
        <div className="max-w-3xl mx-auto px-4 md:px-6 py-8 space-y-6">

            <div className="flex items-center justify-between">
                <h1 className="text-2xl font-bold">Minha Coleção</h1>
                <Button size="sm" onClick={() => navigate("/colecao/novo")}>
                    <Plus className="w-4 h-4 mr-1.5"/>
                    Adicionar
                </Button>
            </div>

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
                    <div className="h-1.5 w-full rounded-full bg-muted overflow-hidden">
                        <div
                            className={`h-full rounded-full transition-all ${quotaColor}`}
                            style={{width: `${usedPercent}%`}}
                        />
                    </div>
                </div>
            )}

            {loading ? (
                <div className="space-y-3">
                    {[...Array(3)].map((_, i) => (
                        <div key={i} className="h-20 rounded-lg bg-muted animate-pulse"/>
                    ))}
                </div>
            ) : mangas.length === 0 ? (
                <div className="text-center py-16 space-y-3">
                    <BookOpen className="w-12 h-12 mx-auto text-muted-foreground/40"/>
                    <p className="text-muted-foreground">Nenhum mangá na coleção ainda.</p>
                    <Button variant="outline" onClick={() => navigate("/colecao/novo")}>
                        <Upload className="w-4 h-4 mr-1.5"/>
                        Fazer primeiro upload
                    </Button>
                </div>
            ) : (
                <div className="space-y-2">
                    {mangas.map((manga) => (
                        <Card
                            key={manga.id}
                            className="cursor-pointer hover:bg-muted/40 transition-colors"
                            onClick={() => navigate(`/colecao/${manga.id}`)}
                        >
                            <CardContent className="p-4 flex items-center gap-4">
                                <div
                                    className="w-12 h-16 shrink-0 rounded overflow-hidden bg-muted flex items-center justify-center">
                                    {manga.coverUrl ? (
                                        <img
                                            src={manga.coverUrl}
                                            alt={manga.title}
                                            className="w-full h-full object-cover"
                                        />
                                    ) : (
                                        <BookOpen className="w-5 h-5 text-muted-foreground"/>
                                    )}
                                </div>

                                <div className="flex-1 min-w-0">
                                    <p className="font-medium truncate">{manga.title}</p>
                                    <p className="text-sm text-muted-foreground">
                                        {manga.volumes.length === 0
                                            ? "Sem volumes"
                                            : `${manga.volumes.length} volume${manga.volumes.length !== 1 ? "s" : ""}`}
                                        {manga.volumes.length > 0 && (
                                            <span className="ml-1.5 text-xs">
                                                · {formatBytes(manga.volumes.reduce((acc, v) => acc + v.fileSizeBytes, 0))}
                                            </span>
                                        )}
                                    </p>
                                </div>

                                <div className="flex items-center gap-1 shrink-0" onClick={(e) => e.stopPropagation()}>
                                    <Button
                                        variant="ghost"
                                        size="icon"
                                        className="h-8 w-8 text-muted-foreground hover:text-destructive"
                                        disabled={deletingId === manga.id}
                                        onClick={() => handleDelete(manga)}
                                    >
                                        <Trash2 className="w-4 h-4"/>
                                    </Button>
                                    <ChevronRight className="w-4 h-4 text-muted-foreground"/>
                                </div>
                            </CardContent>
                        </Card>
                    ))}
                </div>
            )}
        </div>
    );
}