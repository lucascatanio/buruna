# ADR-27 — Canvas do pdfjs escalado por devicePixelRatio + escala mínima

**Contexto:** O pdfjs renderizava o canvas em resolução 1x independente do dispositivo. Em celulares com tela de alta densidade (DPR 2x ou 3x), isso deixava texto e imagens visivelmente borrados. O modo responsivo do DevTools no desktop não reproduzia o problema — só aparecia em celulares reais.

**Decisão:** Multiplicar a escala de renderização pelo `devicePixelRatio` do dispositivo (limitado a 3) e forçar um piso de 1.5 na escala base. O truque é usar escalas diferentes para o canvas: `canvas.width/height` recebe a escala alta (mais pixels reais), enquanto `canvas.style.width/height` mantém a escala original (tamanho visual inalterado).

**Justificativa:** Só aplicar o DPR não bastou — em telas estreitas, a escala base calculada era tão baixa que mesmo multiplicada por 3 o resultado ficava pobre. O piso de 1.5 resolve isso garantindo uma resolução mínima de renderização. A separação entre resolução e tamanho visual é o que permite renderizar em alta qualidade sem alterar o layout da página.

**Tradeoff aceito:** O canvas usa mais memória no celular (entre 3x e 4.5x mais pixels). Na prática não causou problemas — se um dia causar em devices com pouca RAM, o cap de 3 no DPR já limita o pior caso.
