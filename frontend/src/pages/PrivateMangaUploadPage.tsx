import {useState} from "react";
import {useNavigate} from "react-router-dom";
import api from "@/lib/axios";
import {Button} from "@/components/ui/button";
import {Input} from "@/components/ui/input";
import {Label} from "@/components/ui/label";
import {Card, CardContent, CardHeader, CardTitle} from "@/components/ui/card";
import {toast} from "sonner";
import {ArrowLeft, Upload, X, Check} from "lucide-react";

interface CreatedManga {
    id: string;
    title: string;
}

export function PrivateMangaUploadPage() {
    const navigate = useNavigate();

    const [title, setTitle] = useState("");
    const [synopsis, setSynopsis] = useState("");
    const [coverBase64, setCoverBase64] = useState<string | null>(null);
    const [coverPreview, setCoverPreview] = useState<string | null>(null);
    const [volumeNumber, setVolumeNumber] = useState("1");
    const [volumeFile, setVolumeFile] = useState<File | null>(null);
    const [submitting, setSubmitting] = useState(false);

    const [createdManga, setCreatedManga] = useState<CreatedManga | null>(null);
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

    async function handleSubmit(e: React.FormEvent) {
        e.preventDefault();
        if (!volumeFile) {
            toast.error("Selecione um arquivo para o primeiro volume");
            return;
        }
        setSubmitting(true);
        try {
            const formData = new FormData();
            formData.append("title", title.trim());
            if (synopsis.trim()) formData.append("synopsis", synopsis.trim());
            if (coverBase64) formData.append("coverBase64", coverBase64);
            formData.append("volumeNumber", volumeNumber);
            formData.append("file", volumeFile);

            const {data} = await api.post("/my/mangas", formData);
            setCreatedManga({id: data.id, title: data.title});
            setUploadedVolumes([Number(volumeNumber)]);
            setVolumeNumber(String(Number(volumeNumber) + 1));
            setVolumeFile(null);
            toast.success("Mangá adicionado à coleção!");
        } catch (err: any) {
            toast.error(err.response?.data?.message ?? "Erro ao adicionar mangá");
        } finally {
            setSubmitting(false);
        }
    }

    async function handleAddVolume() {
        if (!createdManga || !volumeFile) return;
        setUploadingVolume(true);
        try {
            const formData = new FormData();
            formData.append("file", volumeFile);
            formData.append("volumeNumber", volumeNumber);
            await api.post(`/my/mangas/${createdManga.id}/volumes`, formData);
            toast.success(`Volume ${volumeNumber} enviado!`);
            setUploadedVolumes((prev) => [...prev, Number(volumeNumber)]);
            setVolumeNumber(String(Number(volumeNumber) + 1));
            setVolumeFile(null);
            const fileInput = document.getElementById("volume-file") as HTMLInputElement;
            if (fileInput) fileInput.value = "";
        } catch (err: any) {
            toast.error(err.response?.data?.message ?? "Erro ao enviar volume");
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
                        <p className="text-sm text-muted-foreground">Adicionado à coleção privada</p>
                    </div>
                </div>

                <Card>
                    <CardHeader>
                        <CardTitle className="text-base">Adicionar mais volumes</CardTitle>
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
                            onClick={handleAddVolume}
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
                    onClick={() => navigate(`/colecao/${createdManga.id}`)}
                >
                    Ver mangá na coleção
                </Button>
            </div>
        );
    }

    return (
        <div className="max-w-2xl mx-auto px-4 md:px-6 py-8 space-y-6">
            <div className="flex items-center gap-3">
                <Button variant="ghost" size="icon" onClick={() => navigate("/colecao")}>
                    <ArrowLeft className="w-4 h-4"/>
                </Button>
                <h1 className="text-2xl font-bold">Adicionar à coleção</h1>
            </div>

            <form onSubmit={handleSubmit} className="space-y-6">

                <div className="space-y-2">
                    <Label>Capa (opcional)</Label>
                    <div className="flex gap-4 items-start">
                        {coverPreview && (
                            <div className="relative w-24 shrink-0">
                                <img
                                    src={coverPreview}
                                    alt="Capa"
                                    className="w-24 aspect-[2/3] object-cover rounded-md border"
                                />
                                <button
                                    type="button"
                                    className="absolute -top-1.5 -right-1.5 bg-destructive text-destructive-foreground rounded-full w-5 h-5 flex items-center justify-center"
                                    onClick={() => {
                                        setCoverBase64(null);
                                        setCoverPreview(null);
                                    }}
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
                        value={title}
                        onChange={(e) => setTitle(e.target.value)}
                        placeholder="Título da obra"
                        required
                    />
                </div>

                <div className="space-y-2">
                    <Label htmlFor="synopsis">Sinopse</Label>
                    <textarea
                        id="synopsis"
                        className="w-full border rounded-md px-3 py-2 text-sm bg-background resize-none min-h-[100px]"
                        placeholder="Descrição da obra (opcional)"
                        value={synopsis}
                        onChange={(e) => setSynopsis(e.target.value)}
                    />
                </div>

                <Card>
                    <CardHeader className="pb-3">
                        <CardTitle className="text-base">Primeiro volume *</CardTitle>
                    </CardHeader>
                    <CardContent className="space-y-3">
                        <div className="grid grid-cols-2 gap-3">
                            <div className="space-y-1.5">
                                <Label htmlFor="volumeNumber">Número do volume</Label>
                                <Input
                                    id="volumeNumber"
                                    type="number"
                                    min="1"
                                    value={volumeNumber}
                                    onChange={(e) => setVolumeNumber(e.target.value)}
                                    required
                                />
                            </div>
                            <div className="space-y-1.5">
                                <Label htmlFor="volumeFile">Arquivo (PDF, EPUB, MOBI)</Label>
                                <Input
                                    id="volumeFile"
                                    type="file"
                                    accept=".pdf,.epub,.mobi"
                                    onChange={(e) => setVolumeFile(e.target.files?.[0] ?? null)}
                                    required
                                />
                            </div>
                        </div>
                    </CardContent>
                </Card>

                <Button type="submit" className="w-full" disabled={submitting || !volumeFile}>
                    <Upload className="w-4 h-4 mr-1.5"/>
                    {submitting ? "Enviando…" : "Adicionar à coleção"}
                </Button>
            </form>
        </div>
    );
}