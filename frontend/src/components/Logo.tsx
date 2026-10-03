// Letras construídas como formas separadas: sobrepostas de propósito para não abrir frestas.
const LETTERS = [
    "M0,60 H26 V200 H0 Z",
    "M13,100 H51 V126 H13 Z",
    "M13,174 H51 V200 H13 Z",
    "M50,100 A50,50 0 0 1 50,200 V174 A24,24 0 0 0 50,126 Z",
    "M124,100 H150 V151 H124 Z",
    "M198,100 H224 V200 H198 Z",
    "M124,150 A50,50 0 0 0 224,150 H198 A24,24 0 0 1 150,150 Z",
    "M248,100 H274 V200 H248 Z",
    "M297,100 H318 V126 H297 Z",
    "M248,150 A50,50 0 0 1 298,100 V126 A24,24 0 0 0 274,150 Z",
    "M340,100 H366 V151 H340 Z",
    "M414,100 H440 V200 H414 Z",
    "M340,150 A50,50 0 0 0 440,150 H414 A24,24 0 0 1 366,150 Z",
    "M464,100 H490 V200 H464 Z",
    "M538,149 H564 V200 H538 Z",
    "M464,150 A50,50 0 0 1 564,150 H538 A24,24 0 0 0 490,150 Z",
    "M662,100 H688 V200 H662 Z",
    "M637,100 H675 V126 H637 Z",
    "M637,174 H675 V200 H637 Z",
    "M638,100 A50,50 0 0 0 638,200 V174 A24,24 0 0 1 638,126 Z",
];
const WORDMARK_MACRON = "M346.43,60 H440 L433.57,84 H340 Z";

/** Logotipo "burūna". As letras herdam a cor do texto (currentColor); o mácron é sempre shu (vermelhão), via token --shu do index.css. */
export function Wordmark({className}: {className?: string}) {
    return (
        <svg viewBox="0 60 688 140" role="img" aria-label="Burūna" className={className}>
            <g fill="currentColor">
                {LETTERS.map((d) => <path key={d} d={d}/>)}
            </g>
            <path className="fill-shu" d={WORDMARK_MACRON}/>
        </svg>
    );
}


const MARK_U = "M48,90 H92 V134 A36,36 0 0 0 164,134 V90 H208 V214 H164 V205.4 A80,80 0 0 1 48,134 Z";
const MARK_MACRON = "M56.57,42 H208 L199.43,74 H48 Z";

/**
 * Símbolo ū. O u herda a cor do texto (currentColor); o mácron é sempre shu (vermelhão), via token --shu do index.css.
 * Com `decorative`, fica fora da árvore de acessibilidade (quando o texto ao lado já diz tudo).
 */
export function LogoMark({className, label = "Burūna", decorative = false}: {className?: string; label?: string; decorative?: boolean}) {
    const a11y = decorative ? {"aria-hidden": true} : {role: "img", "aria-label": label};
    return (
        <svg viewBox="48 42 160 172" {...a11y} className={className}>
            <path fill="currentColor" d={MARK_U}/>
            <path className="fill-shu" d={MARK_MACRON}/>
        </svg>
    );
}
