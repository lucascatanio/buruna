import {useEffect, useRef, useState} from "react";
import {useParams, useNavigate} from "react-router-dom";
import api from "@/lib/axios";
import {useAuthStore} from "@/store/authStore";
import {Button} from "@/components/ui/button";
import {Badge} from "@/components/ui/badge";
import {Card, CardContent} from "@/components/ui/card";
import {Input} from "@/components/ui/input";
import {Label} from "@/components/ui/label";
import {toast} from "sonner";
import {ArrowLeft, BookOpen, Pencil, Trash2, Upload, X, Star, BookMarked, ChevronDown} from "lucide-react";

interface Tag {
    id: string;
    name: string;
    slug: string;
    category: { id: string; name: string };
}

interface Volume {
    id: string;
    volumeNumber: number;
    fileSizeBytes: number;
    createdAt: string;
}

interface MangaDetail {
    id: string;
    slug: string;
    title: string;
    alternativeTitles: string[];
    synopsis: string | null;
    coverUrl: string | null;
    format: string;
    originCountry: string | null;
    statusOrigin: string;
    statusSite: string;
    year: number | null;
    contentWarnings: string[];
    avgRating: number;
    ratingCount: number;
    viewCount: number;
    isPublic: boolean;
    ownerId: string;
    tags: Tag[];
    volumes: Volume[];
    createdAt: string;
}

const FORMAT_LABELS: Record<string, string> = {
    MANGA: "Mangá", MANHWA: "Manhwa", MANHUA: "Manhua",
    WEBTOON: "Webtoon", ONE_SHOT: "One-shot",
};

const STATUS_ORIGIN_LABELS: Record<string, string> = {
    ONGOING: "Em andamento", COMPLETED: "Completo",
    HIATUS: "Hiato", CANCELLED: "Cancelado",
};

const CONTENT_WARNING_LABELS: Record<string, string> = {
    NSFW: "NSFW", GORE: "Gore",
    GATILHO_SUICIDIO: "Gatilho: Suicídio",
    GATILHO_ABUSO: "Gatilho: Abuso",
    GATILHO_TRAUMA: "Gatilho: Trauma",
};


type ReadingStatus = "WANT_TO_READ" | "READING" | "COMPLETED" | "DROPPED";

const READING_STATUS_LABELS: Record<ReadingStatus, string> = {
    WANT_TO_READ: "Quero ler",
    READING: "Lendo",
    COMPLETED: "Concluído",
    DROPPED: "Dropei",
};

const READING_STATUS_OPTIONS: ReadingStatus[] = ["WANT_TO_READ", "READING", "COMPLETED", "DROPPED"];

