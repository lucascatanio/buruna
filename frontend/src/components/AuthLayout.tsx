import type {ReactNode} from "react";
import {Wordmark} from "@/components/Logo";
import {MangaPageArt} from "@/components/MangaPageArt";

declare const __APP_VERSION__: string;

interface Props {
    title: string;
    description?: string;
    children?: ReactNode;
}

/**
 * Moldura das telas públicas de autenticação: a página de mangá de um lado e o
 * formulário do outro. Em telas estreitas a arte vira uma faixa acima do formulário.
 */
export function AuthLayout({title, description, children}: Props) {
    return (
        <div className="min-h-screen flex flex-wrap bg-background text-foreground">
            <MangaPageArt className="flex-[999_1_560px] min-h-[360px]"/>

            <main className="flex-[1_1_480px] flex flex-col justify-center gap-9 px-6 py-10 sm:px-12 lg:px-16">
                <Wordmark className="h-[30px] w-auto self-start"/>

                {/* Campos e botão principal maiores que o padrão do app: área de toque de 48px */}
                <div className="w-full max-w-[400px] flex flex-col gap-9 [&_[data-slot=input]]:h-12 [&_[data-slot=input]]:pl-3.5 [&_[data-slot=input]]:text-[15px] [&_[data-slot=button][type=submit]]:h-12 [&_[data-slot=button][type=submit]]:text-[15px]">
                    <header className="flex flex-col gap-2">
                        <h1 className="m-0 text-[34px] font-semibold tracking-[-0.03em]">{title}</h1>
                        {description && <p className="m-0 text-[15px] text-muted-foreground">{description}</p>}
                    </header>
                    {children}
                </div>

                <span className="font-mono text-xs text-muted-foreground/60">v{__APP_VERSION__}</span>
            </main>
        </div>
    );
}
