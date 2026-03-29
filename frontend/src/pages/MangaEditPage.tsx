import {useEffect, useState} from "react";
import {useParams, useNavigate} from "react-router-dom";
import api from "@/lib/axios";
import {Button} from "@/components/ui/button";
import {Input} from "@/components/ui/input";
import {Label} from "@/components/ui/label";
import {Card, CardContent, CardHeader, CardTitle} from "@/components/ui/card";
import {TagSelector} from "@/components/TagSelector";
import {toast} from "sonner";
import {ArrowLeft, Plus, X, Upload, Trash2} from "lucide-react";

const FORMAT_OPTIONS = [
    {value: "MANGA", label: "Mangá"},
    {value: "MANHWA", label: "Manhwa"},
    {value: "MANHUA", label: "Manhua"},
    {value: "WEBTOON", label: "Webtoon"},
    {value: "ONE_SHOT", label: "One-shot"},
    {value: "LIVRO", label: "Livro"},
];

const STATUS_ORIGIN_OPTIONS = [
    {value: "ONGOING", label: "Em andamento"},
    {value: "COMPLETED", label: "Completo"},
    {value: "HIATUS", label: "Hiato"},
    {value: "CANCELLED", label: "Cancelado"},
];

const STATUS_SITE_OPTIONS = [
    {value: "INCOMPLETE", label: "Incompleto"},
    {value: "COMPLETE", label: "Completo"},
];

const CONTENT_WARNING_OPTIONS = [
    {value: "NSFW", label: "NSFW (conteúdo adulto)"},
    {value: "GORE", label: "Gore (violência extrema)"},
    {value: "GATILHO_SUICIDIO", label: "Gatilho: Suicídio"},
    {value: "GATILHO_ABUSO", label: "Gatilho: Abuso"},
    {value: "GATILHO_TRAUMA", label: "Gatilho: Trauma"},
];

interface MangaRequest {
    title: string;
    alternativeTitles: string[];
    synopsis?: string | null;
    coverBase64?: string;
    format: string;
    originCountry?: string | null;
    statusOrigin: string;
    statusSite: string;
    year?: number | null;
    contentWarnings: string[];
    tagIds: string[];
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
    tags: {id: string; name: string; slug: string; category: {id: string; name: string}}[];
    volumes: Volume[];
}

