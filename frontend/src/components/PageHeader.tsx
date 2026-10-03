import type {ReactNode} from "react";
import {Link} from "react-router-dom";
import {ChevronLeft} from "lucide-react";
import {Macron} from "@/components/Macron";

interface Props {
    title: string;
    description?: string;
    /** Ações à direita do título (busca, botões); descem para baixo dele no celular. */
    actions?: ReactNode;
    /** Link de volta, para telas um nível abaixo (formulários, detalhes). */
    back?: {to: string; label: string};
}

/** Cabeçalho das páginas internas: o mácron vermelhão sobre o título, como no ū. */
export function PageHeader({title, description, actions, back}: Props) {
    return (
        <div className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between md:gap-6">
            <div className="flex min-w-0 flex-col gap-2.5">
                {back && (
                    <Link to={back.to} className="-mt-1 mb-1 inline-flex items-center gap-1.5 self-start text-[13px] text-muted-foreground hover:text-foreground">
                        <ChevronLeft className="size-3.5"/>
                        {back.label}
                    </Link>
                )}
                <Macron className="h-[5px] w-8"/>
                <h1 className="m-0 text-[28px] md:text-[34px] leading-[1.05] font-semibold tracking-[-0.03em]">{title}</h1>
                {description && <p className="m-0 text-[15px] text-muted-foreground">{description}</p>}
            </div>
            {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
        </div>
    );
}
