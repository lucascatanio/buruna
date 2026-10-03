import {cn} from "@/lib/utils";

/**
 * O mácron do ū: traço vermelhão inclinado a 15°. Marca o que está ativo ou em
 * destaque (item de navegação, título de página, status de leitura). Tamanho e
 * posição vêm do className.
 */
export function Macron({className}: {className?: string}) {
    return <span aria-hidden="true" className={cn("block h-1 w-3 shrink-0 -skew-x-15 bg-shu", className)}/>;
}