function formatBytes(bytes: number): string {
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function nextVolumeNumber(volumes: Volume[]): number {
    if (volumes.length === 0) return 1;
    return Math.max(...volumes.map((v) => v.volumeNumber)) + 1;
}

export function MangaDetailPage() {
    const {slug} = useParams<{ slug: string }>();
    const navigate = useNavigate();
    const user = useAuthStore((s) => s.user);

    const [manga, setManga] = useState<MangaDetail | null>(null);
    const [volumes, setVolumes] = useState<Volume[]>([]);
    const [volumeProgress, setVolumeProgress] = useState<Record<string, number>>({});
    const [loading, setLoading] = useState(true);
    const [deleting, setDeleting] = useState(false);

    const [readingStatus, setReadingStatus] = useState<ReadingStatus | null>(null);
    const [showStatusMenu, setShowStatusMenu] = useState(false);
    const [userRating, setUserRating] = useState<number | null>(null);
    const [hoverRating, setHoverRating] = useState<number | null>(null);
    const [ratingCount, setRatingCount] = useState(0);
    const [avgRating, setAvgRating] = useState(0);
    const [savingStatus, setSavingStatus] = useState(false);
    const [savingRating, setSavingRating] = useState(false);

    const [showUploadModal, setShowUploadModal] = useState(false);
    const [volumeNumber, setVolumeNumber] = useState("1");
    const [volumeFile, setVolumeFile] = useState<File | null>(null);
    const [uploading, setUploading] = useState(false);
    const fileInputRef = useRef<HTMLInputElement>(null);

    useEffect(() => {
        if (!slug) return;
        api.get<MangaDetail>(`/mangas/${slug}`)
            .then(({data}) => {
                setManga(data);
                const sorted = [...data.volumes].sort((a, b) => a.volumeNumber - b.volumeNumber);
                setVolumes(sorted);
                if (sorted.length > 0) {
                    const ids = sorted.map((v) => v.id).join(",");
                    api.get<Record<string, number>>(`/reader/progress/batch?volumeIds=${ids}`)
                        .then(({data: prog}) => setVolumeProgress(prog))
                        .catch(() => {});
                }
            })
            .catch(() => navigate("/biblioteca", {replace: true}))
            .finally(() => setLoading(false));
    }, [slug, navigate]);

    useEffect(() => {
        if (!manga) return;

        api.get<{mangaId: string; status: ReadingStatus}[]>("/reading-list")
            .then(({data}) => {
                const entry = data.find(e => e.mangaId === manga.id);
                if (entry) setReadingStatus(entry.status);
            })
            .catch(() => {});

        api.get(`/mangas/${manga.id}/rating`)
            .then(({data}) => {
                if (data?.score) setUserRating(data.score);
            })
            .catch(() => {}); // 204 = nunca avaliou, ignora

        setRatingCount(manga.ratingCount);
        setAvgRating(Number(manga.avgRating));
    }, [manga]);

    async function handleStatusChange(status: ReadingStatus) {
        if (!manga) return;
        setSavingStatus(true);
        try {
            await api.put(`/reading-list/${manga.id}`, {status});
            setReadingStatus(status);
            setShowStatusMenu(false);
        } catch {
            toast.error("Erro ao atualizar lista de leitura");
        } finally {
            setSavingStatus(false);
        }
    }

    async function handleRemoveFromList() {
        if (!manga || !readingStatus) return;
        setSavingStatus(true);
        try {
            await api.delete(`/reading-list/${manga.id}`);
            setReadingStatus(null);
            setShowStatusMenu(false);
        } catch {
            toast.error("Erro ao remover da lista");
        } finally {
            setSavingStatus(false);
        }
    }

    async function handleRate(score: number) {
        if (!manga || savingRating) return;
        setSavingRating(true);
        try {
            if (userRating !== null) {
                const {data} = await api.put(`/mangas/${manga.id}/rating`, {score});
                setUserRating(score);
                setAvgRating(Number(data.avgRating));
                setRatingCount(data.ratingCount);
            } else {
                const {data} = await api.post(`/mangas/${manga.id}/rating`, {score});
                setUserRating(score);
                setAvgRating(Number(data.avgRating));
                setRatingCount(data.ratingCount);
            }
        } catch {
            toast.error("Erro ao salvar avaliação");
        } finally {
            setSavingRating(false);
        }
    }

    async function handleRemoveRating() {
        if (!manga || userRating === null || savingRating) return;
        setSavingRating(true);
        try {
            await api.delete(`/mangas/${manga.id}/rating`);
            setUserRating(null);
            // busca avg/count atualizado
            const {data} = await api.get(`/mangas/${manga.slug}`);
            setAvgRating(Number(data.avgRating));
            setRatingCount(data.ratingCount);
        } catch {
            toast.error("Erro ao remover avaliação");
        } finally {
            setSavingRating(false);
        }
    }

    const canModify = user && manga && (
        user.role === "ADMIN" || user.id === manga.ownerId
    );

    function openUploadModal() {
        setVolumeNumber(String(nextVolumeNumber(volumes)));
        setVolumeFile(null);
        setShowUploadModal(true);
    }

    function closeUploadModal() {
        setShowUploadModal(false);
        setVolumeFile(null);
        if (fileInputRef.current) fileInputRef.current.value = "";
    }

    async function handleUploadVolume() {
        if (!manga || !volumeFile) return;
        setUploading(true);
        try {
            const formData = new FormData();
            formData.append("file", volumeFile);
            formData.append("volumeNumber", volumeNumber);
            const {data} = await api.post(`/mangas/${manga.id}/volumes`, formData);
            toast.success(`Volume ${volumeNumber} adicionado!`);
            setVolumes((prev) =>
                [...prev, data].sort((a, b) => a.volumeNumber - b.volumeNumber)
            );
            closeUploadModal();
        } catch (err: any) {
            toast.error(err.response?.data?.message ?? "Erro ao enviar volume");
        } finally {
            setUploading(false);
        }
    }

    async function handleDelete() {
        if (!manga) return;
        if (!window.confirm(`Deletar "${manga.title}"? Esta ação não pode ser desfeita.`)) return;
        setDeleting(true);
        try {
            await api.delete(`/mangas/${manga.id}`);
            toast.success("Mangá removido");
            navigate("/biblioteca");
        } catch (err: any) {
            toast.error(err.response?.data?.message ?? "Erro ao deletar");
            setDeleting(false);
        }
    }

    if (loading) {
        return (
            <div className="max-w-5xl mx-auto px-4 md:px-6 py-8 space-y-6 animate-pulse">
                <div className="h-6 bg-muted rounded w-32"/>
                <div className="flex gap-6">
                    <div className="w-40 aspect-[2/3] bg-muted rounded-md shrink-0"/>
                    <div className="flex-1 space-y-3">
                        <div className="h-8 bg-muted rounded w-3/4"/>
                        <div className="h-4 bg-muted rounded w-1/2"/>
                    </div>
                </div>
            </div>
        );
    }

    if (!manga) return null;

    const tagsByCategory = manga.tags.reduce<Record<string, Tag[]>>((acc, tag) => {
        const cat = tag.category.name;
        if (!acc[cat]) acc[cat] = [];
        acc[cat].push(tag);
        return acc;
    }, {});

    return (
        <div className="max-w-5xl mx-auto px-4 md:px-6 py-6 space-y-6">

            {showUploadModal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 px-4">
                    <div className="bg-background border rounded-xl w-full max-w-md p-6 space-y-4 shadow-xl">
                        <div className="flex items-center justify-between">
                            <h2 className="text-base font-semibold">Adicionar volume</h2>
                            <button onClick={closeUploadModal}>
                                <X className="w-4 h-4 text-muted-foreground"/>
                            </button>
                        </div>

                        <div className="grid grid-cols-2 gap-3">
                            <div className="space-y-1.5">
                                <Label htmlFor="modal-vol-number">Número do volume</Label>
                                <Input
                                    id="modal-vol-number"
                                    type="number"
                                    min="1"
                                    value={volumeNumber}
                                    onChange={(e) => setVolumeNumber(e.target.value)}
                                />
                            </div>
                            <div className="space-y-1.5">
                                <Label htmlFor="modal-vol-file">Arquivo (PDF, EPUB, MOBI)</Label>
                                <Input
                                    id="modal-vol-file"
                                    ref={fileInputRef}
                                    type="file"
                                    accept=".pdf,application/pdf"
                                    onChange={(e) => setVolumeFile(e.target.files?.[0] ?? null)}
                                />
                            </div>
                        </div>

                        {volumeFile && (
                            <p className="text-xs text-muted-foreground">
                                {volumeFile.name} — {formatBytes(volumeFile.size)}
                            </p>
                        )}

                        <div className="flex gap-2 pt-1">
                            <Button variant="outline" className="flex-1" onClick={closeUploadModal}>
                                Cancelar
                            </Button>
                            <Button
                                className="flex-1"
                                onClick={handleUploadVolume}
                                disabled={!volumeFile || uploading}
                            >
                                <Upload className="w-4 h-4 mr-1.5"/>
                                {uploading ? "Enviando…" : "Enviar"}
                            </Button>
                        </div>
                    </div>
                </div>
            )}

            <div className="flex items-center justify-between gap-2">
                <Button variant="ghost" size="sm" onClick={() => navigate("/biblioteca")}>
                    <ArrowLeft className="w-4 h-4 mr-1.5"/>
                    Biblioteca
                </Button>
                {canModify && (
                    <div className="flex gap-2">
                        <Button
                            variant="outline"
                            size="sm"
                            onClick={() => navigate(`/mangas/${manga.id}/editar`)}
                        >
                            <Pencil className="w-4 h-4 mr-1.5"/>
                            Editar
                        </Button>
                        <Button
                            variant="destructive"
                            size="sm"
                            onClick={handleDelete}
                            disabled={deleting}
                        >
                            <Trash2 className="w-4 h-4 mr-1.5"/>
                            {deleting ? "Deletando…" : "Deletar"}
                        </Button>
                    </div>
                )}
            </div>

            <div className="flex flex-col sm:flex-row gap-6">
                <div className="w-full sm:w-44 shrink-0">
                    <div className="aspect-[2/3] rounded-lg overflow-hidden bg-muted border">
                        {manga.coverUrl ? (
                            <img src={manga.coverUrl} alt={manga.title} className="w-full h-full object-cover"/>
                        ) : (
                            <div className="w-full h-full flex items-center justify-center">
                                <BookOpen className="w-10 h-10 text-muted-foreground/30"/>
                            </div>
                        )}
                    </div>
                </div>

                <div className="flex-1 space-y-3">
                    <div>
                        <h1 className="text-2xl font-bold leading-snug">{manga.title}</h1>
                        {manga.alternativeTitles.length > 0 && (
                            <p className="text-sm text-muted-foreground mt-1">
                                {manga.alternativeTitles.join(" · ")}
                            </p>
                        )}
                    </div>

                    <div className="flex flex-wrap gap-2 text-sm">
                        <Badge variant="secondary">{FORMAT_LABELS[manga.format] ?? manga.format}</Badge>
                        <Badge
                            variant="outline">{STATUS_ORIGIN_LABELS[manga.statusOrigin] ?? manga.statusOrigin}</Badge>
                        {manga.year && <Badge variant="outline">{manga.year}</Badge>}
                        {manga.originCountry && <Badge variant="outline">{manga.originCountry}</Badge>}
                    </div>

                    {manga.contentWarnings.length > 0 && (
                        <div className="flex flex-wrap gap-1">
                            {manga.contentWarnings.map((w) => (
                                <Badge key={w} variant="destructive" className="text-xs">
                                    {CONTENT_WARNING_LABELS[w] ?? w}
                                </Badge>
                            ))}
                        </div>
                    )}

                    {manga.synopsis && (
                        <p className="text-sm text-foreground/80 leading-relaxed">{manga.synopsis}</p>
                    )}

                    <div className="flex flex-wrap items-center gap-3 pt-1">

                        <div className="relative">
                            <button
                                className={`flex items-center gap-1.5 text-xs px-3 py-1.5 rounded-md border transition-colors
                                    ${readingStatus
                                    ? "bg-primary text-primary-foreground border-primary"
                                    : "border-border text-muted-foreground hover:text-foreground hover:border-foreground/40"
                                }
                                    ${savingStatus ? "opacity-60 cursor-not-allowed" : ""}
                                `}
                                onClick={() => setShowStatusMenu(s => !s)}
                                disabled={savingStatus}
                            >
                                <BookMarked className="w-3.5 h-3.5"/>
                                {readingStatus ? READING_STATUS_LABELS[readingStatus] : "Adicionar à lista"}
                                <ChevronDown className="w-3 h-3"/>
                            </button>

                            {showStatusMenu && (
                                <div className="absolute top-full left-0 mt-1 z-20 bg-popover border rounded-md shadow-md py-1 min-w-[160px]">
                                    {READING_STATUS_OPTIONS.map(s => (
                                        <button
                                            key={s}
                                            className={`w-full text-left text-xs px-3 py-2 hover:bg-muted transition-colors
                                                ${readingStatus === s ? "text-primary font-medium" : "text-foreground"}
                                            `}
                                            onClick={() => handleStatusChange(s)}
                                        >
                                            {READING_STATUS_LABELS[s]}
                                        </button>
                                    ))}
                                    {readingStatus && (
                                        <>
                                            <div className="border-t my-1"/>
                                            <button
                                                className="w-full text-left text-xs px-3 py-2 text-destructive hover:bg-muted transition-colors"
                                                onClick={handleRemoveFromList}
                                            >
                                                Remover da lista
                                            </button>
                                        </>
                                    )}
                                </div>
                            )}
                        </div>

                        <div className="flex items-center gap-1">
                            {[1, 2, 3, 4, 5].map(star => (
                                <button
                                    key={star}
                                    className={`transition-colors ${savingRating ? "cursor-not-allowed" : "cursor-pointer"}`}
                                    onMouseEnter={() => setHoverRating(star)}
                                    onMouseLeave={() => setHoverRating(null)}
                                    onClick={() => userRating === star ? handleRemoveRating() : handleRate(star)}
                                    disabled={savingRating}
                                >
                                    <Star
                                        className={`w-5 h-5 transition-colors
                                            ${(hoverRating ?? userRating ?? 0) >= star
                                            ? "fill-yellow-400 text-yellow-400"
                                            : "text-muted-foreground/40"
                                        }
                                        `}
                                    />
                                </button>
                            ))}
                            {userRating && (
                                <span className="text-xs text-muted-foreground ml-1">sua nota</span>
                            )}
                        </div>
                    </div>

                    <div className="flex flex-wrap gap-4 text-xs text-muted-foreground">
                        {ratingCount > 0 && (
                            <span>⭐ {avgRating.toFixed(1)} ({ratingCount} avaliações)</span>
                        )}
                        <span>👁 {manga.viewCount} visualizações</span>
                    </div>

                    {Object.entries(tagsByCategory).map(([cat, tags]) => (
                        <div key={cat}>
                            <p className="text-xs text-muted-foreground uppercase tracking-wide mb-1">{cat}</p>
                            <div className="flex flex-wrap gap-1">
                                {tags.map((t) => (
                                    <Badge key={t.id} variant="outline" className="text-xs">
                                        {t.name}
                                    </Badge>
                                ))}
                            </div>
                        </div>
                    ))}
                </div>
            </div>

            <div className="space-y-3">
                <div className="flex items-center justify-between">
                    <h2 className="text-lg font-semibold">
                        Volumes{" "}
                        <span className="text-muted-foreground font-normal text-base">
                            ({volumes.length})
                        </span>
                    </h2>
                    {canModify && (
                        <Button variant="outline" size="sm" onClick={openUploadModal}>
                            <Upload className="w-4 h-4 mr-1.5"/>
                            Adicionar volume
                        </Button>
                    )}
                </div>

                {volumes.length === 0 && (
                    <Card>
                        <CardContent className="py-8 text-center text-sm text-muted-foreground">
                            Nenhum volume disponível
                        </CardContent>
                    </Card>
                )}

                <div className="space-y-2">
                    {volumes.map((vol) => (
                        <Card key={vol.id}>
                            <CardContent className="py-3 px-4 flex items-center justify-between">
                                <div>
                                    <p className="text-sm font-medium">Volume {vol.volumeNumber}</p>
                                    <p className="text-xs text-muted-foreground">{formatBytes(vol.fileSizeBytes)}</p>
                                    {volumeProgress[vol.id] !== undefined && (
                                        <p className="text-xs text-primary mt-0.5">
                                            Pág. {volumeProgress[vol.id]}
                                        </p>
                                    )}
                                </div>
                                <Button
                                    size="sm"
                                    onClick={() => navigate(`/leitor/${vol.id}`, {
                                        state: {
                                            mangaId: manga.id,
                                            mangaTitle: manga.title,
                                            volumeNumber: vol.volumeNumber,
                                            backUrl: `/biblioteca/${manga.slug}`,
                                        }
                                    })}
                                >
                                    {volumeProgress[vol.id] !== undefined ? "Continuar" : "Ler"}
                                </Button>
                            </CardContent>
                        </Card>
                    ))}
                </div>
            </div>
        </div>
    );
}