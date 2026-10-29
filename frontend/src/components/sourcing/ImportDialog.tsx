import {useEffect, useState} from "react";
import {toast} from "sonner";
import {Download, Loader2, X} from "lucide-react";
import {importWork, listSourceGroups} from "@/api/sourcingApi";
import {listWorks} from "@/api/workApi";
import type {SourceGroupSummary, SourceSearchResult} from "@/types/sourcing";
import type {WorkCard} from "@/types/work";
import {Button} from "@/components/ui/button";
import {Input} from "@/components/ui/input";
import {Label} from "@/components/ui/label";
import {NativeSelect} from "@/components/ui/native-select";
import {languageName} from "@/lib/chapterLabel";
import {apiErrorMessage} from "@/lib/apiError";

const DEFAULT_LANGUAGE = "pt-BR";
const NO_GROUP = "";

interface ImportDialogProps {
    work: SourceSearchResult;
    onClose: () => void;
    onImported: () => void;
}

interface LanguageState {
    /** undefined: carregando; null: falhou */
    groups: SourceGroupSummary[] | null | undefined;
    preferredGroupId: string;
}

/**
 * Importa uma obra do MangaDex: escolhe os idiomas, o grupo preferido de cada um e se a obra
 * entra como nova ou ligada a uma que já existe no catálogo.
 */
