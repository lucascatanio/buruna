import {useState} from "react";
import {useNavigate} from "react-router-dom";
import api from "@/lib/axios";
import {Button} from "@/components/ui/button";
import {Input} from "@/components/ui/input";
import {Label} from "@/components/ui/label";
import {Card, CardContent, CardHeader, CardTitle} from "@/components/ui/card";
import {TagSelector} from "@/components/TagSelector";
import {toast} from "sonner";
import {ArrowLeft, Plus, X, Upload, Check} from "lucide-react";

const FORMAT_OPTIONS = [
    {value: "MANGA", label: "Mangá"},
    {value: "MANHWA", label: "Manhwa"},
    {value: "MANHUA", label: "Manhua"},
    {value: "WEBTOON", label: "Webtoon"},
    {value: "ONE_SHOT", label: "One-shot"},
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

interface CreatedManga {
    id: string;
    slug: string;
    title: string;
}

export function MangaUploadPage() {
    const navigate = useNavigate();

    const [form, setForm] = useState({
        title: "",
        synopsis: "",
        format: "MANGA",
        originCountry: "",
        statusOrigin: "ONGOING",
        statusSite: "INCOMPLETE",
        year: "",
    });
    const [alternativeTitles, setAlternativeTitles] = useState<string[]>([]);
    const [altTitleInput, setAltTitleInput] = useState("");
    const [contentWarnings, setContentWarnings] = useState<string[]>([]);
    const [tagIds, setTagIds] = useState<string[]>([]);
    const [coverBase64, setCoverBase64] = useState<string | null>(null);
    const [coverPreview, setCoverPreview] = useState<string | null>(null);
    const [submitting, setSubmitting] = useState(false);

    const [createdManga, setCreatedManga] = useState<CreatedManga | null>(null);
    const [volumeNumber, setVolumeNumber] = useState("1");
    const [volumeFile, setVolumeFile] = useState<File | null>(null);
    const [uploadingVolume, setUploadingVolume] = useState(false);
    const [uploadedVolumes, setUploadedVolumes] = useState<number[]>([]);

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

    function removeAltTitle(title: string) {
        setAlternativeTitles((prev) => prev.filter((t) => t !== title));
    }

    function toggleWarning(value: string) {
        setContentWarnings((prev) =>
            prev.includes(value) ? prev.filter((w) => w !== value) : [...prev, value]
        );
    }

    async function handleSubmitManga(e: React.FormEvent) {
        e.preventDefault();
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

            const {data} = await api.post("/mangas", payload);
            setCreatedManga({id: data.id, slug: data.slug, title: data.title});
            toast.success("Mangá criado! Agora você pode adicionar volumes.");
        } catch (e) {
            const message = e instanceof Error ? e.message : "Erro desconhecido";
            toast.error(message);
        } finally {
            setSubmitting(false);
        }
    }

    async function handleUploadVolume() {
        if (!createdManga || !volumeFile) return;
        setUploadingVolume(true);
        try {
            const formData = new FormData();
            formData.append("file", volumeFile);
            formData.append("volumeNumber", volumeNumber);
            // sem Content-Type manual — o browser define com o boundary correto
            await api.post(`/mangas/${createdManga.id}/volumes`, formData);
            toast.success(`Volume ${volumeNumber} enviado!`);
            setUploadedVolumes((prev) => [...prev, Number(volumeNumber)]);
            setVolumeNumber(String(Number(volumeNumber) + 1));
            setVolumeFile(null);
            const fileInput = document.getElementById("volume-file") as HTMLInputElement;
            if (fileInput) fileInput.value = "";
        } catch (e) {
            const message = e instanceof Error ? e.message : "Erro desconhecido";
            toast.error(message);
        } finally {
            setUploadingVolume(false);
        }
    }

    if (createdManga) {
        return (
            <div className="max-w-2xl mx-auto px-4 md:px-6 py-8 space-y-6">
                <div className="flex items-center gap-3">
                    <div className="w-8 h-8 rounded-full bg-green-500/20 flex items-center justify-center">
                        <Check className="w-4 h-4 text-green-500"/>
                    </div>
                    <div>
                        <h2 className="text-lg font-semibold">{createdManga.title}</h2>
                        <p className="text-sm text-muted-foreground">Mangá criado com sucesso</p>
                    </div>
                </div>

                <Card>
                    <CardHeader>
                        <CardTitle className="text-base">Adicionar volumes</CardTitle>
                    </CardHeader>
                    <CardContent className="space-y-4">
                        {uploadedVolumes.length > 0 && (
                            <div className="flex flex-wrap gap-2">
                                {uploadedVolumes.map((n) => (
                                    <span key={n} className="text-xs border rounded-md px-2 py-1 text-muted-foreground">
                                        Vol. {n} ✓
                                    </span>
                                ))}
                            </div>
                        )}

                        <div className="grid grid-cols-2 gap-3">
                            <div className="space-y-1.5">
                                <Label htmlFor="volume-number">Número do volume</Label>
                                <Input
                                    id="volume-number"
                                    type="number"
                                    min="1"
                                    value={volumeNumber}
                                    onChange={(e) => setVolumeNumber(e.target.value)}
                                />
                            </div>
                            <div className="space-y-1.5">
                                <Label htmlFor="volume-file">Arquivo (PDF, EPUB, MOBI)</Label>
                                <Input
                                    id="volume-file"
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
                    </CardContent>
                </Card>

                <Button
                    variant="outline"
                    className="w-full"
                    onClick={() => navigate(`/biblioteca/${createdManga.slug}`)}
                >
                    Ver mangá na biblioteca
                </Button>
            </div>
        );
    }

    return (
        <div className="max-w-2xl mx-auto px-4 md:px-6 py-8 space-y-6">
            <div className="flex items-center gap-3">
                <Button variant="ghost" size="icon" onClick={() => navigate(-1)}>
                    <ArrowLeft className="w-4 h-4"/>
                </Button>
                <h1 className="text-2xl font-bold">Publicar mangá</h1>
            </div>

            <form onSubmit={handleSubmitManga} className="space-y-6">

                <div className="space-y-2">
                    <Label>Capa (opcional)</Label>
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
                        placeholder="Título da obra"
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
                                    <button type="button" onClick={() => removeAltTitle(t)}>
                                        <X className="w-3 h-3"/>
                                    </button>
                                </span>
                            ))}
                        </div>
                    )}
                    <div className="flex gap-2">
                        <Input
                            placeholder="Ex: Título em japonês"
                            value={altTitleInput}
                            onChange={(e) => setAltTitleInput(e.target.value)}
                            onKeyDown={(e) => {
                                if (e.key === "Enter") {e.preventDefault(); addAltTitle();}
                            }}
                        />
                        <Button type="button" variant="outline" onClick={addAltTitle}>
                            <Plus className="w-4 h-4"/>
                        </Button>
                    </div>
                </div>

                <div className="space-y-2">
                    <Label htmlFor="synopsis">Sinopse</Label>
                    <textarea
                        id="synopsis"
                        className="w-full border rounded-md px-3 py-2 text-sm bg-background resize-none min-h-[100px]"
                        placeholder="Descrição da obra"
                        value={form.synopsis}
                        onChange={(e) => setForm((p) => ({...p, synopsis: e.target.value}))}
                    />
                </div>

                <div className="grid grid-cols-2 gap-3">
                    <div className="space-y-2">
                        <Label>Formato</Label>
                        <select
                            className="w-full border rounded-md px-3 py-2 text-sm bg-background"
                            value={form.format}
                            onChange={(e) => setForm((p) => ({...p, format: e.target.value}))}
                        >
                            {FORMAT_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
                        </select>
                    </div>
                    <div className="space-y-2">
                        <Label>Status de publicação</Label>
                        <select
                            className="w-full border rounded-md px-3 py-2 text-sm bg-background"
                            value={form.statusOrigin}
                            onChange={(e) => setForm((p) => ({...p, statusOrigin: e.target.value}))}
                        >
                            {STATUS_ORIGIN_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
                        </select>
                    </div>
                    <div className="space-y-2">
                        <Label>Status no site</Label>
                        <select
                            className="w-full border rounded-md px-3 py-2 text-sm bg-background"
                            value={form.statusSite}
                            onChange={(e) => setForm((p) => ({...p, statusSite: e.target.value}))}
                        >
                            {STATUS_SITE_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
                        </select>
                    </div>
                    <div className="space-y-2">
                        <Label htmlFor="year">Ano de lançamento</Label>
                        <Input
                            id="year"
                            type="number"
                            min="1900"
                            max={new Date().getFullYear() + 1}
                            placeholder="Ex: 2020"
                            value={form.year}
                            onChange={(e) => setForm((p) => ({...p, year: e.target.value}))}
                        />
                    </div>
                </div>

                <div className="space-y-2">
                    <Label htmlFor="originCountry">País de origem</Label>
                    <Input
                        id="originCountry"
                        placeholder="Ex: Japan, Korea, China"
                        value={form.originCountry}
                        onChange={(e) => setForm((p) => ({...p, originCountry: e.target.value}))}
                    />
                </div>

                <div className="space-y-2">
                    <Label>Avisos de conteúdo</Label>
                    <div className="flex flex-wrap gap-2">
                        {CONTENT_WARNING_OPTIONS.map((w) => (
                            <button
                                key={w.value}
                                type="button"
                                onClick={() => toggleWarning(w.value)}
                                className={`text-xs border rounded-md px-2.5 py-1 transition-colors ${
                                    contentWarnings.includes(w.value)
                                        ? "bg-destructive text-destructive-foreground border-destructive"
                                        : "hover:bg-muted"
                                }`}
                            >
                                {w.label}
                            </button>
                        ))}
                    </div>
                </div>

                <div className="space-y-2">
                    <Label>Tags</Label>
                    <TagSelector
                        selectedIds={tagIds}
                        onChange={setTagIds}
                        excludeCategories={["Aviso de Conteúdo"]}
                    />
                </div>

                <Button type="submit" className="w-full" disabled={submitting}>
                    {submitting ? "Criando…" : "Criar mangá"}
                </Button>
            </form>
        </div>
    );
}