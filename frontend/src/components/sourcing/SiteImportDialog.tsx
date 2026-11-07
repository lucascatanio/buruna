import {type FormEvent, useEffect, useState} from "react";
import {toast} from "sonner";
import {AlertTriangle, ChevronLeft, ChevronRight, Download, ListChecks, Loader2, X} from "lucide-react";
import {getMetadataPreview, importWork, listSiteSeries} from "@/api/sourcingApi";
import {listWorks} from "@/api/workApi";
import type {SiteSeriesListing, WorkMetadataPreview, WorkMetadataSearchResult} from "@/types/sourcing";
import type {WorkCard} from "@/types/work";
import {Badge} from "@/components/ui/badge";
import {Button} from "@/components/ui/button";
import {Input} from "@/components/ui/input";
import {Label} from "@/components/ui/label";
import {NativeSelect} from "@/components/ui/native-select";
import {Textarea} from "@/components/ui/textarea";
import {languageName} from "@/lib/chapterLabel";
import {apiErrorMessage} from "@/lib/apiError";
import {guessLanguage, SITE_LANGUAGES, siteGroupId} from "@/lib/siteLanguage";
import {FORMAT_LABELS} from "@/lib/workLabels";
import {MetadataCover} from "./MetadataCover";

type Step = 1 | 2 | 3;

/** Obra que recebe os capítulos: a do AniList (criada, ou a que já está ligada a ele) ou outra do catálogo. */
type Target = { kind: "aniList" } | { kind: "catalog"; workId: string; title: string };

/** Idioma marcado: o código do site, a tag do Burūna que ele vira e o grupo preferido. */
interface LanguageRow {
    siteCode: string;
    language: string;
    groupId: string | null;
    groupName: string | null;
}

const STEP_TITLES: Record<Step, string> = {1: "Obra", 2: "Site", 3: "Idiomas"};

function isHttpUrl(value: string): boolean {
    try {
        const url = new URL(value.trim());
        return url.protocol === "http:" || url.protocol === "https:";
    } catch {
        return false;
    }
}

function siteLanguageLabel(code: string): string {
    return code ? code : "sem idioma informado";
}

interface SiteImportDialogProps {
    work: WorkMetadataSearchResult;
    onClose: () => void;
    onImported: () => void;
}

/**
 * Importa uma série de site pelo manga-downloader (ADR-54), em três passos: a obra (pelo AniList ou
 * do catálogo), a URL da série com a listagem, e os idiomas, grupos e sinopse.
 */
