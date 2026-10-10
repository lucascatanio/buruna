import {useCallback, useEffect, useRef, useState} from "react";
import type {FoliateLocation, FoliateTocItem, FoliateView} from "foliate-js/view.js";
import {Loading} from "@/components/Loading";
import {ArrowLeft, List, Type, X} from "lucide-react";

type Theme = "light" | "sepia" | "dark";
type FontFamily = "original" | "serif" | "sans";
type Flow = "paginated" | "scrolled";

interface EpubSettings {
    theme: Theme;
    font: FontFamily;
    /** Tamanho do texto em %. */
    size: number;
    flow: Flow;
}

const DEFAULT_SETTINGS: EpubSettings = {theme: "light", font: "original", size: 100, flow: "paginated"};
const SETTINGS_KEY = "epub-settings";
const MIN_SIZE = 80;
const MAX_SIZE = 160;
const SIZE_STEP = 10;
const CONTROLS_HIDE_MS = 3000;

const THEMES: Record<Theme, { label: string; background: string; text: string; link: string }> = {
    light: {label: "Claro", background: "#ffffff", text: "#1a1a1a", link: "#b3261e"},
    sepia: {label: "Sépia", background: "#f4ecd8", text: "#3b2f1e", link: "#8a3b12"},
    dark: {label: "Escuro", background: "#121212", text: "#d8d4cc", link: "#f08a7e"},
};

const FONTS: Record<FontFamily, { label: string; css: string | null }> = {
    original: {label: "Do livro", css: null},
    serif: {label: "Serifada", css: "Georgia, 'Times New Roman', serif"},
    sans: {label: "Sem serifa", css: "system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif"},
};

// valor desconhecido (de outra versão do app, ou editado à mão) volta ao padrão
function loadSettings(): EpubSettings {
    try {
        const stored = JSON.parse(localStorage.getItem(SETTINGS_KEY) ?? "null") ?? {};
        const size = Number(stored.size);
        return {
            theme: Object.hasOwn(THEMES, stored.theme) ? stored.theme : DEFAULT_SETTINGS.theme,
            font: Object.hasOwn(FONTS, stored.font) ? stored.font : DEFAULT_SETTINGS.font,
            size: size >= MIN_SIZE && size <= MAX_SIZE ? size : DEFAULT_SETTINGS.size,
            flow: stored.flow === "scrolled" ? "scrolled" : "paginated",
        };
    } catch {
        return DEFAULT_SETTINGS;
    }
}

function storeSettings(settings: EpubSettings) {
    try {
        localStorage.setItem(SETTINGS_KEY, JSON.stringify(settings));
    } catch {
        // navegação privada: vale só nesta sessão
    }
}

// --theme-bg-color: o foliate-js pinta com ela as margens em volta do texto
function bookCss({theme, font, size}: EpubSettings): string {
    const colors = THEMES[theme];
    const family = FONTS[font].css;
    return `
        html { color-scheme: ${theme === "dark" ? "dark" : "light"}; --theme-bg-color: ${colors.background}; }
        html, body { background: ${colors.background} !important; color: ${colors.text} !important; }
        body { font-size: ${size}% !important; ${family ? `font-family: ${family} !important;` : ""} }
        ${family ? `p, li, blockquote, dd, span, div { font-family: inherit !important; }` : ""}
        a:any-link { color: ${colors.link} !important; }
    `;
}

const ACTIVE_CONTENT_TYPES = ["application/xhtml+xml", "text/html", "image/svg+xml"];
const URL_ATTRIBUTES = ["href", "src", "xlink:href", "action", "formaction"];

/**
 * Tira do HTML do livro o que pode executar código: scripts, handlers on*, links javascript: e
 * conteúdo embutido. O foliate-js abre cada capítulo num iframe com allow-scripts (por um bug
 * do WebKit) e na mesma origem; o CSP herdado pelo blob: já bloqueia script, e isto é a segunda
 * barreira (ADR-51).
 */
