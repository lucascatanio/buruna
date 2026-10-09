import type {CSSProperties} from "react";

/*
 * Arte das telas de autenticação: uma página de mangá desenhada só com CSS.
 * Quatro quadros, calha inclinada, retícula e linhas de velocidade. A numeração
 * segue a leitura do mangá, da direita para a esquerda (1 → 4).
 *
 * Cada quadro tem duas camadas com o mesmo polígono: a de fora é o traço (tinta),
 * a de dentro recua BORDER px e leva o conteúdo.
 */
const BORDER = "3px";

// Vértice (x, y) deslocado BORDER px para dentro do quadro, na direção (dx, dy).
const inner = (x: string, y: string, dx: 1 | -1, dy: 1 | -1) =>
    `calc(${x} ${dx > 0 ? "+" : "-"} ${BORDER}) calc(${y} ${dy > 0 ? "+" : "-"} ${BORDER})`;

const fill: CSSProperties = {position: "absolute", inset: 0};

// Contornos de "ペラッ" (onomatopeia de virar a página), extraídos da Noto Sans CJK JP
// Black (licença OFL) para não carregar uma fonte japonesa só por três caracteres.
const PERA_PATH =
    "M733 541C733 509 759 483 791 483C823 483 849 509 849 541C849 573 823 599 791 599C759 599 733 573 733 541ZM657 541C657 616 716 675 791 675C866 675 926 616 926 541C926 466 866 406 791 406C716 406 657 466 657 541ZM25 863 171 1014C190 985 215 947 239 912C277 858 340 766 375 720C400 687 422 686 451 715C484 749 570 845 629 916C685 983 767 1087 832 1168L966 1024C889 942 785 831 718 759C657 693 586 619 515 552C432 474 366 485 303 560C231 646 158 737 114 781C81 814 57 837 25 863Z M1219 380V536C1249 533 1297 532 1331 532C1393 532 1653 532 1708 532C1746 532 1800 534 1828 536V380C1799 384 1742 386 1710 386C1653 386 1399 386 1331 386C1296 386 1247 384 1219 380ZM1919 686 1812 619C1796 626 1766 631 1728 631C1650 631 1331 631 1252 631C1218 631 1171 628 1125 624V780C1170 776 1227 775 1252 775C1358 775 1656 775 1707 775C1690 821 1663 871 1614 920C1542 992 1426 1058 1270 1091L1391 1228C1519 1191 1649 1118 1748 1007C1822 924 1864 830 1897 734C1901 722 1911 701 1919 686Z M2517 556 2373 603C2400 659 2442 777 2456 827L2601 777C2586 730 2537 600 2517 556ZM2890 638 2719 583C2710 707 2663 843 2597 926C2514 1031 2368 1107 2262 1134L2389 1264C2509 1218 2635 1131 2728 1009C2794 923 2836 821 2862 725C2869 701 2876 677 2890 638ZM2285 610 2139 662C2166 710 2214 841 2231 895L2379 840C2359 782 2313 666 2285 610Z";

const MARK_U = "M48,90 H92 V134 A36,36 0 0 0 164,134 V90 H208 V214 H164 V205.4 A80,80 0 0 1 48,134 Z";
const MARK_MACRON = "M56.57,42 H208 L199.43,74 H48 Z";

