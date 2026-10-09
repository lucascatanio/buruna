import {useEffect, useState} from "react";
import {useNavigate, useParams} from "react-router-dom";
import {
    deleteMyVolume,
    finalizeMyVolumeUpload,
    getMyWork,
    getMyVolumeUploadUrl,
    promoteWork,
    submitForApproval,
    updateMyWork,
} from "@/api/privateWorkApi";
import {uploadVolumeFile} from "@/api/volumeUpload";
import type {PrivateWork, Volume} from "@/types/work";
import {useAuthStore} from "@/store/authStore";
import {Button} from "@/components/ui/button";
import {Input} from "@/components/ui/input";
import {Label} from "@/components/ui/label";
import {Card, CardContent, CardHeader, CardTitle} from "@/components/ui/card";
import {PageHeader} from "@/components/PageHeader";
import {FileInput} from "@/components/ui/file-input";
import {toast} from "sonner";
import {Upload, Trash2, Pencil, Check, X, Globe, BookOpen, Send, AlertCircle, Clock} from "lucide-react";

function formatBytes(bytes: number): string {
    if (bytes === 0) return "0 B";
    const k = 1024;
    const sizes = ["B", "KB", "MB", "GB"];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return `${parseFloat((bytes / Math.pow(k, i)).toFixed(1))} ${sizes[i]}`;
}