export function SiteImportDialog({work, onClose, onImported}: SiteImportDialogProps) {
    const [step, setStep] = useState<Step>(1);
    // undefined: carregando; null: falhou
    const [preview, setPreview] = useState<WorkMetadataPreview | null | undefined>(undefined);
    const [target, setTarget] = useState<Target>({kind: "aniList"});
    const [url, setUrl] = useState("");
    const [listing, setListing] = useState<SiteSeriesListing | null>(null);
    const [listingError, setListingError] = useState<string | null>(null);
    const [listingLoading, setListingLoading] = useState(false);
    const [manual, setManual] = useState(false);
    const [rows, setRows] = useState<LanguageRow[]>([]);
    const [synopsis, setSynopsis] = useState("");
    const [submitting, setSubmitting] = useState(false);

    useEffect(() => {
        let cancelled = false;
        getMetadataPreview(work.externalId)
            .then((result) => {
                if (cancelled) return;
                setPreview(result);
                setSynopsis(result.synopsis ?? "");
            })
            .catch(() => !cancelled && setPreview(null));
        return () => {
            cancelled = true;
        };
    }, [work.externalId]);

    // a sinopse só vale quando o import cria a obra
    const createsWork = target.kind === "aniList" && !preview?.catalogWork;

    async function handleList(e: FormEvent) {
        e.preventDefault();
        setListingLoading(true);
        setListing(null);
        setListingError(null);
        setManual(false);
        try {
            const result = await listSiteSeries(url.trim());
            setListing(result);
            // pt-BR já marcado quando o site tem
            const ptBr = result.languages.find((l) => guessLanguage(l.code) === "pt-BR");
            setRows(ptBr ? [{siteCode: ptBr.code, language: "pt-BR", groupId: null, groupName: null}] : []);
        } catch (err) {
            setListingError(apiErrorMessage(err, "Não deu para listar esta série."));
        } finally {
            setListingLoading(false);
        }
    }

    function startManual() {
        setManual(true);
        setRows([{siteCode: "", language: "", groupId: null, groupName: null}]);
    }

    const canAdvance: Record<Step, boolean> = {
        1: preview !== undefined && preview !== null,
        2: isHttpUrl(url) && ((listing !== null && !listing.tracked) || manual),
        3: rows.length > 0 && rows.every((row) => row.language !== ""),
    };

    async function handleSubmit() {
        if (!preview) return;
        setSubmitting(true);
        try {
            await importWork({
                source: "MANGA_DOWNLOADER",
                externalId: url.trim(),
                linkWorkId: target.kind === "catalog" ? target.workId : null,
                aniListId: target.kind === "aniList" ? Number(preview.externalId) : null,
                synopsis: createsWork && synopsis.trim() ? synopsis.trim() : null,
                languages: rows.map((row) => ({
                    language: row.language,
                    sourceLanguage: row.siteCode,
                    preferredGroupId: row.groupId,
                    preferredGroupName: row.groupName,
                })),
            });
            toast.success("Importação iniciada. Os capítulos chegam aos poucos.");
            onImported();
            onClose();
        } catch (e) {
            toast.error(apiErrorMessage(e, "Não foi possível importar a série."));
        } finally {
            setSubmitting(false);
        }
    }

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 px-4">
            <div role="dialog" aria-modal="true" aria-labelledby="site-import-title"
                 className="flex max-h-[90vh] w-full max-w-lg flex-col rounded-lg border bg-card shadow-xl">
                <div className="flex items-center justify-between gap-3 border-b px-6 py-4">
                    <div className="min-w-0">
                        <h2 id="site-import-title" className="truncate text-base font-semibold">Importar {work.title}</h2>
                        <p className="font-mono text-[11px] text-muted-foreground">
                            Passo {step} de 3 · {STEP_TITLES[step]}
                        </p>
                    </div>
                    <button aria-label="Fechar" className="flex size-9 shrink-0 items-center justify-center" onClick={onClose}>
                        <X className="size-4 text-muted-foreground"/>
                    </button>
                </div>

                <div className="flex-1 space-y-5 overflow-y-auto px-6 py-5">
                    {step === 1 && <WorkStep work={work} preview={preview} target={target} onTarget={setTarget}/>}
                    {step === 2 && (
                        <SiteStep url={url} onUrl={(value) => {
                            setUrl(value);
                            setListing(null);
                            setListingError(null);
                            setManual(false);
                        }} onList={handleList} loading={listingLoading} listing={listing} error={listingError}
                                  manual={manual} onManual={startManual}/>
                    )}
                    {step === 3 && (
                        <LanguagesStep listing={manual ? null : listing} rows={rows} onRows={setRows}
                                       createsWork={createsWork} synopsis={synopsis} onSynopsis={setSynopsis}/>
                    )}
                </div>

                <div className="flex gap-2 border-t px-6 py-4">
                    {step === 1
                        ? <Button variant="outline" className="flex-1" onClick={onClose}>Cancelar</Button>
                        : (
                            <Button variant="outline" className="flex-1" disabled={submitting}
                                    onClick={() => setStep((step - 1) as Step)}>
                                <ChevronLeft className="size-4"/> Voltar
                            </Button>
                        )}
                    {step < 3
                        ? (
                            <Button className="flex-1" disabled={!canAdvance[step]} onClick={() => setStep((step + 1) as Step)}>
                                Próximo <ChevronRight className="size-4"/>
                            </Button>
                        )
                        : (
                            <Button className="flex-1" disabled={!canAdvance[3] || submitting} onClick={handleSubmit}>
                                <Download className="mr-1.5 size-4"/>
                                {submitting ? "Importando…" : "Importar"}
                            </Button>
                        )}
                </div>
            </div>
        </div>
    );
}

