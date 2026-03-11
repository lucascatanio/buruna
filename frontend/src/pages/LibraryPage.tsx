import {useEffect, useState, useCallback} from "react";
import {useNavigate} from "react-router-dom";
import api from "@/lib/axios";
import {Button} from "@/components/ui/button";
import {Input} from "@/components/ui/input";
import {Badge} from "@/components/ui/badge";
import {Card, CardContent} from "@/components/ui/card";
import {TagSelector} from "@/components/TagSelector";
import {Search, SlidersHorizontal, X, BookOpen} from "lucide-react";

interface MangaCard {
    id: string;
    slug: string;
    title: string;
    coverUrl: string | null;
    format: string;
    statusOrigin: string;
    year: number | null;
    avgRating: number;
    ratingCount: number;
}

interface PageResponse {
    content: MangaCard[];
    totalPages: number;
    totalElements: number;
    number: number;
}

const FORMAT_OPTIONS = [
    {value: "", label: "Todos os formatos"},
    {value: "MANGA", label: "Mangá"},
    {value: "MANHWA", label: "Manhwa"},
    {value: "MANHUA", label: "Manhua"},
    {value: "WEBTOON", label: "Webtoon"},
    {value: "ONE_SHOT", label: "One-shot"},
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
};

// const STATUS_COLORS: Record<string, string> = {
//     ONGOING: "text-green-500",
//     COMPLETED: "text-blue-500",
//     HIATUS: "text-yellow-500",
//     CANCELLED: "text-red-500",
// };

export function LibraryPage() {
    const navigate = useNavigate();
    const [data, setData] = useState<PageResponse | null>(null);
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
            const params = new URLSearchParams();
            params.set("page", String(pageNum));
            params.set("size", "24");
            if (title) params.set("title", title);
            if (format) params.set("format", format);
            if (statusOrigin) params.set("statusOrigin", statusOrigin);
            if (tagIds.length > 0) params.set("tagIds", tagIds.join(","));

            const {data: res} = await api.get(`/mangas?${params}`);
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
        <div className="max-w-7xl mx-auto px-4 md:px-6 py-6 space-y-6">

            <div className="flex gap-2">
                <form onSubmit={handleSearch} className="flex gap-2 flex-1">
                    <div className="relative flex-1">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground"/>
                        <Input
                            className="pl-9"
                            placeholder="Buscar por título…"
                            value={titleInput}
                            onChange={(e) => setTitleInput(e.target.value)}
                        />
                    </div>
                    <Button type="submit" variant="secondary">Buscar</Button>
                </form>
                <Button
                    variant={showFilters ? "secondary" : "outline"}
                    size="icon"
                    onClick={() => setShowFilters((v) => !v)}
                    className="relative"
                >
                    <SlidersHorizontal className="w-4 h-4"/>
                    {hasActiveFilters && (
                        <span className="absolute -top-1 -right-1 w-2 h-2 rounded-full bg-primary"/>
                    )}
                </Button>
            </div>

            {showFilters && (
                <div className="border rounded-lg p-4 space-y-4 bg-card">
                    <div className="flex items-center justify-between">
                        <span className="text-sm font-medium">Filtros</span>
                        {hasActiveFilters && (
                            <Button variant="ghost" size="sm" onClick={clearFilters}>
                                <X className="w-3 h-3 mr-1"/> Limpar
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

            {data && (
                <p className="text-sm text-muted-foreground">
                    {data.totalElements} {data.totalElements === 1 ? "resultado" : "resultados"}
                </p>
            )}

            {loading && (
                <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-4">
                    {Array.from({length: 12}).map((_, i) => (
                        <div key={i} className="space-y-2">
                            <div className="aspect-[2/3] rounded-md bg-muted animate-pulse"/>
                            <div className="h-3 bg-muted rounded animate-pulse w-3/4"/>
                        </div>
                    ))}
                </div>
            )}

            {!loading && data?.content.length === 0 && (
                <Card>
                    <CardContent className="py-16 flex flex-col items-center gap-3 text-muted-foreground">
                        <BookOpen className="w-10 h-10 opacity-30"/>
                        <p className="text-sm">Nenhum mangá encontrado</p>
                        {(title || hasActiveFilters) && (
                            <Button variant="ghost" size="sm" onClick={() => {
                                setTitleInput("");
                                setTitle("");
                                clearFilters();
                            }}>
                                Limpar busca
                            </Button>
                        )}
                    </CardContent>
                </Card>
            )}

            {!loading && data && data.content.length > 0 && (
                <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-4">
                    {data.content.map((manga) => (
                        <MangaCardItem key={manga.id} manga={manga}
                                       onClick={() => navigate(`/biblioteca/${manga.slug}`)}/>
                    ))}
                </div>
            )}

            {/* Pagination */}
            {data && data.totalPages > 1 && (
                <div className="flex justify-center gap-2 pt-4">
                    <Button variant="outline" size="sm"
                            disabled={page === 0}
                            onClick={() => changePage(page - 1)}>
                        Anterior
                    </Button>
                    <span className="text-sm self-center text-muted-foreground">
                        {page + 1} / {data.totalPages}
                    </span>
                    <Button variant="outline" size="sm"
                            disabled={page + 1 >= data.totalPages}
                            onClick={() => changePage(page + 1)}>
                        Próxima
                    </Button>
                </div>
            )}
        </div>
    );
}

function MangaCardItem({manga, onClick}: { manga: MangaCard; onClick: () => void }) {
    return (
        <button
            className="group text-left space-y-2 focus:outline-none"
            onClick={onClick}
        >
            <div className="relative aspect-[2/3] rounded-md overflow-hidden bg-muted border">
                {manga.coverUrl ? (
                    <img
                        src={manga.coverUrl}
                        alt={manga.title}
                        className="w-full h-full object-cover transition-transform duration-300 group-hover:scale-105"
                        loading="lazy"
                    />
                ) : (
                    <div className="w-full h-full flex items-center justify-center">
                        <BookOpen className="w-8 h-8 text-muted-foreground/30"/>
                    </div>
                )}
                <div className="absolute bottom-0 left-0 right-0 px-1.5 py-1">
                    <Badge variant="secondary" className="text-[10px] px-1 py-0 opacity-90">
                        {FORMAT_LABELS[manga.format] ?? manga.format}
                    </Badge>
                </div>
            </div>
            <div>
                <p className="text-xs font-medium leading-snug line-clamp-2 group-hover:text-primary transition-colors">
                    {manga.title}
                </p>
                {manga.year && (
                    <p className="text-[11px] text-muted-foreground mt-0.5">{manga.year}</p>
                )}
            </div>
        </button>
    );
}
