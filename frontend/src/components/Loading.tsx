import {LogoMark} from "@/components/Logo";
import {cn} from "@/lib/utils";

/** Indicador de carregamento da marca: o ū pulsando. */
export function Loading({className, label = "Carregando"}: {className?: string; label?: string}) {
    return (
        <div role="status" className={cn("flex justify-center py-10", className)}>
            <LogoMark className="h-10 w-auto animate-pulse" label={label}/>
        </div>
    );
}
