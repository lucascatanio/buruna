import type {ReactNode} from "react";
import {Wordmark} from "@/components/Logo";
import {WorkPageArt, WorkStripArt} from "@/components/WorkPageArt";

declare const __APP_VERSION__: string;

interface Props {
    title: string;
    description?: string;
    children?: ReactNode;
}

/**
 * Moldura das telas públicas de autenticação: a página de mangá de um lado e o
 * formulário do outro. Abaixo de lg a página dá lugar a uma tira curta acima do formulário,
 * para o botão principal caber na primeira tela do celular.
 */
export function AuthLayout({title, description, children}: Props) {
    return (
        <div className="min-h-screen flex flex-col lg:flex-row bg-background text-foreground">
            <WorkStripArt className="lg:hidden"/>
            <WorkPageArt className="hidden lg:block lg:flex-1"/>

            <main className="flex-1 lg:flex-none flex flex-col gap-6 px-6 pt-6 pb-8 sm:px-12 sm:items-center lg:items-stretch lg:w-[480px] lg:shrink-0 lg:justify-center lg:gap-9 lg:px-16 lg:py-10">
                {/* Campos e botão principal maiores que o padrão do app: área de toque de 48px */}
                <div className="w-full max-w-[400px] flex flex-col gap-6 lg:gap-9 [&_[data-slot=input]]:h-12 [&_[data-slot=input]]:pl-3.5 [&_[data-slot=input]]:text-[15px] [&_[data-slot=button][type=submit]]:h-12 [&_[data-slot=button][type=submit]]:text-[15px]">
                    <Wordmark className="h-6 lg:h-[30px] w-auto self-start"/>
                    <header className="flex flex-col gap-2">
                        <h1 className="m-0 text-[28px] lg:text-[34px] font-semibold tracking-[-0.03em]">{title}</h1>
                        {description && <p className="m-0 text-[15px] text-muted-foreground">{description}</p>}
                    </header>
                    {children}
                </div>

                <span className="mt-auto lg:mt-0 font-mono text-xs text-muted-foreground/60 sm:self-center lg:self-start">v{__APP_VERSION__}</span>
            </main>
        </div>
    );
}
