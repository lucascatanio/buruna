import {useRef, useState} from "react";
import {toast} from "sonner";
import {Upload, X} from "lucide-react";
import {finalizeChapterUpload, getChapterUploadUrl, type ChapterScope, type ChapterSourceFormat} from "@/api/chapterApi";
import {uploadSignedFile} from "@/api/volumeUpload";
import {Button} from "@/components/ui/button";
import {Input} from "@/components/ui/input";
import {Label} from "@/components/ui/label";
import {FileInput} from "@/components/ui/file-input";
import {languageName} from "@/lib/chapterLabel";
import {apiErrorMessage} from "@/lib/apiError";

const COMMON_LANGUAGES = ["pt-BR", "en", "es-419", "es", "ja", "ko", "zh"];

interface ChapterUploadDialogProps {
    scope: ChapterScope;
    workId: string;
    /** Formato da obra: livro em PDF não vira imagens, então PDF não é oferecido para LIVRO. */
    workFormat?: string;
    defaultLanguage: string;
    onClose: () => void;
    /** Chamado depois do finalize: o capítulo entra na lista como "processando". */
    onUploaded: () => void;
}

function formatBytes(bytes: number): string {
    if (bytes >= 1024 ** 3) return `${(bytes / 1024 ** 3).toFixed(1)} GB`;
    if (bytes >= 1024 ** 2) return `${(bytes / 1024 ** 2).toFixed(1)} MB`;
    return `${Math.max(1, Math.round(bytes / 1024))} KB`;
}

function sourceFormatOf(file: File): ChapterSourceFormat | null {
    const extension = file.name.split(".").pop()?.toLowerCase();
    return extension === "cbz" || extension === "cbr" || extension === "pdf" ? extension : null;
}

