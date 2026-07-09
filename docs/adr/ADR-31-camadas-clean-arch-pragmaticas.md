# ADR-31 — Camadas Clean Architecture pragmáticas por bounded context

**Status:** Aceita (Fase 2 da refatoração)
**Contexto:** O backend é um monolito modular com camadas de fato `controller → service (gordo) → repository → entidade JPA`. A regra de negócio vive nos services, o "domínio" é o modelo de persistência, e não há fronteira explícita de dependência. Isso impede teste de regra sem subir Spring e dificulta a navegação para contribuidores externos (objetivo de primeira classe do projeto).

**Decisão:** Adotar Clean Architecture **por bounded context**, com quatro camadas e regra de dependência apontando só para dentro:

- `domain/` — entidades ricas, value objects, enums, exceções de domínio, domain services puros. **Não depende de nada** (nem Spring, nem `HttpStatus`, nem de outro contexto).
- `application/` — casos de uso (orquestração), fronteira transacional, portas que o caso de uso consome. Depende de `domain` + portas.
- `persistence/` — repositórios Spring Data, specifications, projeções. Depende de `domain`.
- `web/` — controllers + DTOs HTTP + `@PreAuthorize`. Depende de `application`.

Comunicação entre contextos **só** via caso de uso público (`application`) do outro contexto — nunca importando seu `domain`/`persistence`. No frontend, **não** se aplica Clean Arch: apenas formaliza-se uma camada `api/` tipada e `types/` (arquitetura leve).

**Por quê:** A separação domínio↔framework é o que destrava teste unitário puro. As quatro camadas são um vocabulário convencional e previsível — o contribuidor abre qualquer contexto e reconhece onde fica o quê, mantendo a regularidade que o `controller/service/domain/repository` atual já oferecia, agora com fronteiras explícitas. Aplicar a mesma arquitetura no front não se paga e elevaria a barreira de entrada.

**Tradeoff:** Mais pacotes por contexto e a disciplina de não "furar" camadas (revisável em PR). Em contextos quase-CRUD (engagement, reading) isso pode parecer cerimônia — mitigado permitindo um único application service nesses casos (ver proporcionalidade, ADR-34/02-arquitetura-alvo §8).
