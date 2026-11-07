import {useEffect, useState} from "react";
import {toast} from "sonner";
import {Loader2, Plus} from "lucide-react";
import {listUntrackedSourceLanguages, trackLanguage} from "@/api/sourcingApi";
import type {TrackedWork} from "@/types/sourcing";
import {Button} from "@/components/ui/button";
import {Label} from "@/components/ui/label";
import {NativeSelect} from "@/components/ui/native-select";
import {languageName} from "@/lib/chapterLabel";
import {apiErrorMessage} from "@/lib/apiError";
import {guessLanguage, SITE_LANGUAGES, siteHost} from "@/lib/siteLanguage";
import {LanguageGroupPicker} from "./LanguageGroupPicker";

const NONE = "__none";

interface AddLanguagePanelProps {
    tracked: TrackedWork;
    onAdded: () => void;
}

/**
 * Acrescenta um idioma a uma obra já acompanhada; os capítulos dele chegam na rodada seguinte. No
 * MangaDex, com grupo preferido; num site, o ADM diz a que idioma do Burūna o código dele corresponde.
 */
export function AddLanguagePanel({tracked, onAdded}: AddLanguagePanelProps) {
    const isSite = tracked.source === "MANGA_DOWNLOADER";
    const sourceName = isSite ? siteHost(tracked.sourceUrl) : "MangaDex";
    // undefined: carregando; null: falhou
    const [codes, setCodes] = useState<string[] | null | undefined>(undefined);
    // o código como a fonte escreve; vazio é o site que não informa idioma, então "nenhum escolhido" é null
    const [code, setCode] = useState<string | null>(null);
    const [language, setLanguage] = useState("");
    const [group, setGroup] = useState<{ id: string; name: string } | null>(null);
    const [saving, setSaving] = useState(false);

    useEffect(() => {
        listUntrackedSourceLanguages(tracked.id)
            .then(setCodes)
            .catch(() => setCodes(null));
    }, [tracked.id]);

    function choose(value: string | null) {
        setCode(value);
        setLanguage(value === null ? "" : isSite ? guessLanguage(value) : value);
        setGroup(null);
    }

    async function handleAdd() {
        if (code === null) return;
        setSaving(true);
        try {
            await trackLanguage(tracked.id, {
                language,
                preferredGroupId: group?.id ?? null,
                preferredGroupName: group?.name ?? null,
                sourceLanguage: isSite ? code : null,
            });
            toast.success(`${languageName(language)} adicionado. Os capítulos chegam aos poucos.`);
            choose(null);
            onAdded();
        } catch (e) {
            toast.error(apiErrorMessage(e, "Não foi possível adicionar o idioma."));
        } finally {
            setSaving(false);
        }
    }

    if (codes === undefined) {
        return <p className="flex items-center gap-2 p-3 text-sm text-muted-foreground"><Loader2 className="size-4 animate-spin"/> Carregando idiomas…</p>;
    }
    if (codes === null) {
        return <p className="p-3 text-sm text-destructive">O {sourceName} não respondeu. Tente de novo em alguns minutos.</p>;
    }
    if (codes.length === 0) {
        return <p className="p-3 text-sm text-muted-foreground">Todos os idiomas do {sourceName} já são acompanhados.</p>;
    }

    const selectId = `add-language-${tracked.id}`;
    return (
        <div className="space-y-3 p-3">
            <div className="space-y-1.5">
                <Label htmlFor={selectId}>Adicionar idioma</Label>
                <NativeSelect id={selectId} value={code ?? NONE}
                              onChange={(e) => choose(e.target.value === NONE ? null : e.target.value)}>
                    <option value={NONE}>Escolha um idioma</option>
                    {codes.map((c) => (
                        <option key={c} value={c}>{isSite ? (c || "sem idioma informado") : languageName(c)}</option>
                    ))}
                </NativeSelect>
            </div>
            {code !== null && isSite && (
                <div className="space-y-1.5">
                    <Label htmlFor={`${selectId}-buruna`}>Idioma no Burūna</Label>
                    <NativeSelect id={`${selectId}-buruna`} value={language} onChange={(e) => setLanguage(e.target.value)}>
                        <option value="">Escolha o idioma</option>
                        {language && !SITE_LANGUAGES.includes(language) && <option value={language}>{languageName(language)}</option>}
                        {SITE_LANGUAGES.map((l) => <option key={l} value={l}>{languageName(l)}</option>)}
                    </NativeSelect>
                </div>
            )}
            {code !== null && !isSite && (
                <LanguageGroupPicker key={code} externalId={tracked.externalId} language={code}
                                     preferredGroupId={group?.id ?? null} onChange={setGroup}/>
            )}
            <Button size="sm" onClick={handleAdd} disabled={code === null || !language || saving}>
                {saving ? <Loader2 className="size-3.5 animate-spin"/> : <Plus className="size-3.5"/>}
                Adicionar
            </Button>
        </div>
    );
}