export function WorkPageArt({className}: {className?: string}) {
    return (
        <div className={`relative overflow-hidden bg-[#1A1A1A] ${className ?? ""}`} aria-hidden="true">
            <div className="absolute inset-[clamp(16px,3vw,40px)] bg-paper shadow-[0_30px_80px_rgba(0,0,0,.55)]">
                <div className="absolute inset-[clamp(12px,1.6vw,22px)] text-ink">

                    {/* Quadro 1 (topo, direita): linhas de velocidade e o ū */}
                    <div className="bg-ink" style={{...fill, clipPath: "polygon(52% 0, 100% 0, 100% 44%, 52% 49.76%)"}}/>
                    <div
                        style={{
                            ...fill,
                            background:
                                "radial-gradient(circle at 76% 23%, var(--paper) 0 15%, transparent 34%)," +
                                "repeating-conic-gradient(from 0deg at 76% 23%, var(--ink) 0deg 1.4deg, var(--paper) 1.4deg 5deg)",
                            clipPath: `polygon(${inner("52%", "0px", 1, 1)}, ${inner("100%", "0px", -1, 1)}, ${inner("100%", "44%", -1, -1)}, ${inner("52%", "49.76%", 1, -1)})`,
                        }}
                    />
                    <div className="absolute left-[52%] right-0 top-0 h-[46%] flex items-center justify-center">
                        <svg viewBox="48 42 160 172" className="w-[clamp(72px,9vw,150px)] h-auto">
                            <path className="fill-ink" d={MARK_U}/>
                            <path className="fill-shu" d={MARK_MACRON}/>
                        </svg>
                    </div>

                    {/* Quadro 2 (topo, esquerda): retícula e a onomatopeia */}
                    <div className="bg-ink" style={{...fill, clipPath: "polygon(0 0, 49% 0, 49% 50.12%, 0 56%)"}}/>
                    <div
                        className="bg-paper bg-[radial-gradient(var(--ink)_34%,transparent_37%)] bg-size-[9px_9px] mask-[linear-gradient(200deg,#000_0%,rgba(0,0,0,.15)_70%)]"
                        style={{
                            ...fill,
                            clipPath: `polygon(${inner("0px", "0px", 1, 1)}, ${inner("49%", "0px", -1, 1)}, ${inner("49%", "50.12%", -1, -1)}, ${inner("0px", "56%", 1, -1)})`,
                        }}
                    />
                    <div className="absolute left-0 top-0 w-[49%] h-[50%] flex items-center justify-center">
                        <svg viewBox="0 340 3000 960" className="w-[clamp(120px,17vw,280px)] h-auto -rotate-12">
                            <path d={PERA_PATH} className="fill-ink stroke-paper" strokeWidth={70} style={{paintOrder: "stroke"}}/>
                        </svg>
                    </div>

                    {/* Quadro 3 (embaixo, direita): narração */}
                    <div className="bg-ink" style={{...fill, clipPath: "polygon(66% 51.08%, 100% 47%, 100% 100%, 66% 100%)"}}/>
                    <div
                        className="bg-paper"
                        style={{
                            ...fill,
                            clipPath: `polygon(${inner("66%", "51.08%", 1, 1)}, ${inner("100%", "47%", -1, 1)}, ${inner("100%", "100%", -1, -1)}, ${inner("66%", "100%", 1, -1)})`,
                        }}
                    />
                    <div className="absolute left-[calc(66%+clamp(14px,1.6vw,24px))] right-[clamp(14px,1.6vw,24px)] bottom-[clamp(14px,2vw,28px)] flex flex-col gap-2.5">
                        <div className="w-10 h-1.5 bg-shu -skew-x-15"/>
                        <p className="hidden md:block m-0 text-[clamp(13px,1.1vw,16px)] leading-snug font-medium">
                            Sua biblioteca pessoal de mangás: volumes, progresso de leitura e a lista do que vem a seguir.
                        </p>
                    </div>

                    {/* Quadro 4 (embaixo, esquerda): chamada */}
                    <div className="bg-ink" style={{...fill, clipPath: "polygon(0 59%, 63% 51.44%, 63% 100%, 0 100%)"}}/>
                    <div
                        className="bg-[radial-gradient(#3A3A3A_30%,transparent_33%)] bg-size-[7px_7px] mask-[linear-gradient(to_bottom,#000_10%,transparent_75%)]"
                        style={{...fill, clipPath: "polygon(0 59%, 63% 51.44%, 63% 100%, 0 100%)"}}
                    />
                    <p className="absolute left-[clamp(16px,2.4vw,36px)] w-[calc(63%-clamp(32px,4.8vw,72px))] bottom-[clamp(16px,2.4vw,36px)] m-0 text-paper text-[clamp(30px,4.4vw,68px)] leading-[0.98] font-bold tracking-[-0.035em]">
                        Leia de onde<br/>parou.
                    </p>

                    {/* Números dos quadros, na ordem de leitura do mangá */}
                    <span className="absolute right-3 top-2.5 font-mono text-[11px] bg-paper px-1.5 py-0.5">1</span>
                    <span className="absolute left-[calc(49%-30px)] top-2.5 font-mono text-[11px] bg-paper px-1.5 py-0.5">2</span>
                    <span className="absolute right-3 top-[calc(47%+14px)] font-mono text-[11px]">3</span>
                    <span className="absolute left-3 top-[calc(59%+10px)] font-mono text-[11px] text-[#8A857C]">4</span>
                </div>
            </div>
        </div>
    );
}