export function ImportDialog({work, onClose, onImported}: ImportDialogProps) {
    const [selected, setSelected] = useState<Record<string, LanguageState>>(() => {
        const initial: Record<string, LanguageState> = {};
        if (work.availableLanguages.includes(DEFAULT_LANGUAGE)) {
            initial[DEFAULT_LANGUAGE] = {groups: undefined, preferredGroupId: NO_GROUP};
        }
        return initial;
    });
    const [linkQuery, setLinkQuery] = useState(work.title);
    const [candidates, setCandidates] = useState<WorkCard[]>([]);
    const [linkWorkId, setLinkWorkId] = useState<string | null>(null);
    const [submitting, setSubmitting] = useState(false);

    const languages = [...work.availableLanguages].sort((a, b) =>
        a === DEFAULT_LANGUAGE ? -1 : b === DEFAULT_LANGUAGE ? 1 : languageName(a).localeCompare(languageName(b)));
    const selectedLanguages = Object.keys(selected);

    // grupos de cada idioma marcado, sob demanda
    useEffect(() => {
        for (const language of selectedLanguages) {
            if (selected[language].groups !== undefined) continue;
            listSourceGroups(work.externalId, language)
                .then((groups) => updateLanguage(language, {groups}))
                .catch(() => updateLanguage(language, {groups: null}));
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [selectedLanguages.join(","), work.externalId]);

    // obras do catálogo com título parecido, para não criar uma duplicata
    useEffect(() => {
        const query = linkQuery.trim();
        if (query.length < 2) {
            setCandidates([]);
            return;
        }
        const timer = setTimeout(() => {
            listWorks({title: query, size: 5})
                .then((page) => setCandidates(page.content))
                .catch(() => setCandidates([]));
        }, 300);
        return () => clearTimeout(timer);
    }, [linkQuery]);

    function updateLanguage(language: string, patch: Partial<LanguageState>) {
        setSelected((current) => current[language]
            ? {...current, [language]: {...current[language], ...patch}}
            : current);
    }

    function toggleLanguage(language: string) {
        setSelected((current) => {
            if (current[language]) {
                const next = {...current};
                delete next[language];
                return next;
            }
            return {...current, [language]: {groups: undefined, preferredGroupId: NO_GROUP}};
        });
    }

    async function handleSubmit() {
        setSubmitting(true);
        try {
            await importWork({
                externalId: work.externalId,
                linkWorkId,
                languages: selectedLanguages.map((language) => {
                    const state = selected[language];
                    const group = state.groups?.find((g) => g.id === state.preferredGroupId);
                    return {language, preferredGroupId: group?.id ?? null, preferredGroupName: group?.name ?? null};
                }),
            });
            toast.success("Importação iniciada. Os capítulos chegam aos poucos.");
            onImported();
            onClose();
        } catch (e) {
            toast.error(apiErrorMessage(e, "Não foi possível importar a obra."));
        } finally {
            setSubmitting(false);
        }
    }

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 px-4">
            <div role="dialog" aria-modal="true" aria-labelledby="import-title"
                 className="flex max-h-[90vh] w-full max-w-lg flex-col rounded-lg border bg-card shadow-xl">
                <div className="flex items-center justify-between border-b px-6 py-4">
                    <h2 id="import-title" className="truncate text-base font-semibold">Importar {work.title}</h2>
                    <button aria-label="Fechar" className="flex size-9 shrink-0 items-center justify-center" onClick={onClose}>
                        <X className="size-4 text-muted-foreground"/>
                    </button>
                </div>

                <div className="flex-1 space-y-6 overflow-y-auto px-6 py-5">
                    <fieldset className="space-y-3">
                        <legend className="text-sm font-medium">Idiomas</legend>
                        <p className="text-xs text-muted-foreground">
                            Todos os capítulos dos idiomas marcados são baixados. Sem grupo preferido, entra a
                            versão publicada mais recentemente.
                        </p>
                        {languages.length === 0 && (
                            <p className="text-sm text-muted-foreground">O MangaDex não tem capítulos desta obra.</p>
                        )}
                        <div className="flex flex-wrap gap-2">
                            {languages.map((language) => (
                                <label key={language}
                                       className="flex min-h-9 cursor-pointer items-center gap-2 rounded-md border px-3 text-sm has-[:checked]:border-foreground">
                                    <input type="checkbox" checked={!!selected[language]} onChange={() => toggleLanguage(language)}/>
                                    {languageName(language)}
                                </label>
                            ))}
                        </div>

                        {selectedLanguages.map((language) => {
                            const state = selected[language];
                            return (
                                <div key={language} className="space-y-1.5">
                                    <Label htmlFor={`group-${language}`}>Grupo preferido · {languageName(language)}</Label>
                                    {state.groups === undefined ? (
                                        <p className="flex h-9 items-center gap-2 text-sm text-muted-foreground">
                                            <Loader2 className="size-4 animate-spin"/> Carregando grupos…
                                        </p>
                                    ) : (
                                        <NativeSelect
                                            id={`group-${language}`}
                                            value={state.preferredGroupId}
                                            onChange={(e) => updateLanguage(language, {preferredGroupId: e.target.value})}
                                        >
                                            <option value={NO_GROUP}>Sem preferência</option>
                                            {(state.groups ?? []).map((group) => (
                                                <option key={group.id} value={group.id}>
                                                    {group.name} ({group.chapterCount} cap.)
                                                </option>
                                            ))}
                                        </NativeSelect>
                                    )}
                                    {state.groups?.length === 0 && (
                                        <p className="text-xs text-muted-foreground">
                                            Nenhum capítulo disponível neste idioma agora. O MangaDex ainda lista o
                                            idioma, mas os capítulos podem ter sido retirados.
                                        </p>
                                    )}
                                    {state.groups === null && (
                                        <p className="text-xs text-destructive">Não deu para carregar os grupos. Dá para importar sem preferência.</p>
                                    )}
                                </div>
                            );
                        })}
                    </fieldset>

                    <fieldset className="space-y-3">
                        <legend className="text-sm font-medium">Obra no Burūna</legend>
                        <label className="flex min-h-9 cursor-pointer items-center gap-2 text-sm">
                            <input type="radio" name="link" checked={linkWorkId === null} onChange={() => setLinkWorkId(null)}/>
                            Criar uma obra nova com os dados do MangaDex
                        </label>
                        <div className="space-y-1.5">
                            <Label htmlFor="link-query">Ou ligar a uma obra que já existe</Label>
                            <Input id="link-query" value={linkQuery} onChange={(e) => setLinkQuery(e.target.value)}
                                   placeholder="Título da obra no catálogo"/>
                        </div>
                        {candidates.map((candidate) => (
                            <label key={candidate.id} className="flex min-h-9 cursor-pointer items-center gap-2 text-sm">
                                <input type="radio" name="link" checked={linkWorkId === candidate.id}
                                       onChange={() => setLinkWorkId(candidate.id)}/>
                                <span className="truncate">{candidate.title}</span>
                                {candidate.year && <span className="font-mono text-xs text-muted-foreground">{candidate.year}</span>}
                            </label>
                        ))}
                    </fieldset>
                </div>

                <div className="flex gap-2 border-t px-6 py-4">
                    <Button variant="outline" className="flex-1" onClick={onClose} disabled={submitting}>Cancelar</Button>
                    <Button className="flex-1" onClick={handleSubmit} disabled={submitting || selectedLanguages.length === 0}>
                        <Download className="mr-1.5 size-4"/>
                        {submitting ? "Importando…" : "Importar"}
                    </Button>
                </div>
            </div>
        </div>
    );
}
