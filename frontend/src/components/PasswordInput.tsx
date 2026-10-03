import {useState, type ComponentProps} from "react";
import {Eye, EyeOff} from "lucide-react";
import {Input} from "@/components/ui/input";
import {cn} from "@/lib/utils";

/** Campo de senha com botão para mostrar/ocultar o que foi digitado. */
export function PasswordInput({className, ...props}: Omit<ComponentProps<"input">, "type">) {
    const [visible, setVisible] = useState(false);

    return (
        <div className="relative">
            <Input type={visible ? "text" : "password"} className={cn("pr-12", className)} {...props}/>
            <button
                type="button"
                onClick={() => setVisible((v) => !v)}
                aria-label={visible ? "Ocultar senha" : "Mostrar senha"}
                aria-pressed={visible}
                className="absolute right-0.5 top-1/2 -translate-y-1/2 size-11 flex items-center justify-center rounded-md text-muted-foreground hover:text-foreground focus-visible:outline-2 focus-visible:outline-ring"
            >
                {visible ? <EyeOff className="size-5"/> : <Eye className="size-5"/>}
            </button>
        </div>
    );
}
