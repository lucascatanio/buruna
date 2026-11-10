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
import {SiteCodeField} from "./SiteCodeField";

const NONE = "__none";

/** "Português (Brasil) é acompanhado hoje sem código. Agora vai usar pt-br, com o mesmo grupo preferido." */
function codeChange(name: string, from: string | null | undefined, to: string | null): string {
    const today = from ? `com o código ${from}` : "sem código";
    const next = to ? `Agora vai usar ${to}` : "Agora vai ficar sem código";
    return `${name} é acompanhado hoje ${today}. ${next}, com o mesmo grupo preferido.`;
}

interface AddLanguagePanelProps {
    tracked: TrackedWork;
    onAdded: () => void;
}

/**
 * Acrescenta um idioma a uma obra já acompanhada; os capítulos dele chegam na rodada seguinte. No
 * MangaDex, com grupo preferido; num site, o ADM diz a que idioma do Burūna o código dele corresponde.
 * Num site, o código também pode ser digitado à mão (o site que exige navegador não lista aqui), e
 * escolher um idioma do Burūna já acompanhado troca o código dele, mantendo o grupo preferido (#142).
 */
export function AddLanguagePanel({tracked, onAdded}: AddLanguagePanelProps) {
    const isSite = tracked.source === "MANGA_DOWNLOADER";
    const sourceName = isSite ? siteHost(tracked.sourceUrl) : "MangaDex";
    // undefined: carregando; null: falhou
    const [codes, setCodes] = useState<string[] | null | undefined>(undefined);
    const [typing, setTyping] = useState(false);
    // o código como a fonte escreve; na lista, vazio é o site que não informa idioma, então "nenhum escolhido" é null
    const [code, setCode] = useState<string | null>(null);
    const [noLanguage, setNoLanguage] = useState(false);
    const [language, setLanguage] = useState("");
    const [group, setGroup] = useState<{ id: string; name: string } | null>(null);
    const [saving, setSaving] = useState(false);

    useEffect(() => {
        listUntrackedSourceLanguages(tracked.id)
            .then(setCodes)
            .catch(() => setCodes(null));
    }, [tracked.id]);

    // site que não lista aqui: o código vai à mão
    const manual = isSite && (typing || codes === null);
    const listed = codes ?? [];
    const typed = (code ?? "").trim();
    const nextCode = manual && noLanguage ? "" : typed;
    const existing = isSite ? tracked.languages.find((l) => l.language === language) : undefined;
    const ready = language !== "" && (manual ? noLanguage || typed !== "" : code !== null);

    function reset(nextTyping: boolean) {
        setTyping(nextTyping);
        setCode(null);
        setNoLanguage(false);
        setLanguage("");
        setGroup(null);
    }

    function choose(value: string | null) {
        setCode(value);
        setLanguage(value === null ? "" : isSite ? guessLanguage(value) : value);
        setGroup(null);
    }

    // o palpite do idioma do Burūna segue o código digitado, até o ADM escolher outro
    function type(value: string, confirmedNoLanguage: boolean) {
        const followsGuess = language === "" || language === guessLanguage(code ?? "");
        setCode(value);
        setNoLanguage(confirmedNoLanguage);
        if (followsGuess && !confirmedNoLanguage) {
            setLanguage(guessLanguage(value));
        }
    }

    async function handleAdd() {
        if (!ready) return;
        setSaving(true);
        // trocar o código não pode apagar o grupo preferido que o idioma já tinha
        const preferred = group ?? (existing?.preferredGroupId
            ? {id: existing.preferredGroupId, name: existing.preferredGroupName ?? existing.preferredGroupId}
            : null);
        try {
            await trackLanguage(tracked.id, {
                language,
                preferredGroupId: preferred?.id ?? null,
                preferredGroupName: preferred?.name ?? null,
                sourceLanguage: isSite ? (manual ? nextCode : code) : null,
            });
            toast.success(existing
                ? `Código de ${languageName(language)} trocado. A verificação já começou.`
                : `${languageName(language)} adicionado. Os capítulos chegam aos poucos.`);
            reset(false);
            onAdded();
        } catch (e) {
            toast.error(apiErrorMessage(e, "Não foi possível salvar o idioma."));
        } finally {
            setSaving(false);
        }
    }

    if (codes === undefined) {
        return <p className="flex items-center gap-2 p-3 text-sm text-muted-foreground"><Loader2 className="size-4 animate-spin"/> Carregando idiomas…</p>;
    }
    if (codes === null && !isSite) {
        return <p className="p-3 text-sm text-destructive">O {sourceName} não respondeu. Tente de novo em alguns minutos.</p>;
    }

    const selectId = `add-language-${tracked.id}`;
    return (
        <div className="space-y-3 p-3">
            {codes === null && (
                <p className="text-sm text-muted-foreground">
                    O {sourceName} não listou os idiomas por aqui. Sites que exigem navegador não listam, mas
                    baixam normalmente: informe o código à mão.
                </p>
            )}

            {manual ? (
                <SiteCodeField id={`${selectId}-code`} code={code ?? ""} noLanguage={noLanguage} onChange={type}/>
            ) : listed.length === 0 ? (
                <p className="text-sm text-muted-foreground">Todos os idiomas do {sourceName} já são acompanhados.</p>
            ) : (
                <div className="space-y-1.5">
                    <Label htmlFor={selectId}>Adicionar idioma</Label>
                    <NativeSelect id={selectId} value={code ?? NONE}
                                  onChange={(e) => choose(e.target.value === NONE ? null : e.target.value)}>
                        <option value={NONE}>Escolha um idioma</option>
                        {listed.map((c) => (
                            <option key={c} value={c}>{isSite ? (c || "sem idioma informado") : languageName(c)}</option>
                        ))}
                    </NativeSelect>
                </div>
            )}

            {isSite && (manual || code !== null) && (
                <BurunaLanguageSelect id={`${selectId}-buruna`} value={language} onChange={setLanguage}
                                      tracked={tracked.languages.map((l) => l.language)}/>
            )}
            {existing && (
                <p className="text-xs text-muted-foreground">
                    {codeChange(languageName(language), existing.sourceLanguage, manual ? nextCode : code)}
                </p>
            )}
            {code !== null && !isSite && (
                <LanguageGroupPicker key={code} externalId={tracked.externalId} language={code}
                                     preferredGroupId={group?.id ?? null} onChange={setGroup}/>
            )}

            <div className="flex flex-wrap items-center gap-2">
                {(manual || listed.length > 0) && (
                    <Button size="sm" onClick={handleAdd} disabled={!ready || saving}>
                        {saving ? <Loader2 className="size-3.5 animate-spin"/> : <Plus className="size-3.5"/>}
                        {existing ? "Trocar o código" : "Adicionar"}
                    </Button>
                )}
                {isSite && codes !== null && (
                    <Button size="sm" variant="link" onClick={() => reset(!typing)}>
                        {typing ? "Escolher da lista" : "Digitar o código à mão"}
                    </Button>
                )}
            </div>
        </div>
    );
}

interface BurunaLanguageSelectProps {
    id: string;
    value: string;
    onChange: (language: string) => void;
    /** Idiomas do Burūna já acompanhados: escolher um deles troca o código do site dele. */
    tracked: string[];
}

function BurunaLanguageSelect({id, value, onChange, tracked}: BurunaLanguageSelectProps) {
    const options = [...new Set([...tracked, ...SITE_LANGUAGES, ...(value ? [value] : [])])];
    return (
        <div className="space-y-1.5">
            <Label htmlFor={id}>Idioma no Burūna</Label>
            <NativeSelect id={id} value={value} onChange={(e) => onChange(e.target.value)}>
                <option value="">Escolha o idioma</option>
                {options.map((l) => (
                    <option key={l} value={l}>{languageName(l)}{tracked.includes(l) ? " (já acompanhado)" : ""}</option>
                ))}
            </NativeSelect>
        </div>
    );
}
