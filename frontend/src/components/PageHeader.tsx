import type {ReactNode} from "react";

interface Props {
    title: string;
    description?: string;
    /** Ações à direita do título (busca, botões); descem para baixo dele no celular. */
    actions?: ReactNode;
}

/** Cabeçalho das páginas internas: o mácron vermelhão sobre o título, como no ū. */
export function PageHeader({title, description, actions}: Props) {
    return (
        <div className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between md:gap-6">
            <div className="flex flex-col gap-2.5">
                <span aria-hidden="true" className="h-[5px] w-8 -skew-x-15 bg-shu"/>
                <h1 className="m-0 text-[28px] md:text-[34px] leading-[1.05] font-semibold tracking-[-0.03em]">{title}</h1>
                {description && <p className="m-0 text-[15px] text-muted-foreground">{description}</p>}
            </div>
            {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
        </div>
    );
}
