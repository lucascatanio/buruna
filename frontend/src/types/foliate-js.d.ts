// Tipos mínimos do foliate-js (o pacote é JavaScript puro), só do que o leitor de EPUB usa.
declare module "foliate-js/view.js" {
    export interface FoliateTocItem {
        label: string;
        href: string;
        subitems?: FoliateTocItem[];
    }

    export interface FoliateBook {
        toc?: FoliateTocItem[];
        /** Recebe cada recurso antes de virar blob: URL; dá para trocar o conteúdo. */
        transformTarget?: EventTarget;
        destroy?: () => void;
    }

    export interface FoliateLocation {
        /** Andamento no livro inteiro, de 0 a 1. */
        fraction: number;
        cfi: string;
        tocItem?: { label?: string } | null;
    }

    export interface FoliateRenderer extends HTMLElement {
        setStyles(css: string): void;
    }

    export interface FoliateView extends HTMLElement {
        book: FoliateBook;
        renderer: FoliateRenderer;
        open(book: FoliateBook): Promise<void>;
        init(options: { lastLocation?: string | null; showTextStart?: boolean }): Promise<void>;
        close(): void;
        goTo(target: string): Promise<unknown>;
        goLeft(): Promise<void>;
        goRight(): Promise<void>;
    }

    export function makeBook(file: File): Promise<FoliateBook>;
}
