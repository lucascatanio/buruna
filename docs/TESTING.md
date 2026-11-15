# Testes

> Como testar no Burūna, na prática. As regras (pirâmide, AAA, nomenclatura, rede de
> segurança antes de refatorar) estão na
> [constituição, princípio V](../.specify/memory/constitution.md#v-teste-antes-de-mudar-comportamento).

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

## Frontend

`npm run build` (dentro de `frontend/`) — typecheck (`tsc -b`) + build (`vite build`).
Sem suíte de testes automatizados de UI no momento.

Mudança em leitor, upload ou tela nova é testada no navegador antes do PR, de preferência
com o CSP de produção: um `vite preview` com o header `Content-Security-Policy` copiado do
`nginx.conf` (num config temporário, fora do commit) e o backend local. O `vite dev` não
aplica o CSP, e foi assim que o isolamento do EPUB (ADR-51) foi verificado.
