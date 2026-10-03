import type {ReactNode} from "react";
import {Wordmark} from "@/components/Logo";

/** Moldura das telas públicas de autenticação: logotipo acima do conteúdo, centralizado. */
export function AuthLayout({children}: {children: ReactNode}) {
    return (
        <div className="min-h-screen flex flex-col items-center justify-center bg-background px-4 gap-6">
            <Wordmark className="h-8 w-auto"/>
            {children}
        </div>
    );
}
