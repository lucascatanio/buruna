import {useEffect, useState, useCallback} from "react";
import {Link} from "react-router-dom";
import {listMangas} from "@/api/mangaApi";
import type {Page} from "@/types/common";
import type {MangaCard} from "@/types/manga";
import {Button} from "@/components/ui/button";
import {Input} from "@/components/ui/input";
import {TagSelector} from "@/components/TagSelector";
import {PageHeader} from "@/components/PageHeader";
import {EmptyState} from "@/components/EmptyState";
import {MangaCover} from "@/components/MangaCover";
import {Search, SlidersHorizontal, X, Plus, Star, ChevronLeft, ChevronRight} from "lucide-react";
import {useAuthStore} from "@/store/authStore";

const FORMAT_OPTIONS = [
    {value: "", label: "Todos os formatos"},
    {value: "MANGA", label: "Mangá"},
    {value: "MANHWA", label: "Manhwa"},
    {value: "MANHUA", label: "Manhua"},
    {value: "WEBTOON", label: "Webtoon"},
    {value: "ONE_SHOT", label: "One-shot"},
    {value: "LIVRO", label: "Livro"},
];

const STATUS_OPTIONS = [
    {value: "", label: "Todos os status"},
    {value: "ONGOING", label: "Em andamento"},
    {value: "COMPLETED", label: "Completo"},
    {value: "HIATUS", label: "Hiato"},
    {value: "CANCELLED", label: "Cancelado"},
];

const FORMAT_LABELS: Record<string, string> = {
    MANGA: "Mangá", MANHWA: "Manhwa", MANHUA: "Manhua",
    WEBTOON: "Webtoon", ONE_SHOT: "One-shot",
    LIVRO: "Livro",
};

