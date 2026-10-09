import {useEffect, useRef, useState} from "react";
import {Link, useParams, useNavigate} from "react-router-dom";
import {deleteWork, finalizeVolumeUpload, getWork, getVolumeUploadUrl} from "@/api/workApi";
import {uploadVolumeFile} from "@/api/volumeUpload";
import {getBatchProgress, getVolumeUrl} from "@/api/readingApi";
import {
    createRating,
    deleteRating,
    getMyRating,
    getReadingList,
    removeFromReadingList,
    setReadingStatus as setReadingStatusApi,
    updateRating,
} from "@/api/engagementApi";
import type {WorkDetail, Tag, Volume} from "@/types/work";
import type {ReadingStatus} from "@/types/engagement";
import type {ProgressResponse} from "@/types/reading";
import {getSignedUrl, setSignedUrl} from "@/lib/signedUrlCache";
import {useAuthStore} from "@/store/authStore";
import {Button} from "@/components/ui/button";
import {Badge} from "@/components/ui/badge";
import {Input} from "@/components/ui/input";
import {Label} from "@/components/ui/label";
import {EmptyState} from "@/components/EmptyState";
import {WorkCover} from "@/components/WorkCover";
import {Macron} from "@/components/Macron";
import {FileInput} from "@/components/ui/file-input";
import {toast} from "sonner";
import {BookOpen, ChevronLeft, Pencil, Trash2, Upload, X, Star, BookMarked, ChevronDown} from "lucide-react";

