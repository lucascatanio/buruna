import type {ChapterListItem} from "@/types/chapter";

type Labeled = Pick<ChapterListItem, "number" | "label">;

/** "Cap. 10.5" quando o capítulo tem número; o rótulo ("Extra") quando não tem. */
export function chapterName(chapter: Labeled): string {
    if (chapter.number != null) return `Cap. ${chapter.number}`;
    return chapter.label ?? "Capítulo";
}

const LANGUAGE_NAMES = new Intl.DisplayNames(["pt-BR"], {type: "language"});

/** Nome do idioma em português ("Português (Brasil)"), sem bandeira: pt-BR × pt-PT seriam ambíguos. */
export function languageName(tag: string): string {
    try {
        const name = LANGUAGE_NAMES.of(tag);
        return name ? name.charAt(0).toUpperCase() + name.slice(1) : tag;
    } catch {
        return tag;
    }
}

/** Formatos que leem melhor em rolagem vertical; o resto abre página a página. */
const SCROLL_FORMATS = new Set(["MANHWA", "WEBTOON"]);

export function defaultReadMode(format: string | null | undefined): "paged" | "scroll" {
    return format && SCROLL_FORMATS.has(format) ? "scroll" : "paged";
}
