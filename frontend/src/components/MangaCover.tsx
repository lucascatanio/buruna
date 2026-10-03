import {Badge} from "@/components/ui/badge";
import {cn} from "@/lib/utils";

interface Props {
    title: string;
    coverUrl: string | null;
    /** Rótulo do formato, mostrado como selo no pé da capa. */
    formatLabel?: string;
    /** Texto alternativo da imagem; vazio quando o título já aparece ao lado. */
    alt?: string;
    className?: string;
}

/**
 * Capa de mangá com cara de volume: sombra de lombada à esquerda e selo de formato.
 * Sem imagem, vira retícula com o título — no lugar do ícone genérico de livro.
 */
export function MangaCover({title, coverUrl, formatLabel, alt = "", className}: Props) {
    return (
        <div className={cn("relative aspect-[2/3] overflow-hidden rounded-lg border bg-card shadow-[0_12px_24px_rgba(0,0,0,.35)]", className)}>
            {coverUrl ? (
                <img src={coverUrl} alt={alt} loading="lazy" className="absolute inset-0 size-full object-cover"/>
            ) : (
                <div aria-hidden="true" className="screentone absolute inset-0 bg-ink">
                    <span className="absolute inset-x-2.5 top-2.5 sm:inset-x-3.5 sm:top-3 line-clamp-4 text-[13px] sm:text-[17px] leading-[1.05] font-bold tracking-[-0.02em] text-paper">
                        {title}
                    </span>
                </div>
            )}
            <span aria-hidden="true" className="absolute inset-y-0 left-0 w-[7px] bg-gradient-to-r from-black/45 to-transparent"/>
            {formatLabel && (
                <Badge variant="stamp" className="absolute bottom-2.5 left-2.5">{formatLabel}</Badge>
            )}
        </div>
    );
}
