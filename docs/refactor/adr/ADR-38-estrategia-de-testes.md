# ADR-38 — Estratégia de testes (pirâmide) e substituição dos scripts bash

**Status:** Aceita (Fase 2 da refatoração)
**Contexto:** Não há **nenhum** teste automatizado em `src/test` (apesar de `spring-boot-starter-test` e `spring-security-test` já no `pom.xml`). A "suíte" são 7 scripts bash (~2.628 linhas) que batem num backend vivo e checam estado via `psql` — e2e manuais, não determinísticos, fora do CI, que não isolam regra de negócio. Um dos objetivos centrais da migração é viabilizar teste de verdade, e contribuidores precisam de uma forma de validar mudanças antes do PR.

**Decisão:** Adotar uma pirâmide de testes com nomenclatura e estilo padronizados:

- **Domínio (base, maioria):** JUnit + AssertJ **puros**, sem Spring nem banco. Cobrem invariantes de agregado, VOs, domain services (`InactivityPolicy`, `QuotaPolicy`). Tempo via `Clock.fixed`.
- **Aplicação (use cases):** Mockito (`@ExtendWith(MockitoExtension.class)`), repositórios/portas mockados. Sem contexto Spring.
- **Repositórios:** `@DataJpaTest` (slice JPA) para queries custom/specifications/projeções.
- **Web:** `@WebMvcTest` para controllers (binding, status, `@PreAuthorize`), service/use case mockado.
- **Integração (topo, poucos):** `@SpringBootTest` + Testcontainers (Postgres) nos **fluxos críticos** (upload 2-fases, leitura assinada, inatividade, refresh rotation, submissão/promote), com `StorageClient`/`EmailSender` mockados/fakes.

**Convenções:** estilo **AAA** (Arrange-Act-Assert); nomes `should[Resultado]_when[Condição]`; um arquivo de teste por classe testada.

**Substituição dos bash:** para cada cenário coberto por um script `test-phaseN.sh`, escreve-se o teste automatizado equivalente **antes** de refatorar o fluxo correspondente (rede de segurança), e só **depois** o script é removido — mapeamento 1:1, sem perder cobertura. Os scripts servem de checklist dos casos a portar.

**Por quê:** A pirâmide dá confiança e velocidade (a base roda em milissegundos sem infra), o que só é possível porque o domínio ficou isolado de framework (ADR-31/32). Padronizar estilo/nomenclatura baixa a barreira para o contribuidor escrever e ler testes. Introduzir teste antes de refatorar protege os fluxos de produção (princípio "produção é sagrada").

**Tradeoff:** Testes de integração com Testcontainers exigem Docker no ambiente de CI/dev e são mais lentos — por isso ficam restritos aos fluxos críticos. A migração 1:1 dos cenários bash é trabalho incremental espalhado pelo roadmap (Fase 3), não um esforço único.
