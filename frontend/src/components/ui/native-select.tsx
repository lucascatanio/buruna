import * as React from "react"

import {cn} from "@/lib/utils"

/** `<select>` nativo com o mesmo visual do Input (altura, borda, foco). */
function NativeSelect({className, ...props}: React.ComponentProps<"select">) {
    return (
        <select
            data-slot="native-select"
            className={cn(
                "h-10 w-full min-w-0 rounded-lg border border-input bg-input/30 px-3 text-sm text-foreground outline-none transition-colors focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:cursor-not-allowed disabled:opacity-50",
                className
            )}
            {...props}
        />
    )
}

export {NativeSelect}
