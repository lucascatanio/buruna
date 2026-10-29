import {useEffect, useState} from "react";
import {AlertTriangle, Loader2} from "lucide-react";
import {getLanguageAvailability} from "@/api/sourcingApi";
import type {LanguageAvailability} from "@/types/sourcing";
import {Label} from "@/components/ui/label";
import {NativeSelect} from "@/components/ui/native-select";
import {languageName} from "@/lib/chapterLabel";

const NO_GROUP = "";

interface LanguageGroupPickerProps {
    externalId: string;
    language: string;
    preferredGroupId: string | null;
    onChange: (group: { id: string; name: string } | null) => void;
}

/**
 * Para um idioma da obra no MangaDex: quantos capítulos ainda dá para baixar (obra licenciada
 * costuma ter quase tudo retirado) e o grupo preferido, entre os que traduziram.
 */
export function LanguageGroupPicker({externalId, language, preferredGroupId, onChange}: LanguageGroupPickerProps) {
    // undefined: carregando; null: falhou
    const [availability, setAvailability] = useState<LanguageAvailability | null | undefined>(undefined);

    useEffect(() => {
        // quem usa troca o idioma com key nova, então o estado começa em "carregando"
        let cancelled = false;
        getLanguageAvailability(externalId, language)
            .then((result) => !cancelled && setAvailability(result))
            .catch(() => !cancelled && setAvailability(null));
        return () => {
            cancelled = true;
        };
    }, [externalId, language]);

    const id = `group-${language}`;
    if (availability === undefined) {
        return (
            <p className="flex h-9 items-center gap-2 text-sm text-muted-foreground">
                <Loader2 className="size-4 animate-spin"/> Conferindo {languageName(language)} no MangaDex…
            </p>
        );
    }
    if (availability === null) {
        return <p className="text-xs text-destructive">Não deu para conferir este idioma. Dá para seguir sem grupo preferido.</p>;
    }

    const {availableChapters, publishedChapters, groups} = availability;
    const mostlyRemoved = publishedChapters > 0 && availableChapters < publishedChapters / 2;

    return (
        <div className="space-y-1.5">
            <p className={`flex items-start gap-1.5 text-xs ${mostlyRemoved ? "text-shu" : "text-muted-foreground"}`}>
                {mostlyRemoved && <AlertTriangle className="mt-px size-3.5 shrink-0"/>}
                <span>
                    {languageName(language)}: {availableChapters} de {publishedChapters} capítulos disponíveis.
                    {mostlyRemoved && " O resto foi retirado do MangaDex, comum em obra com publicação oficial."}
                </span>
            </p>
            {groups.length > 0 && (
                <>
                    <Label htmlFor={id}>Grupo preferido</Label>
                    <NativeSelect
                        id={id}
                        value={preferredGroupId ?? NO_GROUP}
                        onChange={(e) => {
                            const group = groups.find((g) => g.id === e.target.value);
                            onChange(group ? {id: group.id, name: group.name} : null);
                        }}
                    >
                        <option value={NO_GROUP}>Sem preferência</option>
                        {groups.map((group) => (
                            <option key={group.id} value={group.id}>{group.name} ({group.chapterCount} cap.)</option>
                        ))}
                    </NativeSelect>
                </>
            )}
        </div>
    );
}
