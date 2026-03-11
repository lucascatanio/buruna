import {useEffect, useState} from "react";
import {useNavigate, useParams} from "react-router-dom";
import api from "@/lib/axios";
import {useAuthStore} from "@/store/authStore";
import {Button} from "@/components/ui/button";
import {Input} from "@/components/ui/input";
import {Label} from "@/components/ui/label";
import {Card, CardContent, CardHeader, CardTitle} from "@/components/ui/card";
import {toast} from "sonner";
import {ArrowLeft, Upload, Trash2, Pencil, Check, X, Globe} from "lucide-react";

interface Volume {
    id: string;
    volumeNumber: number;
    fileSizeBytes: number;
    createdAt: string;
}

interface PrivateManga {
    id: string;
    title: string;
    synopsis: string | null;
    coverUrl: string | null;
    volumes: Volume[];
    createdAt: string;
    updatedAt: string;
}

function formatBytes(bytes: number): string {
    if (bytes === 0) return "0 B";
    const k = 1024;
    const sizes = ["B", "KB", "MB", "GB"];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return `${parseFloat((bytes / Math.pow(k, i)).toFixed(1))} ${sizes[i]}`;
}

export function PrivateMangaDetailPage() {
    const {id} = useParams<{ id: string }>();
    const navigate = useNavigate();
    const user = useAuthStore((s) => s.user);

    const [manga, setManga] = useState<PrivateManga | null>(null);
    const [loading, setLoading] = useState(true);

    const [editing, setEditing] = useState(false);
    const [editTitle, setEditTitle] = useState("");
    const [editSynopsis, setEditSynopsis] = useState("");
    const [saving, setSaving] = useState(false);

    const [volumeNumber, setVolumeNumber] = useState("1");
    const [volumeFile, setVolumeFile] = useState<File | null>(null);
    const [uploadingVolume, setUploadingVolume] = useState(false);

    const [deletingVolumeId, setDeletingVolumeId] = useState<string | null>(null);

    const [promoting, setPromoting] = useState(false);

    const isCollab = user?.role === "COLLABORATOR" || user?.role === "ADMIN";

    useEffect(() => {
        if (!id) return;
        api.get<PrivateManga>(`/my/mangas/${id}`)
            .then(({data}) => {
                setManga(data);
                setEditTitle(data.title);
                setEditSynopsis(data.synopsis ?? "");
                const maxVol = data.volumes.reduce((max, v) => Math.max(max, v.volumeNumber), 0);
                setVolumeNumber(String(maxVol + 1));
            })
            .catch(() => {
                toast.error("Mangá não encontrado");
                navigate("/colecao");
            })
            .finally(() => setLoading(false));
    }, [id, navigate]);

    async function handleSaveEdit() {
        if (!manga) return;
        setSaving(true);
        try {
            const {data} = await api.put<PrivateManga>(`/my/mangas/${manga.id}`, {
                title: editTitle.trim(),
                synopsis: editSynopsis.trim() || null,
            });
            setManga(data);
            setEditing(false);
            toast.success("Mangá atualizado");
        } catch (err: any) {
            toast.error(err.response?.data?.message ?? "Erro ao salvar");
        } finally {
            setSaving(false);
        }
    }

    async function handleAddVolume() {
        if (!manga || !volumeFile) return;
        setUploadingVolume(true);
        try {
            const formData = new FormData();
            formData.append("file", volumeFile);
            formData.append("volumeNumber", volumeNumber);
            const {data} = await api.post<PrivateManga>(`/my/mangas/${manga.id}/volumes`, formData);
            setManga(data);
            const maxVol = data.volumes.reduce((max, v) => Math.max(max, v.volumeNumber), 0);
            setVolumeNumber(String(maxVol + 1));
            setVolumeFile(null);
            const fileInput = document.getElementById("add-volume-file") as HTMLInputElement;
            if (fileInput) fileInput.value = "";
            toast.success(`Volume ${volumeNumber} adicionado`);
        } catch (err: any) {
            toast.error(err.response?.data?.message ?? "Erro ao enviar volume");
        } finally {
            setUploadingVolume(false);
        }
    }

    async function handleDeleteVolume(volume: Volume) {
        if (!manga) return;
        if (!confirm(`Deletar Volume ${volume.volumeNumber}?`)) return;
        setDeletingVolumeId(volume.id);
        try {
            const {data} = await api.delete<PrivateManga>(`/my/mangas/${manga.id}/volumes/${volume.id}`);
            setManga(data);
            toast.success(`Volume ${volume.volumeNumber} removido`);
        } catch (err: any) {
            toast.error(err.response?.data?.message ?? "Erro ao deletar volume");
        } finally {
            setDeletingVolumeId(null);
        }
    }

    async function handlePromote() {
        if (!manga) return;
        if (!confirm(`Promover "${manga.title}" para a biblioteca pública? Ele ficará visível para todos os usuários.`)) return;
        setPromoting(true);
        try {
            await api.post(`/my/mangas/${manga.id}/promote`);
            toast.success("Mangá publicado na biblioteca!");
            navigate("/colecao");
        } catch (err: any) {
            toast.error(err.response?.data?.message ?? "Erro ao promover mangá");
        } finally {
            setPromoting(false);
        }
    }

    if (loading) {
        return (
            <div className="max-w-2xl mx-auto px-4 md:px-6 py-8 space-y-4">
                <div className="h-8 w-48 bg-muted animate-pulse rounded"/>
                <div className="h-32 bg-muted animate-pulse rounded-lg"/>
                <div className="h-48 bg-muted animate-pulse rounded-lg"/>
            </div>
        );
    }

    if (!manga) return null;

    return (
        <div className="max-w-2xl mx-auto px-4 md:px-6 py-8 space-y-6">

            <div className="flex items-center gap-3">
                <Button variant="ghost" size="icon" onClick={() => navigate("/colecao")}>
                    <ArrowLeft className="w-4 h-4"/>
                </Button>
                <h1 className="text-xl font-bold truncate flex-1">{manga.title}</h1>
            </div>

            <Card>
                <CardHeader className="pb-2 flex flex-row items-center justify-between">
                    <CardTitle className="text-base">Informações</CardTitle>
                    {!editing ? (
                        <Button
                            variant="ghost"
                            size="sm"
                            className="h-7 gap-1.5 text-xs"
                            onClick={() => setEditing(true)}
                        >
                            <Pencil className="w-3.5 h-3.5"/>
                            Editar
                        </Button>
                    ) : (
                        <div className="flex gap-1">
                            <Button
                                variant="ghost"
                                size="sm"
                                className="h-7 gap-1.5 text-xs text-muted-foreground"
                                onClick={() => {
                                    setEditing(false);
                                    setEditTitle(manga.title);
                                    setEditSynopsis(manga.synopsis ?? "");
                                }}
                            >
                                <X className="w-3.5 h-3.5"/>
                                Cancelar
                            </Button>
                            <Button
                                size="sm"
                                className="h-7 gap-1.5 text-xs"
                                onClick={handleSaveEdit}
                                disabled={saving || !editTitle.trim()}
                            >
                                <Check className="w-3.5 h-3.5"/>
                                {saving ? "Salvando…" : "Salvar"}
                            </Button>
                        </div>
                    )}
                </CardHeader>
                <CardContent className="space-y-3">
                    {manga.coverUrl && !editing && (
                        <img
                            src={manga.coverUrl}
                            alt={manga.title}
                            className="w-24 aspect-[2/3] object-cover rounded-md border"
                        />
                    )}
                    {editing ? (
                        <div className="space-y-3">
                            <div className="space-y-1.5">
                                <Label htmlFor="edit-title">Título</Label>
                                <Input
                                    id="edit-title"
                                    value={editTitle}
                                    onChange={(e) => setEditTitle(e.target.value)}
                                />
                            </div>
                            <div className="space-y-1.5">
                                <Label htmlFor="edit-synopsis">Sinopse</Label>
                                <textarea
                                    id="edit-synopsis"
                                    className="w-full border rounded-md px-3 py-2 text-sm bg-background resize-none min-h-[80px]"
                                    value={editSynopsis}
                                    onChange={(e) => setEditSynopsis(e.target.value)}
                                />
                            </div>
                        </div>
                    ) : (
                        manga.synopsis && (
                            <p className="text-sm text-muted-foreground">{manga.synopsis}</p>
                        )
                    )}
                </CardContent>
            </Card>

            <Card>
                <CardHeader className="pb-3">
                    <CardTitle className="text-base">
                        Volumes ({manga.volumes.length})
                    </CardTitle>
                </CardHeader>
                <CardContent className="space-y-4">
                    {manga.volumes.length > 0 ? (
                        <div className="space-y-1">
                            {[...manga.volumes]
                                .sort((a, b) => a.volumeNumber - b.volumeNumber)
                                .map((v) => (
                                    <div
                                        key={v.id}
                                        className="flex items-center justify-between py-2 px-3 rounded-md hover:bg-muted/50 transition-colors"
                                    >
                                        <div>
                                            <span className="text-sm font-medium">Volume {v.volumeNumber}</span>
                                            <span className="text-xs text-muted-foreground ml-2">
                                                {formatBytes(v.fileSizeBytes)}
                                            </span>
                                        </div>
                                        <Button
                                            variant="ghost"
                                            size="icon"
                                            className="h-7 w-7 text-muted-foreground hover:text-destructive"
                                            disabled={deletingVolumeId === v.id}
                                            onClick={() => handleDeleteVolume(v)}
                                        >
                                            <Trash2 className="w-3.5 h-3.5"/>
                                        </Button>
                                    </div>
                                ))}
                        </div>
                    ) : (
                        <p className="text-sm text-muted-foreground">Nenhum volume ainda.</p>
                    )}

                    <div className="pt-2 border-t space-y-3">
                        <p className="text-sm font-medium">Adicionar volume</p>
                        <div className="grid grid-cols-2 gap-3">
                            <div className="space-y-1.5">
                                <Label htmlFor="vol-num">Número</Label>
                                <Input
                                    id="vol-num"
                                    type="number"
                                    min="1"
                                    value={volumeNumber}
                                    onChange={(e) => setVolumeNumber(e.target.value)}
                                />
                            </div>
                            <div className="space-y-1.5">
                                <Label htmlFor="add-volume-file">Arquivo</Label>
                                <Input
                                    id="add-volume-file"
                                    type="file"
                                    accept=".pdf,.epub,.mobi"
                                    onChange={(e) => setVolumeFile(e.target.files?.[0] ?? null)}
                                />
                            </div>
                        </div>
                        <Button
                            className="w-full"
                            onClick={handleAddVolume}
                            disabled={!volumeFile || uploadingVolume}
                        >
                            <Upload className="w-4 h-4 mr-1.5"/>
                            {uploadingVolume ? "Enviando…" : "Enviar volume"}
                        </Button>
                    </div>
                </CardContent>
            </Card>

            {/* promote só para collab/admin */}
            {isCollab && (
                <Card className="border-dashed">
                    <CardContent
                        className="pt-5 pb-5 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
                        <div>
                            <p className="text-sm font-medium">Publicar na biblioteca</p>
                            <p className="text-xs text-muted-foreground mt-0.5">
                                Este mangá ficará visível para todos os usuários.
                            </p>
                        </div>
                        <Button
                            variant="outline"
                            size="sm"
                            className="shrink-0"
                            onClick={handlePromote}
                            disabled={promoting}
                        >
                            <Globe className="w-4 h-4 mr-1.5"/>
                            {promoting ? "Publicando…" : "Tornar público"}
                        </Button>
                    </CardContent>
                </Card>
            )}
        </div>
    );
}