export function ChapterUploadDialog({scope, workId, workFormat, defaultLanguage, onClose, onUploaded}: ChapterUploadDialogProps) {
    const acceptsPdf = workFormat !== "LIVRO";
    const [language, setLanguage] = useState(defaultLanguage);
    const [number, setNumber] = useState("");
    const [label, setLabel] = useState("");
    const [title, setTitle] = useState("");
    const [group, setGroup] = useState("");
    const [file, setFile] = useState<File | null>(null);
    const [uploading, setUploading] = useState(false);
    const fileInputRef = useRef<HTMLInputElement>(null);

    const parsedNumber = number.trim() === "" ? null : Number(number.replace(",", "."));
    const numberInvalid = parsedNumber !== null && (Number.isNaN(parsedNumber) || parsedNumber < 0);
    const missingIdentity = parsedNumber === null && label.trim() === "";
    const sourceFormat = file ? sourceFormatOf(file) : null;
    const formatInvalid = !!file && (sourceFormat === null || (sourceFormat === "pdf" && !acceptsPdf));
    const canSubmit = !!file && !!sourceFormat && !formatInvalid && !uploading && !numberInvalid
        && !missingIdentity && language.trim() !== "";

    async function handleSubmit() {
        if (!file || !sourceFormat || !canSubmit) return;
        setUploading(true);
        try {
            // o número é conferido já na URL: um capítulo repetido falha antes de subir o arquivo
            const {uploadUrl, objectName, requiredHeaders} =
                await getChapterUploadUrl(scope, workId, language, parsedNumber, sourceFormat);
            await uploadSignedFile(uploadUrl, requiredHeaders, file);
            await finalizeChapterUpload(scope, workId, {
                objectName,
                language,
                number: parsedNumber,
                label: label.trim() || null,
                title: title.trim() || null,
                scanlationGroup: group.trim() || null,
            });
            toast.success("Capítulo enviado. Estamos preparando as páginas.");
            onUploaded();
            onClose();
        } catch (e) {
            toast.error(apiErrorMessage(e, "Não foi possível enviar o capítulo."));
        } finally {
            setUploading(false);
        }
    }

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 px-4">
            <div role="dialog" aria-modal="true" aria-labelledby="chapter-upload-title"
                 className="bg-card border rounded-lg w-full max-w-md p-6 space-y-4 shadow-xl">
                <div className="flex items-center justify-between">
                    <h2 id="chapter-upload-title" className="text-base font-semibold">Adicionar capítulo</h2>
                    <button aria-label="Fechar" className="flex size-9 items-center justify-center" onClick={onClose}>
                        <X className="w-4 h-4 text-muted-foreground"/>
                    </button>
                </div>

                <div className="grid grid-cols-2 gap-3">
                    <div className="space-y-1.5">
                        <Label htmlFor="chapter-language">Idioma</Label>
                        <select
                            id="chapter-language"
                            value={language}
                            onChange={(e) => setLanguage(e.target.value)}
                            className="h-9 w-full rounded-md border bg-transparent px-2 text-sm"
                        >
                            {COMMON_LANGUAGES.includes(language) ? null : <option value={language}>{languageName(language)}</option>}
                            {COMMON_LANGUAGES.map((l) => <option key={l} value={l}>{languageName(l)}</option>)}
                        </select>
                    </div>
                    <div className="space-y-1.5">
                        <Label htmlFor="chapter-number">Número</Label>
                        <Input
                            id="chapter-number"
                            inputMode="decimal"
                            placeholder="Ex.: 12 ou 12.5"
                            value={number}
                            onChange={(e) => setNumber(e.target.value)}
                            aria-invalid={numberInvalid}
                        />
                    </div>
                </div>

                <div className="space-y-1.5">
                    <Label htmlFor="chapter-label">Rótulo {parsedNumber === null && <span className="text-shu">*</span>}</Label>
                    <Input
                        id="chapter-label"
                        placeholder="Para capítulo sem número: Extra, Oneshot…"
                        maxLength={100}
                        value={label}
                        onChange={(e) => setLabel(e.target.value)}
                    />
                </div>

                <div className="grid grid-cols-2 gap-3">
                    <div className="space-y-1.5">
                        <Label htmlFor="chapter-title">Título</Label>
                        <Input id="chapter-title" maxLength={255} value={title} onChange={(e) => setTitle(e.target.value)}/>
                    </div>
                    <div className="space-y-1.5">
                        <Label htmlFor="chapter-group">Grupo de tradução</Label>
                        <Input id="chapter-group" maxLength={255} value={group} onChange={(e) => setGroup(e.target.value)}/>
                    </div>
                </div>

                <div className="space-y-1.5">
                    <Label htmlFor="chapter-file">Arquivo ({acceptsPdf ? "CBZ, CBR ou PDF" : "CBZ ou CBR"})</Label>
                    <FileInput id="chapter-file" ref={fileInputRef}
                               accept={acceptsPdf ? ".cbz,.cbr,.pdf" : ".cbz,.cbr"}
                               onChange={(e) => setFile(e.target.files?.[0] ?? null)}/>
                    {file && <p className="text-xs text-muted-foreground">{file.name} · {formatBytes(file.size)}</p>}
                </div>

                {formatInvalid && (
                    <p className="text-xs text-destructive">
                        {acceptsPdf ? "Envie um arquivo CBZ, CBR ou PDF." : "Livro em PDF ainda é enviado como volume. Aqui, use CBZ ou CBR."}
                    </p>
                )}
                {numberInvalid && <p className="text-xs text-destructive">Número inválido.</p>}
                {missingIdentity && <p className="text-xs text-muted-foreground">Informe o número ou um rótulo.</p>}

                <div className="flex gap-2 pt-1">
                    <Button variant="outline" className="flex-1" onClick={onClose} disabled={uploading}>
                        Cancelar
                    </Button>
                    <Button className="flex-1" onClick={handleSubmit} disabled={!canSubmit}>
                        <Upload className="w-4 h-4 mr-1.5"/>
                        {uploading ? "Enviando…" : "Enviar"}
                    </Button>
                </div>
            </div>
        </div>
    );
}