interface WorkStepProps {
    work: WorkMetadataSearchResult;
    preview: WorkMetadataPreview | null | undefined;
    target: Target;
    onTarget: (target: Target) => void;
}

/** Passo 1: a obra do AniList como vai entrar, e para onde vão os capítulos. */
function WorkStep({work, preview, target, onTarget}: WorkStepProps) {
    const [query, setQuery] = useState(work.title);
    const [candidates, setCandidates] = useState<WorkCard[]>([]);

    // obras do catálogo com título parecido, para não criar uma duplicata; abaixo de 2 letras, nenhuma
    const q = query.trim();
    const shown = q.length < 2 ? [] : candidates;
    useEffect(() => {
        if (q.length < 2) return;
        const timer = setTimeout(() => {
            listWorks({title: q, size: 5})
                .then((page) => setCandidates(page.content))
                .catch(() => setCandidates([]));
        }, 300);
        return () => clearTimeout(timer);
    }, [q]);

    if (preview === undefined) {
        return <p className="flex items-center gap-2 text-sm text-muted-foreground"><Loader2 className="size-4 animate-spin"/> Carregando a obra no AniList…</p>;
    }
    if (preview === null) {
        return <p className="text-sm text-destructive">O AniList não respondeu. Feche e tente de novo em alguns minutos.</p>;
    }

    return (
        <>
            <div className="flex gap-4">
                <MetadataCover url={preview.coverUrl} title={preview.title} className="w-20"/>
                <div className="min-w-0 space-y-1.5">
                    <p className="font-medium">{preview.title}</p>
                    <div className="flex flex-wrap gap-1.5">
                        <Badge variant="meta">{FORMAT_LABELS[preview.format] ?? preview.format}</Badge>
                        {preview.year && <Badge variant="meta">{preview.year}</Badge>}
                        {preview.contentWarnings.map((w) => <Badge key={w} variant="destructive">{w}</Badge>)}
                    </div>
                    <p className="text-xs text-muted-foreground">{preview.tags.map((t) => t.name).join(" · ")}</p>
                </div>
            </div>

            <fieldset className="space-y-3">
                <legend className="text-sm font-medium">Para onde vão os capítulos</legend>
                <label className="flex min-h-9 cursor-pointer items-start gap-2 text-sm">
                    <input type="radio" name="target" className="mt-1" checked={target.kind === "aniList"}
                           onChange={() => onTarget({kind: "aniList"})}/>
                    {preview.catalogWork
                        ? <span>Para <strong>{preview.catalogWork.title}</strong>, que já está ligada a esta obra do AniList</span>
                        : <span>Uma obra nova, com os dados do AniList</span>}
                </label>
                <div className="space-y-1.5">
                    <Label htmlFor="catalog-query">Ou para outra obra do catálogo</Label>
                    <Input id="catalog-query" value={query} onChange={(e) => setQuery(e.target.value)}
                           placeholder="Título da obra no catálogo"/>
                </div>
                {shown.filter((c) => c.id !== preview.catalogWork?.id).map((candidate) => (
                    <label key={candidate.id} className="flex min-h-9 cursor-pointer items-center gap-2 text-sm">
                        <input type="radio" name="target"
                               checked={target.kind === "catalog" && target.workId === candidate.id}
                               onChange={() => onTarget({kind: "catalog", workId: candidate.id, title: candidate.title})}/>
                        <span className="truncate">{candidate.title}</span>
                        {candidate.year && <span className="font-mono text-xs text-muted-foreground">{candidate.year}</span>}
                    </label>
                ))}
            </fieldset>
        </>
    );
}

interface SiteStepProps {
    url: string;
    onUrl: (url: string) => void;
    onList: (e: FormEvent) => void;
    loading: boolean;
    listing: SiteSeriesListing | null;
    error: string | null;
    manual: boolean;
    onManual: () => void;
}

