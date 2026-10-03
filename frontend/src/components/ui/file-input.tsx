import * as React from "react"
import {Upload} from "lucide-react"

import {cn} from "@/lib/utils"

type Props = Omit<React.ComponentProps<"input">, "type"> & {
    /** Texto do botão; o padrão serve para qualquer arquivo. */
    buttonLabel?: string
}

/**
 * Campo de arquivo em português e no visual do Input. O input nativo continua no
 * DOM (sr-only): teclado, `htmlFor` e `required` funcionam como num input comum.
 */
function FileInput({className, onChange, buttonLabel = "Escolher arquivo", ...props}: Props) {
    const [fileName, setFileName] = React.useState<string | null>(null)

    return (
        <label
            data-slot="file-input"
            className={cn(
                "flex h-10 w-full min-w-0 cursor-pointer items-center gap-3 rounded-lg border border-input bg-input/30 pr-3 pl-1 text-sm transition-colors focus-within:border-ring focus-within:ring-3 focus-within:ring-ring/50 hover:border-foreground/30",
                className
            )}
        >
            <input
                type="file"
                className="sr-only"
                {...props}
                onChange={(e) => {
                    setFileName(e.target.files?.[0]?.name ?? null)
                    onChange?.(e)
                }}
            />
            <span className="inline-flex h-8 shrink-0 items-center gap-1.5 rounded-sm bg-secondary px-3 font-medium text-secondary-foreground">
                <Upload className="size-4"/>
                {buttonLabel}
            </span>
            <span className={cn("truncate", fileName ? "text-foreground" : "text-muted-foreground")}>
                {fileName ?? "Nenhum arquivo escolhido"}
            </span>
        </label>
    )
}

export {FileInput}
