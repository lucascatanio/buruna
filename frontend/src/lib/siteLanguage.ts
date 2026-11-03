/** Idiomas do seletor no import de site, os mais comuns primeiro (ADR-54). */
export const SITE_LANGUAGES = [
    "pt-BR", "en", "es-419", "es", "ja", "ko", "zh", "zh-Hant", "fr", "de", "it", "ru", "id", "vi", "th", "tr", "ar", "pl",
];

// como os sites costumam escrever; "es-la" é a América Latina, não o Laos que a tag diria
const ALIASES: Record<string, string> = {
    "es-la": "es-419",
    "es_la": "es-419",
    "es-mx": "es-419",
    "pt-br": "pt-BR",
    "pt_br": "pt-BR",
    "br": "pt-BR",
    english: "en",
    spanish: "es",
    japanese: "ja",
    korean: "ko",
    chinese: "zh",
    french: "fr",
    german: "de",
    italian: "it",
    russian: "ru",
    indonesian: "id",
    vietnamese: "vi",
    thai: "th",
    turkish: "tr",
    arabic: "ar",
    polish: "pl",
};

/**
 * Palpite do idioma do Burūna (tag BCP 47) para o código que o site usa. Vazio quando o site não
 * informa idioma ou o código não é reconhecido: aí o ADM escolhe.
 */
export function guessLanguage(siteCode: string): string {
    const code = siteCode.trim().toLowerCase();
    if (!code) return "";
    if (ALIASES[code]) return ALIASES[code];
    try {
        return Intl.getCanonicalLocales(code.replace("_", "-"))[0] ?? "";
    } catch {
        return "";
    }
}

/**
 * Id de grupo como o backend o monta a partir do nome: só {@code %} e {@code ,} escapados, porque
 * os ids são guardados juntos por vírgula. Usado quando o ADM digita o grupo sem a listagem.
 */
export function siteGroupId(name: string): string {
    return name.replaceAll("%", "%25").replaceAll(",", "%2C");
}

/** O host da URL da série, para nomear o site ("mangadex.org"). */
export function siteHost(url: string): string {
    try {
        return new URL(url).host.replace(/^www\./, "");
    } catch {
        return url;
    }
}