/** Passo 2: a URL da série e o que o site tem dela. */
function SiteStep({url, onUrl, onList, loading, listing, error, manual, onManual}: SiteStepProps) {
    const locked = listing?.chapters.filter((c) => c.locked).length ?? 0;
    const unnumbered = listing?.chapters.filter((c) => c.number === null && !c.locked).length ?? 0;

    return (
        <>
            <form onSubmit={onList} className="space-y-1.5">
                <Label htmlFor="series-url">Endereço da série no site</Label>
                <div className="flex gap-2">
                    {/* travado enquanto lista: a resposta de uma URL antiga não pode valer para a nova */}
                    <Input id="series-url" type="url" value={url} onChange={(e) => onUrl(e.target.value)}
                           placeholder="https://site.com/manga/nome-da-obra" maxLength={500} disabled={loading}/>
                    <Button type="submit" variant="outline" disabled={loading || !isHttpUrl(url)}>
                        {loading ? <Loader2 className="size-4 animate-spin"/> : <ListChecks className="size-4"/>}
                        Listar
                    </Button>
                </div>
                <p className="text-xs text-muted-foreground">
                    A página que lista os capítulos. O endereço de um capítulo também serve.
                </p>
            </form>

            {loading && <p className="text-sm text-muted-foreground">Listando a série no site. Leva alguns segundos.</p>}

            {listing && (
                <div className="space-y-1.5 rounded-md border p-3 text-sm">
                    <p className="font-medium">{listing.title}</p>
                    <p className="text-muted-foreground">
                        {listing.chapters.length} capítulos ·{" "}
                        {listing.languages.map((l) => siteLanguageLabel(l.code)).join(", ")} ·{" "}
                        {listing.groups.length === 1 ? "1 grupo" : `${listing.groups.length} grupos`}
                    </p>
                    {locked > 0 && <p className="text-xs text-muted-foreground">{locked} bloqueados no site (moedas ou acesso antecipado) ficam de fora.</p>}
                    {unnumbered > 0 && <p className="text-xs text-muted-foreground">{unnumbered} sem número não podem ser baixados.</p>}
                    {listing.tracked && <p className="text-xs text-destructive">Esta série já está sendo acompanhada.</p>}
                </div>
            )}

            {error && !manual && (
                <div className="space-y-2 rounded-md border border-destructive/40 p-3 text-sm">
                    <p className="flex items-start gap-1.5 text-destructive">
                        <AlertTriangle className="mt-0.5 size-4 shrink-0"/> {error}
                    </p>
                    <p className="text-xs text-muted-foreground">
                        Sites que exigem navegador não listam aqui, mas baixam normalmente. Dá para seguir
                        informando o idioma e o grupo à mão.
                    </p>
                    <Button size="sm" variant="outline" onClick={onManual}>Seguir sem a listagem</Button>
                </div>
            )}
            {manual && <p className="text-sm text-muted-foreground">Sem a listagem: no próximo passo, informe o idioma e o grupo.</p>}
        </>
    );
}

interface LanguagesStepProps {
    listing: SiteSeriesListing | null;
    rows: LanguageRow[];
    onRows: (rows: LanguageRow[]) => void;
    createsWork: boolean;
    synopsis: string;
    onSynopsis: (synopsis: string) => void;
}

