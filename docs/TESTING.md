# Testes

> Estratégia de testes do backend. Decisão completa (por que essa pirâmide, por que
> substituir os scripts bash 1:1 antes de remover): [ADR-38](adr/ADR-38-estrategia-de-testes.md).

## Pirâmide

| Camada | Ferramenta | O que cobre |
|---|---|---|
| **Domínio** | JUnit 5 puro, sem Spring | Invariantes de agregado, Value Objects, domain services (`InactivityPolicy`, `QuotaService`, etc.) — roda em milissegundos |
| **Persistência** | `@DataJpaTest` | Queries Spring Data, constraints do schema |
| **Web** | `@WebMvcTest` | Controllers isolados (validação de request, `@PreAuthorize`, mapeamento de exceção → `ErrorResponse`) |
| **Integração** | `@SpringBootTest` + Testcontainers | Fluxos completos ponta a ponta contra Postgres real; dependências externas substituídas por `@MockitoBean` (`StorageClient`, `EmailService`, `ExternalSource`) ou por fakes em `@TestConfiguration` (ex.: `fakeEmailSender()` em `IdentityIntegrationTest`) |
| **Arquitetura** | ArchUnit (`ArchitectureTest`) | Regra de dependência entre contextos e camadas — ver [ARCHITECTURE.md §4](ARCHITECTURE.md#4-archunit-como-guarda-de-arquitetura) |

Rodar tudo: `./mvnw clean test` (dentro de `backend/`) — sobe Testcontainers, então
Docker precisa estar rodando.

## Convenções

- **AAA** (Arrange / Act / Assert) — sem exceções misturando as três fases no mesmo
  bloco.
- **Nomenclatura:** `should[Resultado]_when[Condição]` (ex.:
  `shouldThrowDomainException_whenUserAlreadyActive`).
- Teste de domínio nunca sobe Spring — se um teste precisa de `@SpringBootTest` para
  testar uma regra de negócio, é sinal de que a regra está no lugar errado (deveria
  estar no agregado/domain service).

## Padrões dos testes de capítulo, Job e import

- **Profiles `local` + `test`:** o que em produção roda no Cloud Run Job roda na própria
  requisição, depois do commit (`InlineChapterIngestTrigger`, `InlineSourcingTrigger`). Por
  isso o teste faz o `POST` e já confere o resultado final, sem esperar nada.
- **Fonte externa mockada:** `@MockitoBean ExternalSource`; a listagem pode vir de uma lista
  mutável do teste, para simular capítulo novo ou removido entre um sync e outro
  (`SourcingSyncIntegrationTest`).
- **Relógio do teste:** um `Clock` mutável num `@TestConfiguration` com `@Primary`, para datas
  de aviso e o e-mail diário serem previsíveis.
- **Lease:** `app.sourcing.lease-wait=PT0S` no `application-test.yml`, para uma rodada que
  encontra o lease ocupado desistir na hora.
- **Fixtures binárias** (RAR4/RAR5 da suíte do libarchive) ficam em
  `backend/src/test/resources/media/`, com a origem no `README.md` de lá. CBZ, PDF e EPUB
  são gerados no próprio teste.
- **Concorrência:** `RegisterChapterIntegrationTest` corre duas transações reais em paralelo
  e falha se o lock na obra sair (ADR-53).

## Rede de segurança antes de refatorar

Ao migrar um contexto legado para Clean Architecture, a ordem é sempre: **portar a
cobertura equivalente aos scripts bash de smoke test existentes primeiro** (como
testes automatizados no novo formato), só então refatorar, e só então remover o script
bash. Nunca refatorar sem rede de segurança automatizada cobrindo o comportamento atual.
Isso vale para qualquer refatoração futura de um contexto ainda não coberto — não só
para os epics já concluídos.

## Frontend

`npm run build` (dentro de `frontend/`) — typecheck (`tsc -b`) + build (`vite build`).
Sem suíte de testes automatizados de UI no momento.

Mudança em leitor, upload ou tela nova é testada no navegador antes do PR, de preferência
com o CSP de produção: um `vite preview` com o header `Content-Security-Policy` copiado do
`nginx.conf` (num config temporário, fora do commit) e o backend local. O `vite dev` não
aplica o CSP, e foi assim que o isolamento do EPUB (ADR-51) foi verificado.
