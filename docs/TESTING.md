# Testes

> Estratégia de testes do backend. Decisão completa (por que essa pirâmide, por que
> substituir os scripts bash 1:1 antes de remover): [ADR-38](adr/ADR-38-estrategia-de-testes.md).

## Pirâmide

| Camada | Ferramenta | O que cobre |
|---|---|---|
| **Domínio** | JUnit 5 puro, sem Spring | Invariantes de agregado, Value Objects, domain services (`InactivityPolicy`, `QuotaService`, etc.) — roda em milissegundos |
| **Persistência** | `@DataJpaTest` | Queries Spring Data, constraints do schema |
| **Web** | `@WebMvcTest` | Controllers isolados (validação de request, `@PreAuthorize`, mapeamento de exceção → `ErrorResponse`) |
| **Integração** | `@SpringBootTest` + Testcontainers | Fluxos completos ponta a ponta contra Postgres real; `StorageClient`/`EmailSender` substituídos por fakes (`@TestConfiguration`, ex.: bean `fakeEmailSender()` em `IdentityIntegrationTest`) |
| **Arquitetura** | ArchUnit (`ArchitectureTest`) | Regra de dependência entre contextos e camadas — ver [ARCHITECTURE.md §4](ARCHITECTURE.md#4-archunit-como-guarda-de-arquitetura) |

Rodar tudo: `./mvnw clean test` (dentro de `backend/`) — sobe Testcontainers, então
Docker precisa estar rodando. Estado atual: 289 testes verdes.

## Convenções

- **AAA** (Arrange / Act / Assert) — sem exceções misturando as três fases no mesmo
  bloco.
- **Nomenclatura:** `should[Resultado]_when[Condição]` (ex.:
  `shouldThrowDomainException_whenUserAlreadyActive`).
- Teste de domínio nunca sobe Spring — se um teste precisa de `@SpringBootTest` para
  testar uma regra de negócio, é sinal de que a regra está no lugar errado (deveria
  estar no agregado/domain service).

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