/*
 * Versão para telas estreitas: uma tira de dois quadros com a calha a 15°. A altura
 * acompanha a largura (aspect ratio), então a inclinação --slant (fração da largura
 * que corresponde a 15° na altura da tira) é fixa por breakpoint.
 */
const SPLIT = "58%";
const gutterTop = `calc(${SPLIT} + var(--slant) / 2)`;
const gutterBottom = `calc(${SPLIT} - var(--slant) / 2)`;
const GUTTER = "10px";

export function WorkStripArt({className}: {className?: string}) {
    return (
        <div
            className={`relative bg-paper aspect-[2/1] sm:aspect-[3/1] [--slant:12.6%] sm:[--slant:8.3%] ${className ?? ""}`}
            aria-hidden="true"
        >
            <div className="absolute inset-2.5 sm:inset-3.5 text-ink">

                {/* Quadro 1 (direita): linhas de velocidade e o ū */}
                <div className="bg-ink" style={{...fill, clipPath: `polygon(calc(${gutterTop} + ${GUTTER}) 0, 100% 0, 100% 100%, calc(${gutterBottom} + ${GUTTER}) 100%)`}}/>
                <div
                    style={{
                        ...fill,
                        background:
                            "radial-gradient(circle at 80% 50%, var(--paper) 0 13%, transparent 30%)," +
                            "repeating-conic-gradient(from 0deg at 80% 50%, var(--ink) 0deg 1.4deg, var(--paper) 1.4deg 5deg)",
                        clipPath: `polygon(${inner(`calc(${gutterTop} + ${GUTTER})`, "0px", 1, 1)}, ${inner("100%", "0px", -1, 1)}, ${inner("100%", "100%", -1, -1)}, ${inner(`calc(${gutterBottom} + ${GUTTER})`, "100%", 1, -1)})`,
                    }}
                />
                <div className="absolute left-[60%] right-0 inset-y-0 flex items-center justify-center">
                    <svg viewBox="48 42 160 172" className="w-[clamp(56px,15vw,104px)] h-auto">
                        <path className="fill-ink" d={MARK_U}/>
                        <path className="fill-shu" d={MARK_MACRON}/>
                    </svg>
                </div>

                {/* Quadro 2 (esquerda): chamada sobre retícula */}
                <div className="bg-ink" style={{...fill, clipPath: `polygon(0 0, ${gutterTop} 0, ${gutterBottom} 100%, 0 100%)`}}/>
                <div
                    className="bg-[radial-gradient(#3A3A3A_30%,transparent_33%)] bg-size-[7px_7px] mask-[linear-gradient(to_top,#000_5%,transparent_70%)]"
                    style={{...fill, clipPath: `polygon(0 0, ${gutterTop} 0, ${gutterBottom} 100%, 0 100%)`}}
                />
                <p className="absolute left-3.5 top-3 sm:left-5 sm:top-4 m-0 text-paper text-[clamp(22px,6vw,40px)] leading-[0.98] font-bold tracking-[-0.035em]">
                    Leia de onde<br/>parou.
                </p>
            </div>
        </div>
    );
}