/** Passo 3: idiomas, o idioma do Burūna de cada um, grupo preferido e sinopse. */
function LanguagesStep({listing, rows, onRows, createsWork, synopsis, onSynopsis}: LanguagesStepProps) {
    function update(siteCode: string, patch: Partial<LanguageRow>) {
        onRows(rows.map((row) => row.siteCode === siteCode ? {...row, ...patch} : row));
    }

    function toggle(code: string) {
        onRows(rows.some((row) => row.siteCode === code)
            ? rows.filter((row) => row.siteCode !== code)
            : [...rows, {siteCode: code, language: guessLanguage(code), groupId: null, groupName: null}]);
    }

    return (
        <>
            <fieldset className="space-y-3">
                <legend className="text-sm font-medium">Idiomas</legend>
                <p className="text-xs text-muted-foreground">
                    Todos os capítulos dos idiomas marcados são baixados. Quando um número tem versões de
                    mais de um grupo, entra a do grupo preferido; sem preferência, uma delas.
                </p>

                {listing && (
                    <div className="flex flex-wrap gap-2">
                        {listing.languages.map((l) => (
                            <label key={l.code}
                                   className="flex min-h-9 cursor-pointer items-center gap-2 rounded-md border px-3 text-sm has-[:checked]:border-foreground">
                                <input type="checkbox" checked={rows.some((row) => row.siteCode === l.code)}
                                       onChange={() => toggle(l.code)}/>
                                {siteLanguageLabel(l.code)} <span className="font-mono text-xs text-muted-foreground">{l.chapterCount}</span>
                            </label>
                        ))}
                    </div>
                )}

                {rows.map((row, index) => (
                    <div key={listing ? row.siteCode : index} className="space-y-2 rounded-md border p-3">
                        {listing
                            ? <p className="text-sm font-medium">No site: {siteLanguageLabel(row.siteCode)}</p>
                            : (
                                <div className="space-y-1.5">
                                    <Label htmlFor="manual-code">Código do idioma no site</Label>
                                    <Input id="manual-code" value={row.siteCode} maxLength={35} placeholder="es-la, en… vazio se o site não informa"
                                           onChange={(e) => onRows([{...row, siteCode: e.target.value, language: guessLanguage(e.target.value) || row.language}])}/>
                                </div>
                            )}
                        <div className="space-y-1.5">
                            <Label htmlFor={`language-${index}`}>Idioma no Burūna</Label>
                            <NativeSelect id={`language-${index}`} value={row.language}
                                          onChange={(e) => listing ? update(row.siteCode, {language: e.target.value})
                                              : onRows([{...row, language: e.target.value}])}>
                                <option value="">Escolha o idioma</option>
                                {row.language && !SITE_LANGUAGES.includes(row.language) && (
                                    <option value={row.language}>{languageName(row.language)}</option>
                                )}
                                {SITE_LANGUAGES.map((l) => <option key={l} value={l}>{languageName(l)}</option>)}
                            </NativeSelect>
                        </div>
                        {listing && listing.groups.length > 0 && (
                            <div className="space-y-1.5">
                                <Label htmlFor={`group-${index}`}>Grupo preferido</Label>
                                <NativeSelect id={`group-${index}`} value={row.groupId ?? ""}
                                              onChange={(e) => {
                                                  const group = listing.groups.find((g) => g.id === e.target.value);
                                                  update(row.siteCode, {groupId: group?.id ?? null, groupName: group?.name ?? null});
                                              }}>
                                    <option value="">Sem preferência</option>
                                    {listing.groups.map((g) => (
                                        <option key={g.id} value={g.id}>{g.name} ({g.chapterCount} cap.)</option>
                                    ))}
                                </NativeSelect>
                            </div>
                        )}
                        {!listing && (
                            <div className="space-y-1.5">
                                <Label htmlFor="manual-group">Grupo preferido (opcional)</Label>
                                <Input id="manual-group" value={row.groupName ?? ""} maxLength={255}
                                       placeholder="O nome exato, como o site escreve"
                                       onChange={(e) => {
                                           const name = e.target.value.trim();
                                           onRows([{...row, groupName: name || null, groupId: name ? siteGroupId(name) : null}]);
                                       }}/>
                            </div>
                        )}
                    </div>
                ))}
            </fieldset>

            {createsWork && (
                <div className="space-y-1.5">
                    <Label htmlFor="synopsis">Sinopse</Label>
                    <Textarea id="synopsis" value={synopsis} onChange={(e) => onSynopsis(e.target.value)} rows={6}
                              maxLength={10000}/>
                    <p className="text-xs text-muted-foreground">Vem do AniList, em inglês. Traduza antes de importar.</p>
                </div>
            )}
        </>
    );
}