const FORMAT_LABELS: Record<string, string> = {
    MANGA: "Mangá", MANHWA: "Manhwa", MANHUA: "Manhua",
    WEBTOON: "Webtoon", ONE_SHOT: "One-shot", LIVRO: "Livro"
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

const READING_STATUS_LABELS: Record<ReadingStatus, string> = {
    WANT_TO_READ: "Quero ler",
    READING: "Lendo",
    COMPLETED: "Concluído",
    DROPPED: "Dropei",
};

const READING_STATUS_OPTIONS: ReadingStatus[] = ["WANT_TO_READ", "READING", "COMPLETED", "DROPPED"];

function progressLabel(progress: ProgressResponse): string {
    if (progress.finished) return "Lido";
    return progress.totalPages
        ? `Página ${progress.currentPage} de ${progress.totalPages}`
        : `Parou na pág. ${progress.currentPage}`;
}

function formatBytes(bytes: number): string {
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function nextVolumeNumber(volumes: Volume[]): number {
    if (volumes.length === 0) return 1;
    return Math.max(...volumes.map((v) => v.volumeNumber)) + 1;
}

export function WorkDetailPage() {
    const {slug} = useParams<{ slug: string }>();
    const navigate = useNavigate();
    const user = useAuthStore((s) => s.user);

    const [work, setWork] = useState<WorkDetail | null>(null);
    const [volumes, setVolumes] = useState<Volume[]>([]);
    const [volumeProgress, setVolumeProgress] = useState<Record<string, ProgressResponse>>({});
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
        getWork(slug)
            .then((data) => {
                setWork(data);
                const sorted = [...data.volumes].sort((a, b) => a.volumeNumber - b.volumeNumber);
                setVolumes(sorted);
                if (sorted.length > 0) {
                    const ids = sorted.map((v) => v.id);
                    getBatchProgress(ids)
                        .then((prog) => setVolumeProgress(prog))
                        .catch((e) => console.warn("[WorkDetailPage] falha ao carregar progresso de volumes:", e));
                }
            })
            .catch(() => navigate("/biblioteca", {replace: true}))
            .finally(() => setLoading(false));
    }, [slug, navigate]);

    useEffect(() => {
        if (!work) return;

        getReadingList()
            .then((data) => {
                const entry = data.find(e => e.workId === work.id);
                if (entry) setReadingStatus(entry.status);
            })
            .catch((e) => console.warn("[WorkDetailPage] falha ao carregar lista de leitura:", e));

        getMyRating(work.id)
            .then((data) => {
                if (data?.score) setUserRating(data.score);
            })
            .catch((e) => console.warn("[WorkDetailPage] falha ao carregar avaliação do usuário:", e));

        setRatingCount(work.ratingCount);
        setAvgRating(Number(work.avgRating));
    }, [work]);

    // prefetch signed URL em background para o volume que o usuário provavelmente vai ler
    useEffect(() => {
        if (volumes.length === 0) return;
        const target = volumes.find(v => volumeProgress[v.id] !== undefined) ?? volumes[0];
        if (getSignedUrl(target.id)) return;
        getVolumeUrl(target.id)
            .then((data) => setSignedUrl(target.id, data.url))
            .catch(() => {});
    }, [volumes, volumeProgress]);

    async function handleStatusChange(status: ReadingStatus) {
        if (!work) return;
        setSavingStatus(true);
        try {
            await setReadingStatusApi(work.id, status);
            setReadingStatus(status);
            setShowStatusMenu(false);
        } catch {
            toast.error("Erro ao atualizar lista de leitura");
        } finally {
            setSavingStatus(false);
        }
    }

    async function handleRemoveFromList() {
        if (!work || !readingStatus) return;
        setSavingStatus(true);
        try {
            await removeFromReadingList(work.id);
            setReadingStatus(null);
            setShowStatusMenu(false);
        } catch {
            toast.error("Erro ao remover da lista");
        } finally {
            setSavingStatus(false);
        }
    }

    async function handleRate(score: number) {
        if (!work || savingRating) return;
        setSavingRating(true);
        try {
            const data = userRating !== null
                ? await updateRating(work.id, score)
                : await createRating(work.id, score);
            setUserRating(score);
            setAvgRating(Number(data.avgRating));
            setRatingCount(data.ratingCount);
        } catch {
            toast.error("Erro ao salvar avaliação");
        } finally {
            setSavingRating(false);
        }
    }

    async function handleRemoveRating() {
        if (!work || userRating === null || savingRating) return;
        setSavingRating(true);
        try {
            await deleteRating(work.id);
            setUserRating(null);
            // busca avg/count atualizado
            const data = await getWork(work.slug);
            setAvgRating(Number(data.avgRating));
            setRatingCount(data.ratingCount);
        } catch {
            toast.error("Erro ao remover avaliação");
        } finally {
            setSavingRating(false);
        }
    }

    const canModify = user && work && (
        user.role === "ADMIN" || user.id === work.ownerId
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
        if (!work || !volumeFile) return;
        setUploading(true);
        try {
            const {uploadUrl, objectName, requiredHeaders} = await getVolumeUploadUrl(work.id, parseInt(volumeNumber));

            await uploadVolumeFile(uploadUrl, requiredHeaders, volumeFile);

            const data = await finalizeVolumeUpload(work.id, objectName, parseInt(volumeNumber));

            toast.success(`Volume ${volumeNumber} adicionado!`);
            setVolumes((prev) =>
                [...prev, data].sort((a, b) => a.volumeNumber - b.volumeNumber)
            );
            closeUploadModal();
        } catch (e) {
            const message = e instanceof Error ? e.message : "Erro desconhecido";
            toast.error(message);
        } finally {
            setUploading(false);
        }
    }

    async function handleDelete() {
        if (!work) return;
        if (!window.confirm(`Deletar "${work.title}"? Esta ação não pode ser desfeita.`)) return;
        setDeleting(true);
        try {
            await deleteWork(work.id);
            toast.success("Mangá removido");
            navigate("/biblioteca");
        } catch (e) {
            const message = e instanceof Error ? e.message : "Erro desconhecido";
            toast.error(message);
            setDeleting(false);
        }
    }

    if (loading) {
        return (
            <div className="max-w-7xl mx-auto px-4 md:px-8 pt-12 pb-10 grid gap-8 md:grid-cols-[240px_minmax(0,1fr)] md:gap-11 animate-pulse">
                <div className="aspect-[2/3] w-full max-w-[200px] md:max-w-none rounded-lg bg-card screentone"/>
                <div className="flex flex-col gap-3 md:pt-8">
                    <div className="h-3 w-40 bg-card"/>
                    <div className="h-10 w-3/4 bg-card"/>
                    <div className="h-4 w-1/2 bg-card"/>
                </div>
            </div>
        );
    }

    if (!work) return null;

    const tagsByCategory = work.tags.reduce<Record<string, Tag[]>>((acc, tag) => {
        const cat = tag.category.name;
        if (!acc[cat]) acc[cat] = [];
        acc[cat].push(tag);
        return acc;
    }, {});

    const formatLabel = FORMAT_LABELS[work.format] ?? work.format;
    // Botão principal: retoma o volume mais avançado em andamento; se não houver, sugere
    // o volume seguinte ao último concluído; sem progresso nenhum, o primeiro.
    const firstVolume = volumes[0];
    const resumeVolume = [...volumes].reverse().find((v) => volumeProgress[v.id] && !volumeProgress[v.id].finished);
    const lastFinishedIndex = volumes.map((v) => volumeProgress[v.id]?.finished ?? false).lastIndexOf(true);
    const nextUnread = lastFinishedIndex >= 0 ? volumes[lastFinishedIndex + 1] : undefined;

    const {id: workId, title: workTitle, slug: workSlug} = work;
    function openReader(vol: Volume) {
        navigate(`/leitor/${vol.id}`, {
            state: {
                workId,
                workTitle,
                workSlug,
                volumeNumber: vol.volumeNumber,
                backUrl: `/biblioteca/${workSlug}`,
            }
        });
    }

    return (
        <div className="flex flex-col">
            {showUploadModal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 px-4">
                    <div className="bg-card border rounded-lg w-full max-w-md p-6 space-y-4 shadow-xl">
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
                                <FileInput id="modal-vol-file" ref={fileInputRef} accept=".pdf,application/pdf" onChange={(e) => setVolumeFile(e.target.files?.[0] ?? null)}/>
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

            {/* Herói: a capa emoldurada como página, sobre linhas de velocidade discretas */}
            <section className="border-b bg-[radial-gradient(ellipse_at_20%_50%,transparent_0_18%,var(--background)_70%),repeating-conic-gradient(from_0deg_at_20%_50%,color-mix(in_oklch,var(--paper)_5%,transparent)_0deg_1.2deg,transparent_1.2deg_4.5deg)]">
                <div className="max-w-7xl mx-auto px-4 md:px-8 pt-5 pb-10 grid gap-8 md:grid-cols-[240px_minmax(0,1fr)] md:gap-11">
                    <div className="flex flex-col gap-3.5">
                        <div className="flex items-center justify-between gap-2">
                            <Link to="/biblioteca" className="inline-flex items-center gap-1.5 text-[13px] text-muted-foreground hover:text-foreground">
                                <ChevronLeft className="size-3.5"/>
                                Biblioteca
                            </Link>
                        </div>
                        <div className="w-full max-w-[200px] md:max-w-none rounded-lg bg-paper p-2 shadow-[0_24px_60px_rgba(0,0,0,.6)]">
                            <WorkCover
                                title={work.title}
                                coverUrl={work.coverUrl}
                                alt={`Capa de ${work.title}`}
                                className="rounded-none border-[3px] border-ink shadow-none"
                            />
                        </div>
                    </div>

                    <div className="flex flex-col gap-5 md:pt-8">
                        <div className="flex flex-col gap-2">
                            <span className="font-mono text-xs uppercase tracking-widest text-muted-foreground">
                                {[formatLabel, work.originCountry, work.year].filter(Boolean).join(" · ")}
                            </span>
                            <h1 className="m-0 text-[32px] md:text-[48px] leading-none font-bold tracking-[-0.035em]">{work.title}</h1>
                            {work.alternativeTitles.length > 0 && (
                                <p className="m-0 text-[15px] text-muted-foreground">{work.alternativeTitles.join(" · ")}</p>
                            )}
                        </div>

                        <div className="flex flex-wrap gap-2">
                            <Badge variant="stamp" className="h-6 px-2.5 text-[11px]">{formatLabel}</Badge>
                            <Badge variant="meta" className="h-6">{STATUS_ORIGIN_LABELS[work.statusOrigin] ?? work.statusOrigin}</Badge>
                            {work.year && <Badge variant="meta" className="h-6">{work.year}</Badge>}
                            {work.originCountry && <Badge variant="meta" className="h-6">{work.originCountry}</Badge>}
                            {work.contentWarnings.map((w) => (
                                <Badge key={w} variant="meta" className="h-6 border-shu text-shu">
                                    {CONTENT_WARNING_LABELS[w] ?? w}
                                </Badge>
                            ))}
                        </div>

                        {work.synopsis && (
                            <p className="m-0 max-w-[680px] text-base leading-relaxed text-foreground/85">{work.synopsis}</p>
                        )}

                        <div className="flex flex-wrap items-center gap-2.5">
                            {firstVolume && (
                                <Button className="h-12 px-5 text-[15px] font-semibold" onClick={() => openReader(resumeVolume ?? nextUnread ?? firstVolume)}>
                                    <BookOpen className="size-[18px]"/>
                                    {resumeVolume
                                        ? `Continuar · Vol. ${resumeVolume.volumeNumber}`
                                        : nextUnread
                                            ? `Ler · Vol. ${nextUnread.volumeNumber}`
                                            : "Começar a ler"}
                                </Button>
                            )}

                            <div className="relative">
                                <Button
                                    variant="outline"
                                    className="h-12 px-4"
                                    aria-haspopup="menu"
                                    aria-expanded={showStatusMenu}
                                    onClick={() => setShowStatusMenu((s) => !s)}
                                    disabled={savingStatus}
                                >
                                    {readingStatus
                                        ? <Macron/>
                                        : <BookMarked className="size-4"/>}
                                    {readingStatus ? READING_STATUS_LABELS[readingStatus] : "Adicionar à lista"}
                                    <ChevronDown className="size-3.5"/>
                                </Button>
                                {showStatusMenu && (
                                    <div role="menu" className="absolute top-full left-0 mt-1 z-20 min-w-[180px] rounded-lg border bg-popover py-1 shadow-md">
                                        {READING_STATUS_OPTIONS.map((s) => (
                                            <button
                                                key={s}
                                                role="menuitemradio"
                                                aria-checked={readingStatus === s}
                                                className="flex w-full items-center gap-2 px-3 py-2.5 text-left text-sm hover:bg-muted"
                                                onClick={() => handleStatusChange(s)}
                                            >
                                                <Macron className={readingStatus === s ? "" : "invisible"}/>
                                                {READING_STATUS_LABELS[s]}
                                            </button>
                                        ))}
                                        {readingStatus && (
                                            <>
                                                <div className="my-1 border-t"/>
                                                <button
                                                    role="menuitem"
                                                    className="w-full px-3 py-2.5 pl-8 text-left text-sm text-destructive hover:bg-muted"
                                                    onClick={handleRemoveFromList}
                                                >
                                                    Remover da lista
                                                </button>
                                            </>
                                        )}
                                    </div>
                                )}
                            </div>

                            <div role="group" aria-label="Sua nota" className="ml-1 flex items-center">
                                {[1, 2, 3, 4, 5].map((star) => (
                                    <button
                                        key={star}
                                        aria-label={userRating === star ? `Remover nota ${star}` : `Dar nota ${star}`}
                                        aria-pressed={userRating === star}
                                        className={`flex h-11 w-9 items-center justify-center ${savingRating ? "cursor-not-allowed" : "cursor-pointer"}`}
                                        onMouseEnter={() => setHoverRating(star)}
                                        onMouseLeave={() => setHoverRating(null)}
                                        onClick={() => userRating === star ? handleRemoveRating() : handleRate(star)}
                                        disabled={savingRating}
                                    >
                                        <Star
                                            aria-hidden="true"
                                            className={`size-[22px] transition-colors ${(hoverRating ?? userRating ?? 0) >= star
                                                ? "fill-shu text-shu"
                                                : "text-muted-foreground/40"}`}
                                        />
                                    </button>
                                ))}
                                {userRating && <span className="ml-1 text-xs text-muted-foreground">sua nota</span>}
                            </div>
                        </div>

                        <div className="flex flex-wrap gap-4 font-mono text-xs text-muted-foreground">
                            {ratingCount > 0 && (
                                <span>média {avgRating.toLocaleString("pt-BR", {minimumFractionDigits: 1, maximumFractionDigits: 1})} · {ratingCount} {ratingCount === 1 ? "avaliação" : "avaliações"}</span>
                            )}
                            <span>{work.viewCount} {work.viewCount === 1 ? "visualização" : "visualizações"}</span>
                        </div>

                        {Object.keys(tagsByCategory).length > 0 && (
                            <div className="flex flex-wrap gap-7 pt-1">
                                {Object.entries(tagsByCategory).map(([cat, tags]) => (
                                    <div key={cat} className="flex flex-col gap-2">
                                        <span className="font-mono text-[11px] uppercase tracking-widest text-muted-foreground">{cat}</span>
                                        <div className="flex flex-wrap gap-1.5">
                                            {tags.map((t) => (
                                                <span key={t.id} className="rounded-sm bg-muted px-2.5 py-1 text-[13px]">{t.name}</span>
                                            ))}
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}

                        {canModify && (
                            <div className="flex flex-wrap gap-2 border-t pt-5">
                                <Button variant="outline" onClick={() => navigate(`/obras/${work.id}/editar`)}>
                                    <Pencil className="size-4"/>
                                    Editar
                                </Button>
                                <Button variant="destructive" onClick={handleDelete} disabled={deleting}>
                                    <Trash2 className="size-4"/>
                                    {deleting ? "Deletando…" : "Deletar"}
                                </Button>
                            </div>
                        )}
                    </div>
                </div>
            </section>

            {/* Volumes: cada um é um painel; o que tem progresso salvo fica em destaque */}
            <section className="max-w-7xl w-full mx-auto px-4 md:px-8 pt-9 pb-16 flex flex-col gap-5">
                <div className="flex items-end justify-between gap-4">
                    <div className="flex flex-col gap-2">
                        <Macron className="w-6"/>
                        <h2 className="m-0 text-[22px] font-semibold tracking-[-0.02em]">
                            Volumes <span className="font-mono text-sm font-medium text-muted-foreground">{volumes.length}</span>
                        </h2>
                    </div>
                    {canModify && (
                        <Button variant="outline" className="h-10" onClick={openUploadModal}>
                            <Upload className="size-4"/>
                            Adicionar volume
                        </Button>
                    )}
                </div>

                {volumes.length === 0 ? (
                    <EmptyState title="Nenhum volume disponível" description="Os volumes aparecem aqui assim que forem enviados."/>
                ) : (
                    <div className="grid grid-cols-[repeat(auto-fill,minmax(160px,1fr))] gap-3">
                        {volumes.map((vol) => {
                            const progress = volumeProgress[vol.id];
                            const finished = progress?.finished ?? false;
                            const reading = progress !== undefined && !finished;
                            const percent = progress?.totalPages
                                ? Math.round((progress.currentPage / progress.totalPages) * 100)
                                : undefined;
                            return (
                                <div
                                    key={vol.id}
                                    className={`flex flex-col gap-3.5 rounded-lg border bg-card p-4 ${reading ? "border-shu/55" : ""}`}
                                >
                                    <div className="flex items-baseline justify-between gap-2">
                                        <span className="text-[13px] text-muted-foreground">Volume</span>
                                        <span className="font-mono text-[28px] font-medium tracking-[-0.02em]">
                                            {String(vol.volumeNumber).padStart(2, "0")}
                                        </span>
                                    </div>
                                    <div className="flex flex-col gap-1.5">
                                        {percent !== undefined && (
                                            <div
                                                role="progressbar"
                                                aria-valuemin={0}
                                                aria-valuemax={100}
                                                aria-valuenow={percent}
                                                aria-label={`Progresso do volume ${vol.volumeNumber}`}
                                                className="h-1 bg-muted"
                                            >
                                                <div className={`h-full ${finished ? "bg-muted-foreground" : "bg-shu"}`} style={{width: `${percent}%`}}/>
                                            </div>
                                        )}
                                        <span className="flex items-center gap-2 font-mono text-[11px] text-muted-foreground">
                                            {reading && percent === undefined && <Macron/>}
                                            {progress ? progressLabel(progress) : formatBytes(vol.fileSizeBytes)}
                                        </span>
                                    </div>
                                    <Button
                                        variant={reading ? "default" : "outline"}
                                        className="h-10"
                                        onClick={() => openReader(vol)}
                                    >
                                        {finished ? "Reler" : reading ? "Continuar" : "Ler"}
                                        <span className="sr-only"> o volume {vol.volumeNumber}</span>
                                    </Button>
                                </div>
                            );
                        })}
                    </div>
                )}
            </section>
        </div>
    );
}
