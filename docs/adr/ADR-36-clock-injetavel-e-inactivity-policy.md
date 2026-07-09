# ADR-36 — `Clock` injetável e `InactivityPolicy` no domínio

**Status:** Aceita (Fase 2 da refatoração)
**Contexto:** `OffsetDateTime.now()` está espalhado pelo código (tokens, URLs assinadas, inatividade), tornando lógica dependente de tempo difícil de testar de forma determinística. O `InactivityJob` concentra política (75/90 dias) **e** orquestração, e tem dois bugs latentes: **B1** — `@Transactional` ineficaz por self-invocation (`this.deactivateUser`); **B2** — paginação por offset sobre conjunto que muda no loop (usuários podem ser pulados).

**Decisão:**
1. Injetar `java.time.Clock` como bean e usar `OffsetDateTime.now(clock)` na lógica de domínio/aplicação sensível a tempo (inatividade, expiração de token/URL). Em teste, injeta-se um `Clock.fixed(...)`.
2. Extrair **`InactivityPolicy`** como domain service **puro**: `decide(lastAccessAt, now) → NONE | WARN | DEACTIVATE`. Testável sem Spring.
3. Refatorar o job em um caso de uso de aplicação que: usa a política, aplica `@Transactional` **por usuário** num bean separado (proxy AOP válido — corrige B1) e itera de forma **estável** sobre o conjunto (ex.: selecionar só usuários já além do limiar, ou processar por id, sem reconsultar um conjunto que muda sob offset — corrige B2). A deleção de arquivos no GCS permanece **fora** da transação.

**Por quê:** `Clock` é a forma convencional e barata de tornar tempo testável (sem libs). Separar política (domínio puro) de orquestração (aplicação) é o que permite cobrir os limiares 75/90 com teste unitário **antes** de tocar o fluxo — e essa rede de segurança é o pré-requisito para corrigir B1/B2 com confiança (Fase 3).

**Tradeoff:** `Clock` adiciona um parâmetro/bean a propagar. É um custo pequeno e idiomático. Corrigir B1/B2 muda o comportamento de paginação do job; por isso entra **com teste de integração antes**, não como mudança às cegas em produção.
