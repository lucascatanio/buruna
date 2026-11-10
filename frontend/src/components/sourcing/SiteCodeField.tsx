import {Input} from "@/components/ui/input";
import {Label} from "@/components/ui/label";

interface SiteCodeFieldProps {
    id: string;
    code: string;
    /** O ADM confirmou que o site não informa idioma: o código vai vazio. */
    noLanguage: boolean;
    onChange: (code: string, noLanguage: boolean) => void;
}

/**
 * O código do idioma no site, digitado à mão (quando a listagem não roda aqui). Vazio só com a
 * confirmação de que o site não informa idioma: esquecer o campo listaria o idioma padrão do site
 * e não traria nenhum capítulo (#142).
 */
export function SiteCodeField({id, code, noLanguage, onChange}: SiteCodeFieldProps) {
    return (
        <div className="space-y-1.5">
            <Label htmlFor={id}>Código do idioma no site</Label>
            <Input id={id} value={noLanguage ? "" : code} maxLength={35} disabled={noLanguage}
                   placeholder="pt-br, es-la, en…" onChange={(e) => onChange(e.target.value, false)}/>
            <label className="flex min-h-9 cursor-pointer items-center gap-2 text-sm">
                <input type="checkbox" checked={noLanguage} onChange={(e) => onChange("", e.target.checked)}/>
                Este site não informa idioma
            </label>
            <p className="text-xs text-muted-foreground">
                Use o código do filtro de idioma do site (no mangafire, pt-br para português do Brasil).
            </p>
        </div>
    );
}