function formatBytes(bytes: number): string {
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function MangaEditPage() {
    const {id} = useParams<{id: string}>();
    const navigate = useNavigate();

    const [manga, setManga] = useState<MangaDetail | null>(null);
    const [loading, setLoading] = useState(true);
    const [submitting, setSubmitting] = useState(false);

    const [form, setForm] = useState({
        title: "", synopsis: "", format: "MANGA",
        originCountry: "", statusOrigin: "ONGOING",
        statusSite: "INCOMPLETE", year: "",
    });
    const [alternativeTitles, setAlternativeTitles] = useState<string[]>([]);
    const [altTitleInput, setAltTitleInput] = useState("");
    const [contentWarnings, setContentWarnings] = useState<string[]>([]);
    const [tagIds, setTagIds] = useState<string[]>([]);
    const [coverBase64, setCoverBase64] = useState<string | null>(null);
    const [coverPreview, setCoverPreview] = useState<string | null>(null);

    const [volumes, setVolumes] = useState<Volume[]>([]);
    const [volumeNumber, setVolumeNumber] = useState("1");
    const [volumeFile, setVolumeFile] = useState<File | null>(null);
    const [uploadingVolume, setUploadingVolume] = useState(false);
    const [deletingVolumeId, setDeletingVolumeId] = useState<string | null>(null);

    useEffect(() => {
        if (!id) return;
        api.get<MangaDetail>(`/mangas/${id}`)
            .then(({data}) => populateForm(data))
            .catch(() => {
                toast.error("Mangá não encontrado.");
                navigate("/biblioteca");
            })
            .finally(() => setLoading(false));
    }, [id, navigate]);

    function populateForm(m: MangaDetail) {
        setManga(m);
        setForm({
            title: m.title,
            synopsis: m.synopsis ?? "",
            format: m.format,
            originCountry: m.originCountry ?? "",
            statusOrigin: m.statusOrigin,
            statusSite: m.statusSite,
            year: m.year ? String(m.year) : "",
        });
        setAlternativeTitles(m.alternativeTitles);
        setContentWarnings(m.contentWarnings);
        setTagIds(m.tags.map((t) => t.id));
        setCoverPreview(m.coverUrl);
        setVolumes(m.volumes);
        const nextVolume = m.volumes.length > 0
            ? Math.max(...m.volumes.map((v) => v.volumeNumber)) + 1
            : 1;
        setVolumeNumber(String(nextVolume));
    }

    function handleCoverChange(e: React.ChangeEvent<HTMLInputElement>) {
        const file = e.target.files?.[0];
        if (!file) return;
        const reader = new FileReader();
        reader.onload = () => {
            const result = reader.result as string;
            setCoverBase64(result);
            setCoverPreview(result);
        };
        reader.readAsDataURL(file);
    }

    function addAltTitle() {
        const trimmed = altTitleInput.trim();
        if (trimmed && !alternativeTitles.includes(trimmed))
            setAlternativeTitles((prev) => [...prev, trimmed]);
        setAltTitleInput("");
    }

    function toggleWarning(value: string) {
        setContentWarnings((prev) =>
            prev.includes(value) ? prev.filter((w) => w !== value) : [...prev, value]
        );
    }

    async function handleSubmit(e: React.FormEvent) {
        e.preventDefault();
        if (!manga) return;
        setSubmitting(true);
        try {
            const payload: MangaRequest = {
                title: form.title,
                alternativeTitles,
                synopsis: form.synopsis || null,
                format: form.format,
                originCountry: form.originCountry || null,
                statusOrigin: form.statusOrigin,
                statusSite: form.statusSite,
                year: form.year ? Number(form.year) : null,
                contentWarnings,
                tagIds,
            };
            if (coverBase64) payload.coverBase64 = coverBase64;

            const {data} = await api.put(`/mangas/${manga.id}`, payload);
            toast.success("Mangá atualizado");
            navigate(`/biblioteca/${data.slug}`);
        } catch (e) {
            const message = e instanceof Error ? e.message : "Erro desconhecido";
            toast.error(message);
            setSubmitting(false);
        }
    }

    async function handleUploadVolume() {
        if (!manga || !volumeFile) return;
        setUploadingVolume(true);
        try {
            const {data: {uploadUrl, objectName}} = await api.post(
                `/mangas/${manga.id}/volumes/upload-url`,
                {volumeNumber: parseInt(volumeNumber)}
            );
            const uploadRes = await fetch(uploadUrl, {
                method: "PUT",
                headers: {"Content-Type": "application/pdf"},
                body: volumeFile,
            });
            if (!uploadRes.ok) throw new Error(`Upload GCS falhou: ${uploadRes.status}`);
            const {data} = await api.post(`/mangas/${manga.id}/volumes/finalize`, {
                objectName,
                volumeNumber: parseInt(volumeNumber),
            });
            toast.success(`Volume ${volumeNumber} enviado!`);
            setVolumes((prev) => [...prev, data].sort((a, b) => a.volumeNumber - b.volumeNumber));
            setVolumeNumber(String(Number(volumeNumber) + 1));
            setVolumeFile(null);
            const fileInput = document.getElementById("vol-file") as HTMLInputElement;
            if (fileInput) fileInput.value = "";
        } catch (e) {
            const message = e instanceof Error ? e.message : "Erro desconhecido";
            toast.error(message);
        } finally {
            setUploadingVolume(false);
        }
    }

    async function handleDeleteVolume(vol: Volume) {
        if (!manga) return;
        if (!window.confirm(`Remover Volume ${vol.volumeNumber}?`)) return;
        setDeletingVolumeId(vol.id);
        try {
            await api.delete(`/mangas/${manga.id}/volumes/${vol.id}`);
            toast.success(`Volume ${vol.volumeNumber} removido`);
            setVolumes((prev) => prev.filter((v) => v.id !== vol.id));
        } catch (e) {
            const message = e instanceof Error ? e.message : "Erro desconhecido";
            toast.error(message);
        } finally {
            setDeletingVolumeId(null);
        }
    }

    if (loading) {
        return (
            <div className="max-w-2xl mx-auto px-4 py-8">
                <div className="h-6 bg-muted rounded w-32 animate-pulse mb-6"/>
                <div className="space-y-4">
                    {Array.from({length: 6}).map((_, i) => (
                        <div key={i} className="h-10 bg-muted rounded animate-pulse"/>
                    ))}
                </div>
            </div>
        );
    }

    return (
        <div className="max-w-2xl mx-auto px-4 md:px-6 py-8 space-y-8">
            <div className="flex items-center gap-3">
                <Button variant="ghost" size="icon" onClick={() => navigate(-1)}>
                    <ArrowLeft className="w-4 h-4"/>
                </Button>
                <h1 className="text-2xl font-bold">Editar mangá</h1>
            </div>

            <form onSubmit={handleSubmit} className="space-y-6">
                <div className="space-y-2">
                    <Label>Capa</Label>
                    <div className="flex gap-4 items-start">
                        {coverPreview && (
                            <div className="relative w-24 shrink-0">
                                <img src={coverPreview} alt="Capa" className="w-24 aspect-[2/3] object-cover rounded-md border"/>
                                <button
                                    type="button"
                                    className="absolute -top-1.5 -right-1.5 bg-destructive text-destructive-foreground rounded-full w-5 h-5 flex items-center justify-center"
                                    onClick={() => {setCoverBase64(null); setCoverPreview(null);}}
                                >
                                    <X className="w-3 h-3"/>
                                </button>
                            </div>
                        )}
                        <Input type="file" accept="image/*" onChange={handleCoverChange}/>
                    </div>
                </div>

                <div className="space-y-2">
                    <Label htmlFor="title">Título *</Label>
                    <Input
                        id="title"
                        value={form.title}
                        onChange={(e) => setForm((p) => ({...p, title: e.target.value}))}
                        required
                    />
                </div>

                <div className="space-y-2">
                    <Label>Títulos alternativos</Label>
                    {alternativeTitles.length > 0 && (
                        <div className="flex flex-wrap gap-1 mb-2">
                            {alternativeTitles.map((t) => (
                                <span key={t} className="flex items-center gap-1 border rounded-md px-2 py-0.5 text-sm">
                                    {t}
                                    <button type="button" onClick={() => setAlternativeTitles((p) => p.filter((x) => x !== t))}>
                                        <X className="w-3 h-3"/>
                                    </button>
                                </span>
                            ))}
                        </div>
                    )}
                    <div className="flex gap-2">
                        <Input
                            placeholder="Adicionar título alternativo"
                            value={altTitleInput}
                            onChange={(e) => setAltTitleInput(e.target.value)}
                            onKeyDown={(e) => {if (e.key === "Enter") {e.preventDefault(); addAltTitle();}}}
                        />
                        <Button type="button" variant="outline" onClick={addAltTitle}>
                            <Plus className="w-4 h-4"/>
                        </Button>
                    </div>
                </div>

                <div className="space-y-2">
                    <Label>Sinopse</Label>
                    <textarea
                        className="w-full border rounded-md px-3 py-2 text-sm bg-background resize-none min-h-[100px]"
                        value={form.synopsis}
                        onChange={(e) => setForm((p) => ({...p, synopsis: e.target.value}))}
                    />
                </div>

                <div className="grid grid-cols-2 gap-3">
                    <div className="space-y-2">
                        <Label>Formato</Label>
                        <select className="w-full border rounded-md px-3 py-2 text-sm bg-background"
                                value={form.format} onChange={(e) => setForm((p) => ({...p, format: e.target.value}))}>
                            {FORMAT_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
                        </select>
                    </div>
                    <div className="space-y-2">
                        <Label>Status de publicação</Label>
                        <select className="w-full border rounded-md px-3 py-2 text-sm bg-background"
                                value={form.statusOrigin} onChange={(e) => setForm((p) => ({...p, statusOrigin: e.target.value}))}>
                            {STATUS_ORIGIN_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
                        </select>
                    </div>
                    <div className="space-y-2">
                        <Label>Status no site</Label>
                        <select className="w-full border rounded-md px-3 py-2 text-sm bg-background"
                                value={form.statusSite} onChange={(e) => setForm((p) => ({...p, statusSite: e.target.value}))}>
                            {STATUS_SITE_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
                        </select>
                    </div>
                    <div className="space-y-2">
                        <Label>Ano</Label>
                        <Input type="number" min="1900" max={new Date().getFullYear() + 1}
                               value={form.year} onChange={(e) => setForm((p) => ({...p, year: e.target.value}))}/>
                    </div>
                </div>

                <div className="space-y-2">
                    <Label>País de origem</Label>
                    <Input
                        placeholder="Ex: Japan"
                        value={form.originCountry}
                        onChange={(e) => setForm((p) => ({...p, originCountry: e.target.value}))}
                    />
                </div>

                <div className="space-y-2">
                    <Label>Avisos de conteúdo</Label>
                    <div className="flex flex-wrap gap-2">
                        {CONTENT_WARNING_OPTIONS.map((w) => (
                            <button key={w.value} type="button" onClick={() => toggleWarning(w.value)}
                                    className={`text-xs border rounded-md px-2.5 py-1 transition-colors ${
                                        contentWarnings.includes(w.value)
                                            ? "bg-destructive text-destructive-foreground border-destructive"
                                            : "hover:bg-muted"
                                    }`}>
                                {w.label}
                            </button>
                        ))}
                    </div>
                </div>

                <div className="space-y-2">
                    <Label>Tags</Label>
                    <TagSelector selectedIds={tagIds} onChange={setTagIds} excludeCategories={["Aviso de Conteúdo"]}/>
                </div>

                <Button type="submit" className="w-full" disabled={submitting}>
                    {submitting ? "Salvando…" : "Salvar alterações"}
                </Button>
            </form>

            <Card>
                <CardHeader>
                    <CardTitle className="text-base">Volumes ({volumes.length})</CardTitle>
                </CardHeader>
                <CardContent className="space-y-4">
                    {volumes.length > 0 && (
                        <div className="space-y-2">
                            {volumes.map((vol) => (
                                <div key={vol.id} className="flex items-center justify-between border rounded-md px-3 py-2">
                                    <div>
                                        <p className="text-sm font-medium">Volume {vol.volumeNumber}</p>
                                        <p className="text-xs text-muted-foreground">{formatBytes(vol.fileSizeBytes)}</p>
                                    </div>
                                    <Button
                                        variant="ghost"
                                        size="icon"
                                        className="text-destructive hover:text-destructive h-8 w-8"
                                        onClick={() => handleDeleteVolume(vol)}
                                        disabled={deletingVolumeId === vol.id}
                                    >
                                        <Trash2 className="w-4 h-4"/>
                                    </Button>
                                </div>
                            ))}
                        </div>
                    )}

                    <div className="border-t pt-4 space-y-3">
                        <p className="text-sm font-medium">Adicionar volume</p>
                        <div className="grid grid-cols-2 gap-3">
                            <div className="space-y-1.5">
                                <Label htmlFor="vol-number">Número</Label>
                                <Input
                                    id="vol-number"
                                    type="number"
                                    min="1"
                                    value={volumeNumber}
                                    onChange={(e) => setVolumeNumber(e.target.value)}
                                />
                            </div>
                            <div className="space-y-1.5">
                                <Label htmlFor="vol-file">Arquivo</Label>
                                <Input
                                    id="vol-file"
                                    type="file"
                                    accept=".pdf,.epub,.mobi"
                                    onChange={(e) => setVolumeFile(e.target.files?.[0] ?? null)}
                                />
                            </div>
                        </div>
                        <Button
                            className="w-full"
                            onClick={handleUploadVolume}
                            disabled={!volumeFile || uploadingVolume}
                        >
                            <Upload className="w-4 h-4 mr-1.5"/>
                            {uploadingVolume ? "Enviando…" : "Enviar volume"}
                        </Button>
                    </div>
                </CardContent>
            </Card>
        </div>
    );
}