function stripActiveContent(markup: string, type: string): string {
    const doc = new DOMParser().parseFromString(markup, type as DOMParserSupportedType);
    doc.querySelectorAll("script, iframe, frame, object, embed, applet, form, base, meta[http-equiv]")
        .forEach((el) => el.remove());
    // SVG <set>/<animate> conseguem trocar um href para javascript: depois da carga
    doc.querySelectorAll("set, animate").forEach((el) => {
        if (/href/i.test(el.getAttribute("attributeName") ?? "")) el.remove();
    });
    doc.querySelectorAll("*").forEach((el) => {
        for (const attr of [...el.attributes]) {
            const name = attr.name.toLowerCase();
            const value = attr.value.replace(/[\s\p{Cc}]/gu, "").toLowerCase();
            if (name.startsWith("on")
                || (URL_ATTRIBUTES.includes(name) && (value.startsWith("javascript:") || value.startsWith("data:text/html")))) {
                el.removeAttribute(attr.name);
            }
        }
    });
    return new XMLSerializer().serializeToString(doc);
}

function sanitizeResource(event: Event) {
    const detail = (event as CustomEvent<{ data: unknown; type: string }>).detail;
    // o tipo vem do manifesto do livro, escrito por quem enviou
    const type = String(detail.type ?? "").toLowerCase();
    if (!ACTIVE_CONTENT_TYPES.includes(type)) return;
    // HTML com tipo fora do padrão não passa pela troca de links do foliate-js e chega como Blob
    detail.data = Promise.resolve(detail.data).then(async (data) => {
        const markup = typeof data === "string" ? data : data instanceof Blob ? await data.text() : null;
        return markup === null ? data : stripActiveContent(markup, type);
    });
}

// URL assinada vencida: o GCS responde 400 ou 403
async function downloadBook(url: string, fetchFreshUrl: () => Promise<string>): Promise<File> {
    let response = await fetch(url);
    if (response.status === 400 || response.status === 403) response = await fetch(await fetchFreshUrl());
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    return new File([await response.blob()], "livro.epub", {type: "application/epub+zip"});
}

export interface EpubReaderProps {
    url: string;
    fetchFreshUrl: () => Promise<string>;
    /** CFI salvo; nulo abre no começo do texto. */
    initialPosition: string | null;
    title?: string;
    badge?: string;
    /** A cada mudança de posição: o CFI e o andamento de 0 a 1. */
    onRelocate: (position: string, percent: number) => void;
    onBack: () => void;
}

/**
 * Leitor de EPUB com foliate-js. Baixa o arquivo inteiro uma vez (EPUB é zip, não dá para ler
 * por trechos) e guarda a posição como CFI. Tema, fonte, tamanho e modo ficam no navegador.
 */
