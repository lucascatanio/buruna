import type {ReactNode} from "react";
import {LogoMark} from "@/components/Logo";

interface Props {
    title: string;
    description?: string;
    /** Ação para sair do vazio (link ou botão). */
    action?: ReactNode;
}

/** Estado vazio: um quadro com retícula e o ū apagado, no lugar de um ícone genérico. */
export function EmptyState({title, description, action}: Props) {
    return (
        <div className="relative flex flex-col items-center gap-3.5 overflow-hidden rounded-lg border bg-card px-6 pt-11 pb-9 text-center">
            <span aria-hidden="true" className="screentone absolute inset-0 mask-[linear-gradient(to_bottom,#000,transparent_70%)]"/>
            <LogoMark className="relative h-12 w-auto text-muted-foreground/40" decorative/>
            <div className="relative flex flex-col gap-1.5">
                <p className="m-0 text-base font-semibold">{title}</p>
                {description && <p className="m-0 text-sm text-muted-foreground">{description}</p>}
            </div>
            {action && <div className="relative">{action}</div>}
        </div>
    );
}