export function LibraryPage() {
    const user = useAuthStore((s) => s.user);
    const isCollab = user?.role === "COLLABORATOR" || user?.role === "ADMIN";
    const [data, setData] = useState<Page<MangaCard> | null>(null);
    const [loading, setLoading] = useState(true);
    const [showFilters, setShowFilters] = useState(false);

    const [titleInput, setTitleInput] = useState("");
    const [title, setTitle] = useState("");
    const [format, setFormat] = useState("");
    const [statusOrigin, setStatusOrigin] = useState("");
    const [tagIds, setTagIds] = useState<string[]>([]);
    const [page, setPage] = useState(0);

    const hasActiveFilters = format !== "" || statusOrigin !== "" || tagIds.length > 0;

    const fetchMangas = useCallback(async (pageNum: number) => {
        setLoading(true);
        try {
            const res = await listMangas({page: pageNum, size: 24, title, format, statusOrigin, tagIds});
            setData(res);
        } finally {
            setLoading(false);
        }
    }, [title, format, statusOrigin, tagIds]);

    useEffect(() => {
        setPage(0);
        fetchMangas(0);
    }, [fetchMangas]);

    function handleSearch(e: React.FormEvent) {
        e.preventDefault();
        setTitle(titleInput);
    }

    function clearFilters() {
        setFormat("");
        setStatusOrigin("");
        setTagIds([]);
    }

    function changePage(newPage: number) {
        setPage(newPage);
        fetchMangas(newPage);
        window.scrollTo({top: 0, behavior: "smooth"});
    }

    return (
        <div className="max-w-7xl mx-auto px-4 md:px-8 py-8 md:py-10 flex flex-col gap-7">
            <PageHeader
                title="Biblioteca"
                description="Todos os mangás disponíveis no Burūna."
                actions={
                    <>
                        <form onSubmit={handleSearch} role="search" className="relative flex-1 min-w-60">
                            <Search aria-hidden="true" className="absolute left-3.5 top-1/2 -translate-y-1/2 size-4 text-muted-foreground"/>
                            <Input
                                type="search"
                                aria-label="Buscar por título"
                                className="h-11 pl-10 text-[15px]"
                                placeholder="Buscar por título…"
                                value={titleInput}
                                onChange={(e) => setTitleInput(e.target.value)}
                            />
                        </form>
                        <Button
                            variant="outline"
                            onClick={() => setShowFilters((v) => !v)}
                            aria-expanded={showFilters}
                            className="relative h-11 px-4"
                        >
                            <SlidersHorizontal className="size-4"/>
                            Filtros
                            {hasActiveFilters && (
                                <span aria-label="Filtros ativos" className="absolute -top-1 -right-1 size-2.5 -skew-x-15 bg-shu"/>
                            )}
                        </Button>
                    </>
                }
            />

            {showFilters && (
                <div className="border rounded-lg p-4 space-y-4 bg-card">
                    <div className="flex items-center justify-between">
                        <span className="text-sm font-medium">Filtros</span>
                        {hasActiveFilters && (
                            <Button variant="ghost" size="sm" onClick={clearFilters}>
                                <X className="size-3"/> Limpar
                            </Button>
                        )}
                    </div>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                        <div>
                            <label className="text-xs text-muted-foreground mb-1 block">Formato</label>
                            <select
                                className="w-full border rounded-md px-3 py-2 text-sm bg-background"
                                value={format}
                                onChange={(e) => setFormat(e.target.value)}
                            >
                                {FORMAT_OPTIONS.map((o) => (
                                    <option key={o.value} value={o.value}>{o.label}</option>
                                ))}
                            </select>
                        </div>
                        <div>
                            <label className="text-xs text-muted-foreground mb-1 block">Status</label>
                            <select
                                className="w-full border rounded-md px-3 py-2 text-sm bg-background"
                                value={statusOrigin}
                                onChange={(e) => setStatusOrigin(e.target.value)}
                            >
                                {STATUS_OPTIONS.map((o) => (
                                    <option key={o.value} value={o.value}>{o.label}</option>
                                ))}
                            </select>
                        </div>
                    </div>
                    <div>
                        <label className="text-xs text-muted-foreground mb-1 block">Tags</label>
                        <TagSelector selectedIds={tagIds} onChange={setTagIds} excludeCategories={["Aviso de Conteúdo"]} />
                    </div>
                </div>
            )}

            <div className="flex items-center justify-between gap-3 border-t pt-4">
                <span className="font-mono text-xs uppercase tracking-wider text-muted-foreground">
                    {data ? `${data.totalElements} ${data.totalElements === 1 ? "resultado" : "resultados"}` : "\u00a0"}
                </span>
            </div>

            {loading && (
                <div className="grid grid-cols-3 gap-x-3 gap-y-6 sm:grid-cols-[repeat(auto-fill,minmax(150px,1fr))] sm:gap-x-5 sm:gap-y-8">
                    {Array.from({length: 12}).map((_, i) => (
                        <div key={i} className="flex flex-col gap-2.5 animate-pulse">
                            <div className="aspect-[2/3] rounded-lg bg-card screentone"/>
                            <div className="h-3 w-3/4 bg-card"/>
                        </div>
                    ))}
                </div>
            )}

            {!loading && data?.content.length === 0 && (
                <EmptyState
                    title="Nenhum mangá encontrado"
                    description={title || hasActiveFilters ? "Tente outro título ou tire alguns filtros." : "A biblioteca ainda não tem mangás publicados."}
                    action={(title || hasActiveFilters) && (
                        <Button
                            onClick={() => {
                                setTitleInput("");
                                setTitle("");
                                clearFilters();
                            }}
                        >
                            Limpar busca
                        </Button>
                    )}
                />
            )}

            {!loading && data && data.content.length > 0 && (
                <div className="grid grid-cols-3 gap-x-3 gap-y-6 sm:grid-cols-[repeat(auto-fill,minmax(150px,1fr))] sm:gap-x-5 sm:gap-y-8">
                    {data.content.map((manga) => (
                        <MangaCardItem key={manga.id} manga={manga}/>
                    ))}
                </div>
            )}

            {data && data.totalPages > 1 && (
                <nav aria-label="Paginação" className="flex items-center justify-center gap-3 pt-2 pb-8">
                    <Button variant="outline" className="h-10" disabled={page === 0} onClick={() => changePage(page - 1)}>
                        <ChevronLeft className="size-4"/>
                        Anterior
                    </Button>
                    <span className="font-mono text-[13px] text-muted-foreground">
                        p. <span className="text-foreground">{page + 1}</span> / {data.totalPages}
                    </span>
                    <Button variant="outline" className="h-10" disabled={page + 1 >= data.totalPages} onClick={() => changePage(page + 1)}>
                        Próxima
                        <ChevronRight className="size-4"/>
                    </Button>
                </nav>
            )}

            {isCollab && (
                <Link
                    to="/mangas/novo"
                    aria-label="Publicar mangá"
                    className="fixed bottom-24 right-5 md:bottom-8 md:right-8 z-50 size-14 rounded-lg bg-primary text-primary-foreground shadow-[0_10px_30px_rgba(0,0,0,.5)] flex items-center justify-center hover:bg-primary/90 transition-colors"
                >
                    <Plus className="size-6"/>
                </Link>
            )}
        </div>
    );
}

function formatRating(value: number): string {
    return value.toLocaleString("pt-BR", {minimumFractionDigits: 1, maximumFractionDigits: 1});
}

function MangaCardItem({manga}: {manga: MangaCard}) {
    return (
        <Link to={`/biblioteca/${manga.slug}`} className="group flex flex-col gap-2.5">
            <MangaCover
                title={manga.title}
                coverUrl={manga.coverUrl}
                formatLabel={FORMAT_LABELS[manga.format] ?? manga.format}
                className="transition-transform duration-200 group-hover:-translate-y-[3px]"
            />
            <div className="flex flex-col gap-0.5">
                <span className="text-[13px] sm:text-sm font-medium leading-snug line-clamp-2">{manga.title}</span>
                <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
                    {manga.year}
                    {manga.year && manga.ratingCount > 0 && <span aria-hidden="true">·</span>}
                    {manga.ratingCount > 0 && (
                        <>
                            <Star aria-hidden="true" className="size-3 fill-shu text-shu"/>
                            <span>{formatRating(Number(manga.avgRating))}<span className="sr-only"> de 5</span></span>
                        </>
                    )}
                </span>
            </div>
        </Link>
    );
}
