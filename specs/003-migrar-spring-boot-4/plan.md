# Implementation Plan: Migrar o backend para o Spring Boot 4

**Branch**: `chore/124-spring-boot-4` | **Date**: 2026-11-21 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/003-migrar-spring-boot-4/spec.md`

## Summary

Trocar o parent do backend de Spring Boot 3.5.16 para 4.1.3 (Framework 7.0.11, Security
7.1.3) para fechar CVE-2026-47884/47890 e os avisos de 2026-11-19, sem mudar nada que o
usuário veja. A abordagem tem três passos. Primeiro, um commit no Boot 3.5 que deixa em
teste o formato JSON de hoje (rede de segurança). Depois, a troca: sem as oito sobrescritas
de versão, com os starters modulares do Boot 4, Jackson 3 com `use-jackson2-defaults` e
Testcontainers 2. Por fim, os docs que citam a versão. Não há migration, mudança de
infraestrutura nem mudança no frontend. Decisões em [research.md](research.md).

## Technical Context

**Language/Version**: Java 21 (sem mudança). Maven 3.9.9 pelo wrapper.

**Primary Dependencies**: Spring Boot 4.1.3 → Spring Framework 7.0.11, Spring Security 7.1.3,
Spring Data 2026.0.3, Hibernate 7.4.15, Jackson 3.1.8 (+ Jackson 2.21.8 só para o
`jjwt-jackson`), Flyway 12.4.0, Tomcat 11.0.27, springdoc 3.1.1. Detalhe em research R1, R2
e R8.

**Storage**: PostgreSQL 16 com Flyway. **Esquema não muda** (FR-012).

**Testing**: JUnit Jupiter 6.0.3, Mockito 5.23.0, Testcontainers 2.0.5, ArchUnit 1.3.0,
`MockRestServiceServer` para os clientes HTTP. A suíte hoje tem 67 arquivos de teste.

**Target Platform**: Cloud Run (serviço `buruna-backend` e Job `buruna-ingest`, mesma
imagem), `eclipse-temurin:21-jre-alpine`.

**Project Type**: web-service (monolito modular). O frontend não muda.

**Performance Goals**: os de hoje. Não há meta nova. Tempo de startup e memória iguais ou
menores, porque o Cloud Run escala a zero e o Job sobe a aplicação a cada execução.

**Constraints**: comportamento observável idêntico (FR-004 a FR-011). Volta só por troca de
revisão (FR-012). Free tier do GCP, sem custo novo.

**Scale/Scope**: 471 arquivos Java. Arquivos tocados: o `pom.xml`, o `application.yml`,
cerca de 11 classes de `src/main` (6 Jackson, 4 `RestTemplateBuilder` e
`BurunaApplication`, com sobreposição), 15 testes de integração (Testcontainers 2 e
`AutoConfigureMockMvc`), 4 testes que usam `ObjectMapper` e 2 docs.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio / regra | Situação | Nota |
|---|---|---|
| I. Clean Architecture por contexto | ✅ | Nenhum pacote novo nem classe movida. Só imports trocados |
| II. Fronteiras testadas (`ArchitectureTest`) | ✅ | Roda na suíte e precisa ficar verde sem regra afrouxada (FR-013, R8) |
| III. Domínio rico | ✅ | Nenhum agregado muda. Mapeamento JPA igual (R6) |
| IV. Segurança por padrão | ✅ | `SecurityConfig` igual (R7). Tokens, cookies e hash iguais (R2, o jjwt não muda). Segredos iguais. O motivo da feature é segurança (FR-001, FR-002) |
| V. Teste antes de mudar comportamento | ✅ | O commit 1 deixa o formato JSON em teste no Boot 3.5 antes da troca (R10, R13) |
| VI. Simplicidade | ✅ | Sem `starter-classic`, sem `spring-boot-jackson2`, sem mapper global novo. Só o que a troca de linha exige (R2, R3, R4) |
| VII. Trabalho fora da requisição | ✅ | Job, Scheduler e Pub/Sub iguais. Execução em andamento é idempotente (R11) |
| Padrões técnicos: "versão nova de dependência principal é decisão de plano" | ✅ | Este plano é essa decisão. A linha da stack na constituição recebe emenda PATCH (R12) |
| Fluxo: spec cita issue, docs no mesmo PR | ✅ | Spec cita #124. Docs no commit 3 (R12) |
| Portões do PR | ✅ | `./mvnw clean test` verde. O `npm run build` não muda, porque o front não é tocado. `validation.md` APROVADA antes do PR |

**Resultado**: sem violação. *Complexity Tracking* vazio.

**Re-check pós-design (Phase 1)**: os artefatos de design não criam entidade, endpoint nem
camada. O contrato da API ([contracts/api-invariants.md](contracts/api-invariants.md)) é o
de hoje, só registrado. O gate continua ✅.

## Project Structure

### Documentation (this feature)

```text
specs/003-migrar-spring-boot-4/
├── plan.md                    # este arquivo
├── research.md                # decisões R1–R13
├── data-model.md              # registra que o modelo não muda
├── quickstart.md              # roteiro verificável (local + depois do deploy)
├── baseline-boot3.md          # respostas HTTP gravadas no Boot 3.5, antes da troca (R10)
├── contracts/
│   └── api-invariants.md      # o que da API precisa ficar idêntico (FR-009–FR-011)
├── checklists/requirements.md
└── tasks.md                   # /speckit-tasks
```

### Source Code (repository root)

```text
backend/
├── pom.xml                                   # parent 4.1.3, sobrescritas fora, starters (R1, R4, R5, R8, R9)
├── src/main/resources/application.yml        # spring.jackson.use-jackson2-defaults (R2)
├── src/main/java/com/buruna/
│   ├── BurunaApplication.java                # import do UserDetailsServiceAutoConfiguration (R4)
│   ├── identity/web/PasswordResetPushController.java          # Jackson 3 (R2)
│   ├── identity/application/account/PubSubPasswordResetRequests.java  # Jackson 3
│   ├── shared/security/ProxySecretFilter.java                # Jackson 3
│   ├── shared/converter/StringListConverter.java             # Jackson 3
│   ├── shared/config/ExternalSourceConfig.java               # Jackson 3 + RestTemplateBuilder
│   ├── shared/source/mangadownloader/MangaDownloaderClient.java  # Jackson 3
│   └── (+ demais usos de RestTemplateBuilder, ex.: shared/notification/ResendEmailSender.java)
└── src/test/java/com/buruna/
    ├── **/*IntegrationTest.java              # Testcontainers 2, AutoConfigureMockMvc (R4, R5)
    ├── identity/IdentityIntegrationTest.java # + caracterização (R10)
    ├── reading/ChapterReadingIntegrationTest.java  # + caracterização de data e de null (R10)
    ├── work/WorkIntegrationTest.java          # + caracterização do ErrorResponse (R10)
    └── shared/source/mangadownloader/MangaDownloaderClientTest.java  # + volume nulo (R10)

.specify/memory/constitution.md               # emenda PATCH 1.2.1 (R12)
README.md                                     # versão (R12)
```

**Structure Decision**: a estrutura atual (backend em `backend/`, contextos por pacote) não
muda. O frontend fica fora.

## Riscos e como cada um aparece

| Risco | Onde aparece | Resposta |
|---|---|---|
| Flyway sem auto-config (R4) | startup dos `@SpringBootTest`: `validate` falha | `spring-boot-starter-flyway` |
| Override `jackson-bom.version=2.x` sobrevivendo (R1) | compilação ou classpath do Jackson 3 | remover junto com as outras |
| Formato JSON diferente (R2, R3) | testes do commit 1 | `use-jackson2-defaults`. Se um DTO externo quebrar, a correção é no DTO |
| Validação de esquema do Hibernate 7 (R6) | startup dos testes de integração | ajustar o mapeamento, nunca o esquema (FR-012) |
| Motor do ArchUnit no JUnit 6 (R8) | descoberta de testes | trocar para o artefato core |
| CVE nova no Tomcat 11 / dependência nova | Trivy (R11) | sobrescrita com motivo ao lado (FR-003) |

## Complexity Tracking

Sem violações da constituição.