export function PrivateWorkDetailPage() {
    const {id} = useParams<{ id: string }>();
    const navigate = useNavigate();
    const user = useAuthStore((s) => s.user);

    const [work, setWork] = useState<PrivateWork | null>(null);
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
    const [submitting, setSubmitting] = useState(false);

    const isCollab = user?.role === "COLLABORATOR" || user?.role === "ADMIN";

    useEffect(() => {
        if (!id) return;
        getMyWork(id)
            .then((data) => {
                setWork(data);
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
        if (!work) return;
        setSaving(true);
        try {
            const data = await updateMyWork(work.id, {
                title: editTitle.trim(),
                synopsis: editSynopsis.trim() || null,
            });
            setWork(data);
            setEditing(false);
            toast.success("Mangá atualizado");
        } catch (err: any) {
            toast.error(err.response?.data?.message ?? "Erro ao salvar");
        } finally {
            setSaving(false);
        }
    }

    async function handleAddVolume() {
        if (!work || !volumeFile) return;
        setUploadingVolume(true);
        try {
            const {uploadUrl, objectName, requiredHeaders} = await getMyVolumeUploadUrl(work.id, parseInt(volumeNumber));

            await uploadVolumeFile(uploadUrl, requiredHeaders, volumeFile);

            const data = await finalizeMyVolumeUpload(work.id, objectName, parseInt(volumeNumber));

            setWork(data);
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
        if (!work) return;
        if (!confirm(`Deletar Volume ${volume.volumeNumber}?`)) return;
        setDeletingVolumeId(volume.id);
        try {
            const data = await deleteMyVolume(work.id, volume.id);
            setWork(data);
            toast.success(`Volume ${volume.volumeNumber} removido`);
        } catch (err: any) {
            toast.error(err.response?.data?.message ?? "Erro ao deletar volume");
        } finally {
            setDeletingVolumeId(null);
        }
    }

    async function handleSubmitForApproval() {
        if (!work) return;
        if (!confirm(`Enviar "${work.title}" para aprovação? Um administrador precisará aceitá-la antes de ficar pública.`)) return;
        setSubmitting(true);
        try {
            const data = await submitForApproval(work.id);
            setWork(data);
            toast.success("Solicitação enviada! Aguarde a aprovação de um administrador.");
        } catch (err: any) {
            toast.error(err.response?.data?.message ?? "Erro ao solicitar publicação");
        } finally {
            setSubmitting(false);
        }
    }

    async function handlePromote() {
        if (!work) return;
        if (!confirm(`Promover "${work.title}" para a biblioteca pública? Ele ficará visível para todos os usuários.`)) return;
        setPromoting(true);
        try {
            await promoteWork(work.id);
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

    if (!work) return null;

    return (
        <div className="max-w-2xl mx-auto px-4 md:px-6 py-8 space-y-6">

            <PageHeader title={work.title} back={{to: "/colecao", label: "Minha Coleção"}}/>

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
                                    setEditTitle(work.title);
                                    setEditSynopsis(work.synopsis ?? "");
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
                    {work.coverUrl && !editing && (
                        <img
                            src={work.coverUrl}
                            alt={work.title}
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
                        work.synopsis && (
                            <p className="text-sm text-muted-foreground">{work.synopsis}</p>
                        )
                    )}
                </CardContent>
            </Card>

            <Card>
                <CardHeader className="pb-3">
                    <CardTitle className="text-base">
                        Volumes ({work.volumes.length})
                    </CardTitle>
                </CardHeader>
                <CardContent className="space-y-4">
                    {work.volumes.length > 0 ? (
                        <div className="space-y-1">
                            {[...work.volumes]
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
                                            size="sm"
                                            className="h-7 text-xs gap-1"
                                            onClick={() => navigate(`/leitor/${v.id}`, {
                                                state: {
                                                    workId: work.id,
                                                    workTitle: work.title,
                                                    volumeNumber: v.volumeNumber,
                                                    backUrl: `/colecao/${work.id}`,
                                                }
                                            })}
                                        >
                                            <BookOpen className="w-3.5 h-3.5"/>
                                            Ler
                                        </Button>
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
                        <p className="m-0 font-mono text-xs text-muted-foreground">Nenhum volume ainda. Envie o primeiro abaixo.</p>
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
                                <FileInput id="add-volume-file" accept=".pdf,.epub,.mobi" onChange={(e) => setVolumeFile(e.target.files?.[0] ?? null)}/>
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

            {/* promote - admin pode tornar público diretamente */}
            {isCollab && !work.submissionStatus && (
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

            {/* submissão para aprovação - qualquer usuário */}
            {!isCollab && work.submissionStatus === null && (
                <Card className="border-dashed border-primary/50">
                    <CardContent
                        className="pt-5 pb-5 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
                        <div>
                            <p className="text-sm font-medium">Solicitar publicação</p>
                            <p className="text-xs text-muted-foreground mt-0.5">
                                Envie para aprovação de um administrador. Se aprovado, ficará visível na biblioteca pública.
                            </p>
                        </div>
                        <Button
                            variant="default"
                            size="sm"
                            className="shrink-0"
                            onClick={handleSubmitForApproval}
                            disabled={submitting}
                        >
                            <Send className="w-4 h-4 mr-1.5"/>
                            {submitting ? "Enviando…" : "Solicitar publicação"}
                        </Button>
                    </CardContent>
                </Card>
            )}

            {/* status: pendente */}
            {work.submissionStatus === "PENDING" && (
                <Card className="border-dashed border-shu/50 bg-shu/5">
                    <CardContent className="pt-5 pb-5 flex items-start gap-3">
                        <Clock className="w-5 h-5 text-shu shrink-0 mt-0.5"/>
                        <div>
                            <p className="text-sm font-medium text-foreground">
                                Aguardando aprovação
                            </p>
                            <p className="text-xs text-muted-foreground mt-0.5">
                                Sua solicitação foi enviada. Um administrador irá revisá-la em breve.
                            </p>
                        </div>
                    </CardContent>
                </Card>
            )}

            {/* status: rejeitado */}
            {work.submissionStatus === "REJECTED" && (
                <Card className="border-dashed border-destructive/50 bg-destructive/5">
                    <CardContent className="pt-5 pb-5 space-y-3">
                        <div className="flex items-start gap-3">
                            <AlertCircle className="w-5 h-5 text-destructive shrink-0 mt-0.5"/>
                            <div>
                                <p className="text-sm font-medium text-destructive">
                                    Publicação rejeitada
                                </p>
                                {work.rejectionReason && (
                                    <p className="text-xs text-muted-foreground mt-1">
                                        Motivo: {work.rejectionReason}
                                    </p>
                                )}
                            </div>
                        </div>
                        <Button
                            variant="outline"
                            size="sm"
                            onClick={handleSubmitForApproval}
                            disabled={submitting}
                        >
                            <Send className="w-4 h-4 mr-1.5"/>
                            {submitting ? "Enviando…" : "Reenviar para aprovação"}
                        </Button>
                    </CardContent>
                </Card>
            )}
        </div>
    );
}