export function EpubReader({url, fetchFreshUrl, initialPosition, title, badge, onRelocate, onBack}: EpubReaderProps) {
    const containerRef = useRef<HTMLDivElement>(null);
    const viewRef = useRef<FoliateView | null>(null);
    const [ready, setReady] = useState(false);
    const [loadError, setLoadError] = useState<string | null>(null);
    const [settings, setSettings] = useState<EpubSettings>(loadSettings);
    const [toc, setToc] = useState<FoliateTocItem[]>([]);
    const [location, setLocation] = useState<{ percent: number; section: string | null }>({percent: 0, section: null});
    const [showControls, setShowControls] = useState(true);
    const [panel, setPanel] = useState<"toc" | "settings" | null>(null);
    const controlsTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
    const panelRef = useRef(panel);
    const showControlsRef = useRef(showControls);

    const callbacksRef = useRef({onRelocate, fetchFreshUrl});
    useEffect(() => {
        callbacksRef.current = {onRelocate, fetchFreshUrl};
        panelRef.current = panel;
        showControlsRef.current = showControls;
    });

    const showControlsTemporarily = useCallback(() => {
        setShowControls(true);
        if (controlsTimer.current) clearTimeout(controlsTimer.current);
        controlsTimer.current = setTimeout(() => {
            if (!panelRef.current) setShowControls(false);
        }, CONTROLS_HIDE_MS);
    }, []);

    // chamado de dentro do iframe do livro, que guarda a primeira versão da função
    const toggleControls = useCallback(() => {
        if (showControlsRef.current) {
            setShowControls(false);
            if (controlsTimer.current) clearTimeout(controlsTimer.current);
        } else {
            showControlsTemporarily();
        }
    }, [showControlsTemporarily]);

    useEffect(() => {
        showControlsTemporarily();
        return () => {
            if (controlsTimer.current) clearTimeout(controlsTimer.current);
        };
    }, [showControlsTemporarily]);

    // abre o livro uma vez por URL
    useEffect(() => {
        let cancelled = false;
        let view: FoliateView | null = null;

        async function open() {
            // o foliate-js só é baixado quando alguém abre um EPUB
            const [{makeBook}, file] = await Promise.all([
                import("foliate-js/view.js"),
                downloadBook(url, () => callbacksRef.current.fetchFreshUrl()),
            ]);
            if (cancelled || !containerRef.current) return;
            const book = await makeBook(file);
            // antes de abrir: nenhum capítulo pode carregar sem passar pela limpeza
            book.transformTarget?.addEventListener("data", sanitizeResource);

            view = document.createElement("foliate-view") as FoliateView;
            view.style.display = "block";
            view.style.height = "100%";
            view.addEventListener("relocate", (e) => {
                const detail = (e as CustomEvent<FoliateLocation>).detail;
                const percent = Math.max(0, Math.min(1, detail.fraction ?? 0));
                setLocation({percent, section: detail.tocItem?.label?.trim() || null});
                if (detail.cfi) callbacksRef.current.onRelocate(detail.cfi, percent);
            });
            view.addEventListener("load", (e) => {
                const doc = (e as CustomEvent<{ doc: Document }>).detail.doc;
                doc.addEventListener("click", (click) => {
                    // link e seleção de texto não mexem nos controles
                    if ((click.target as Element | null)?.closest?.("a")) return;
                    if (doc.getSelection()?.isCollapsed === false) return;
                    toggleControls();
                });
                doc.addEventListener("keydown", handleKey);
            });
            view.addEventListener("external-link", (e) => {
                e.preventDefault();
                const href = (e as CustomEvent<{ href: string }>).detail.href;
                if (/^https?:\/\//i.test(href)) window.open(href, "_blank", "noopener,noreferrer");
            });
            containerRef.current.append(view);
            await view.open(book);
            if (cancelled) return;
            view.renderer.setAttribute("flow", settings.flow);
            view.renderer.setStyles(bookCss(settings));
            try {
                await view.init({lastLocation: initialPosition, showTextStart: true});
            } catch {
                // CFI salvo não bate com o arquivo (edição trocada): começa do texto
                await view.init({showTextStart: true});
            }
            viewRef.current = view;
            setToc(book.toc ?? []);
            setReady(true);
        }

        open().catch((e) => {
            console.error("Erro ao abrir o EPUB:", e);
            if (!cancelled) setLoadError("Não foi possível abrir o livro. Verifique sua conexão.");
        });

        return () => {
            cancelled = true;
            viewRef.current = null;
            if (view) {
                view.close();
                view.book?.destroy?.();
                view.remove();
            }
        };
        // as preferências entram pelo efeito abaixo; aqui só valem as do momento da abertura
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [url]);

    useEffect(() => {
        storeSettings(settings);
        const view = viewRef.current;
        if (!view) return;
        view.renderer.setAttribute("flow", settings.flow);
        view.renderer.setStyles(bookCss(settings));
    }, [settings, ready]);

    function handleKey(e: KeyboardEvent) {
        const view = viewRef.current;
        if (!view) return;
        if (e.key === "ArrowRight") void view.goRight();
        if (e.key === "ArrowLeft") void view.goLeft();
    }

    useEffect(() => {
        window.addEventListener("keydown", handleKey);
        return () => window.removeEventListener("keydown", handleKey);
    }, []);

    function goToToc(item: FoliateTocItem) {
        void viewRef.current?.goTo(item.href);
        setPanel(null);
    }

    function update(patch: Partial<EpubSettings>) {
        setSettings((current) => ({...current, ...patch}));
    }

    const colors = THEMES[settings.theme];

    if (loadError) {
        return (
            <div className="fixed inset-0 z-50 flex items-center justify-center bg-black">
                <div className="flex flex-col items-center gap-4 px-6 text-center">
                    <p className="text-paper/80">{loadError}</p>
                    <button
                        className="min-h-11 px-3 text-sm text-paper/60 underline underline-offset-4 hover:text-paper"
                        onClick={onBack}
                    >
                        Voltar
                    </button>
                </div>
            </div>
        );
    }

    return (
        <div className="fixed inset-0 z-50 flex flex-col select-none" style={{background: colors.background}}>
            <div ref={containerRef} className="relative flex-1 overflow-hidden"/>

            {!ready && (
                <div className="absolute inset-0 flex items-center justify-center bg-black">
                    <Loading className="text-paper" label="Carregando livro"/>
                </div>
            )}

            {/* faixas laterais para virar a página; o meio do texto mostra os controles */}
            {ready && settings.flow === "paginated" && (
                <>
                    <button aria-label="Página anterior" className="absolute inset-y-16 left-0 w-[12%] cursor-w-resize"
                            onClick={() => void viewRef.current?.goLeft()}/>
                    <button aria-label="Próxima página" className="absolute inset-y-16 right-0 w-[12%] cursor-e-resize"
                            onClick={() => void viewRef.current?.goRight()}/>
                </>
            )}

            <div
                className={`absolute inset-x-0 top-0 z-10 border-b border-paper/10 bg-ink/85 backdrop-blur-sm transition-opacity duration-200 ${showControls || panel ? "opacity-100" : "pointer-events-none opacity-0"}`}
            >
                <div className="flex items-center justify-between px-3 py-2">
                    <button
                        className="flex min-h-11 min-w-0 items-center gap-2.5 px-1.5 text-paper/85 transition-colors hover:text-paper"
                        onClick={onBack}
                        aria-label={`Voltar${title ? ` para ${title}` : ""}`}
                    >
                        <ArrowLeft className="size-5 shrink-0"/>
                        <span className="max-w-[45vw] truncate text-sm font-medium sm:max-w-[320px]">{title ?? "Voltar"}</span>
                        {badge && <span className="shrink-0 truncate font-mono text-xs text-paper/50">{badge}</span>}
                    </button>
                    <div className="flex items-center gap-1">
                        <button
                            className={`flex size-11 items-center justify-center rounded-sm transition-colors hover:bg-paper/10 ${panel === "toc" ? "bg-paper/10 text-paper" : "text-paper/70 hover:text-paper"}`}
                            aria-label="Sumário"
                            aria-expanded={panel === "toc"}
                            disabled={toc.length === 0}
                            onClick={() => setPanel((p) => p === "toc" ? null : "toc")}
                        >
                            <List className="size-5"/>
                        </button>
                        <button
                            className={`flex size-11 items-center justify-center rounded-sm transition-colors hover:bg-paper/10 ${panel === "settings" ? "bg-paper/10 text-paper" : "text-paper/70 hover:text-paper"}`}
                            aria-label="Ajustes de leitura"
                            aria-expanded={panel === "settings"}
                            onClick={() => setPanel((p) => p === "settings" ? null : "settings")}
                        >
                            <Type className="size-5"/>
                        </button>
                    </div>
                </div>

                {panel === "settings" && (
                    <div className="space-y-4 border-t border-paper/10 px-4 py-3">
                        <div className="flex items-center justify-between">
                            <span className="font-mono text-[11px] uppercase tracking-widest text-paper/60">Ajustes de leitura</span>
                            <button aria-label="Fechar ajustes" className="flex size-9 items-center justify-center" onClick={() => setPanel(null)}>
                                <X className="size-4 text-paper/40 transition-colors hover:text-paper"/>
                            </button>
                        </div>
                        <OptionRow label="Tema" value={settings.theme} onChange={(theme) => update({theme})}
                                   options={(Object.keys(THEMES) as Theme[]).map((t) => [t, THEMES[t].label])}/>
                        <OptionRow label="Fonte" value={settings.font} onChange={(font) => update({font})}
                                   options={(Object.keys(FONTS) as FontFamily[]).map((f) => [f, FONTS[f].label])}/>
                        <div className="flex items-center justify-between gap-3">
                            <span className="text-xs text-paper/60">Tamanho</span>
                            <div className="flex items-center gap-2">
                                <button className="h-9 min-w-11 rounded-sm bg-paper/10 px-3 text-sm text-paper disabled:opacity-30"
                                        aria-label="Diminuir o texto" disabled={settings.size <= MIN_SIZE}
                                        onClick={() => update({size: settings.size - SIZE_STEP})}>A−</button>
                                <span className="w-12 text-center font-mono text-xs text-paper">{settings.size}%</span>
                                <button className="h-9 min-w-11 rounded-sm bg-paper/10 px-3 text-sm text-paper disabled:opacity-30"
                                        aria-label="Aumentar o texto" disabled={settings.size >= MAX_SIZE}
                                        onClick={() => update({size: settings.size + SIZE_STEP})}>A+</button>
                            </div>
                        </div>
                        <OptionRow label="Modo" value={settings.flow} onChange={(flow) => update({flow})}
                                   options={[["paginated", "Página a página"], ["scrolled", "Rolagem"]]}/>
                    </div>
                )}

                {panel === "toc" && (
                    <nav aria-label="Sumário" className="max-h-[60vh] overflow-y-auto border-t border-paper/10 py-2">
                        <TocList items={toc} onSelect={goToToc}/>
                    </nav>
                )}
            </div>

            <div className="pointer-events-none absolute inset-x-0 bottom-0 z-10">
                <div className={`flex justify-between gap-3 px-4 pb-1.5 font-mono text-[11px] transition-opacity duration-200 ${showControls ? "opacity-100" : "opacity-0"}`}
                     style={{color: colors.text}}>
                    <span className="truncate opacity-60">{location.section}</span>
                    <span className="shrink-0 opacity-60">{Math.round(location.percent * 100)}%</span>
                </div>
                <div aria-hidden="true" className="h-0.5 bg-paper/10">
                    <div className="h-full bg-shu transition-[width] duration-200" style={{width: `${location.percent * 100}%`}}/>
                </div>
            </div>
        </div>
    );
}

interface OptionRowProps<T extends string> {
    label: string;
    value: T;
    options: [T, string][];
    onChange: (value: T) => void;
}

function OptionRow<T extends string>({label, value, options, onChange}: OptionRowProps<T>) {
    return (
        <div className="flex items-center justify-between gap-3">
            <span className="text-xs text-paper/60">{label}</span>
            <div className="flex gap-1" role="radiogroup" aria-label={label}>
                {options.map(([option, text]) => (
                    <button
                        key={option}
                        role="radio"
                        aria-checked={value === option}
                        className={`h-9 rounded-sm px-3 text-xs transition-colors ${value === option ? "bg-paper text-ink" : "bg-paper/10 text-paper hover:bg-paper/20"}`}
                        onClick={() => onChange(option)}
                    >
                        {text}
                    </button>
                ))}
            </div>
        </div>
    );
}

function TocList({items, onSelect, depth = 0}: { items: FoliateTocItem[]; onSelect: (item: FoliateTocItem) => void; depth?: number }) {
    return (
        <ul>
            {items.map((item, i) => (
                <li key={`${item.href}-${i}`}>
                    <button
                        className="block min-h-11 w-full px-4 py-2 text-left text-sm text-paper/85 hover:bg-paper/10 hover:text-paper"
                        style={{paddingLeft: `${16 + depth * 16}px`}}
                        onClick={() => onSelect(item)}
                    >
                        {item.label?.trim() || "Sem título"}
                    </button>
                    {item.subitems && item.subitems.length > 0 && (
                        <TocList items={item.subitems} onSelect={onSelect} depth={depth + 1}/>
                    )}
                </li>
            ))}
        </ul>
    );
}
