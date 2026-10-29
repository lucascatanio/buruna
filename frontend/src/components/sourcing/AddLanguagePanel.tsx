import {useEffect, useState} from "react";
import {toast} from "sonner";
import {Loader2, Plus} from "lucide-react";
import {listUntrackedSourceLanguages, trackLanguage} from "@/api/sourcingApi";
import {Button} from "@/components/ui/button";
import {Label} from "@/components/ui/label";
import {NativeSelect} from "@/components/ui/native-select";
import {languageName} from "@/lib/chapterLabel";
import {apiErrorMessage} from "@/lib/apiError";
import {LanguageGroupPicker} from "./LanguageGroupPicker";

interface AddLanguagePanelProps {
    trackedId: string;
    externalId: string;
    onAdded: () => void;
}

/** Acrescenta um idioma a uma obra já acompanhada; os capítulos dele chegam na rodada seguinte. */
export function AddLanguagePanel({trackedId, externalId, onAdded}: AddLanguagePanelProps) {
    // undefined: carregando; null: falhou
    const [languages, setLanguages] = useState<string[] | null | undefined>(undefined);
    const [language, setLanguage] = useState("");
    const [group, setGroup] = useState<{ id: string; name: string } | null>(null);
    const [saving, setSaving] = useState(false);

    useEffect(() => {
        listUntrackedSourceLanguages(trackedId)
            .then(setLanguages)
            .catch(() => setLanguages(null));
    }, [trackedId]);

    async function handleAdd() {
        setSaving(true);
        try {
            await trackLanguage(trackedId, {language, preferredGroupId: group?.id ?? null, preferredGroupName: group?.name ?? null});
            toast.success(`${languageName(language)} adicionado. Os capítulos chegam aos poucos.`);
            setLanguage("");
            setGroup(null);
            onAdded();
        } catch (e) {
            toast.error(apiErrorMessage(e, "Não foi possível adicionar o idioma."));
        } finally {
            setSaving(false);
        }
    }

    if (languages === undefined) {
        return <p className="flex items-center gap-2 p-3 text-sm text-muted-foreground"><Loader2 className="size-4 animate-spin"/> Carregando idiomas…</p>;
    }
    if (languages === null) {
        return <p className="p-3 text-sm text-destructive">O MangaDex não respondeu. Tente de novo em alguns minutos.</p>;
    }
    if (languages.length === 0) {
        return <p className="p-3 text-sm text-muted-foreground">Todos os idiomas do MangaDex já são acompanhados.</p>;
    }

    return (
        <div className="space-y-3 p-3">
            <div className="space-y-1.5">
                <Label htmlFor={`add-language-${trackedId}`}>Adicionar idioma</Label>
                <NativeSelect id={`add-language-${trackedId}`} value={language}
                              onChange={(e) => {
                                  setLanguage(e.target.value);
                                  setGroup(null);
                              }}>
                    <option value="">Escolha um idioma</option>
                    {languages.map((l) => <option key={l} value={l}>{languageName(l)}</option>)}
                </NativeSelect>
            </div>
            {language && (
                <LanguageGroupPicker key={language} externalId={externalId} language={language}
                                     preferredGroupId={group?.id ?? null} onChange={setGroup}/>
            )}
            <Button size="sm" onClick={handleAdd} disabled={!language || saving}>
                {saving ? <Loader2 className="size-3.5 animate-spin"/> : <Plus className="size-3.5"/>}
                Adicionar
            </Button>
        </div>
    );
}
