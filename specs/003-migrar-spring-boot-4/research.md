# Research: Migrar o backend para o Spring Boot 4

Decisões da feature [003](spec.md) (issue [#124](https://github.com/lucascatanio/buruna/issues/124)).
Versões e nomes de pacote conferidos em 2026-11-21 no Maven Central (BOM
`spring-boot-dependencies:4.1.3`, jars e fontes dos módulos citados), não de memória.

## R1. Versões-alvo e sobrescritas do `pom.xml`

**Decision**: parent `spring-boot-starter-parent:4.1.3`. Saem as **oito** sobrescritas de
versão do bloco "Sobrescrevem versões gerenciadas pelo Spring Boot 3.5.16". Ficam só
`pdfbox.version` e `jbig2-imageio.version`, que o Boot não gerencia.

| Propriedade | Sobrescrita hoje | Boot 4.1.3 gerencia | Ação |
|---|---|---|---|
| `tomcat.version` | 10.1.62 | 11.0.27 | remover |
| `postgresql.version` | 42.7.14 | 42.7.14 | remover |
| `jackson-bom.version` | 2.21.7 | **3.1.8** (Jackson 3) | remover. Manter quebraria o Jackson 3 (ver R2) |
| `httpclient5.version` | 5.6.4 | 5.6.5 | remover |
| `httpcore5.version` | 5.4.4 | 5.4.4 | remover |
| `commons-lang3.version` | 3.18.0 | 3.20.0 | remover |
| `log4j2.version` | 2.25.5 | 2.25.5 | remover |
| `opentelemetry.version` | 1.62.0 | 1.62.0 | remover |

Versões que vêm junto: Framework 7.0.11, Security 7.1.3, Spring Data 2026.0.3, Hibernate
7.4.15, Hibernate Validator 9.1.6, Flyway 12.4.0, Jackson 3.1.8 (e Jackson 2 em 2.21.8
pela `jackson-2-bom.version`), Testcontainers 2.0.5, JUnit Jupiter 6.0.3, Mockito 5.23.0,
Tomcat 11.0.27 (Servlet 6.1, Jakarta EE 11).

**Rationale**: FR-001 e FR-003. Cada versão gerenciada é igual ou mais nova que a
sobrescrita, então nenhuma continua necessária. Se o Trivy apontar algo depois da troca, a
sobrescrita volta com o motivo ao lado (FR-003).

**Alternatives considered**: Boot 4.0.x, rejeitado porque a 4.1 é a linha com correção
aberta mais longa, e o pedido da issue cita 4.1.3. Manter as sobrescritas "por garantia",
rejeitado: duplicam o BOM e o comentário do pom pede que saiam.

## R2. Jackson 3 com os padrões do Jackson 2

**Decision**: Jackson 3 (`tools.jackson.*`) com `spring.jackson.use-jackson2-defaults: true`
no `application.yml`. As seis classes de `src/main` que usam `ObjectMapper` passam para a
API do Jackson 3. O usuário pediu a recomendação, e esta foi a escolhida.

- Injetadas (o bean do Boot 4 é o `tools.jackson.databind.json.JsonMapper`):
  `PasswordResetPushController`, `PubSubPasswordResetRequests`, `ProxySecretFilter`,
  `ExternalSourceConfig`, `MangaDownloaderClient`. O tipo injetado vira
  `tools.jackson.databind.ObjectMapper` (ou `JsonMapper`), e `JsonProcessingException`
  (checada) vira `tools.jackson.core.JacksonException` (não checada). Onde a exceção só era
  relançada, o `try/catch` e o `throws` saem.
- `StringListConverter` tem um mapper próprio (`new ObjectMapper()`) e passa a
  `JsonMapper.builder().build()`. Ele só converte `List<String>`, então os padrões não mudam
  nada.
- As anotações continuam em `com.fasterxml.jackson.annotation` (o Jackson 3 reaproveita o
  `jackson-annotations`), então `@JsonIgnoreProperties`, `@JsonIgnore` e `@JsonInclude` não
  mudam.
- Os quatro testes que usam `ObjectMapper` (`InactivityJobIntegrationTest`,
  `IdentityIntegrationTest`, `ProxySecretFilterTest`, `MangaDownloaderClientTest`) passam
  para os mesmos imports.

Conferido no fonte de `spring-boot-jackson:4.1.3` (`JacksonAutoConfiguration#customize`):
com a propriedade ligada, o builder recebe `configureForJackson2()` e desliga
`WRITE_DATES_AS_TIMESTAMPS`, `WRITE_DURATIONS_AS_TIMESTAMPS`, `FAIL_ON_UNKNOWN_PROPERTIES` e
`DEFAULT_VIEW_INCLUSION`. São os mesmos padrões que o Boot 3 aplicava ao Jackson 2.

**Rationale**: FR-009 com uma linha de configuração. O código já fica na API do Jackson 3,
e adotar os padrões novos depois é uma troca isolada: desligar a propriedade e ver o que o
teste de caracterização (R10) pega.

**Alternatives considered**:
- Jackson 3 com os padrões novos (ordem alfabética, `FAIL_ON_NULL_FOR_PRIMITIVES`,
  `FAIL_ON_TRAILING_TOKENS`): rejeitado agora porque mistura mudança de comportamento numa
  feature que promete não mudar nada.
- Módulo `spring-boot-jackson2`: rejeitado porque já nasce obsoleto e só adia a troca.

**Jackson 2 continua no classpath**: o `jjwt-jackson` 0.12.6 (e o 0.13.0, o mais novo)
depende de `com.fasterxml.jackson.core`. Fica assim. A versão vem da `jackson-2-bom.version`
do Boot (2.21.8), que o Trivy também confere. O jjwt não muda, então o formato e a
assinatura dos tokens também não mudam (FR-004, sessões abertas continuam válidas).

## R3. `RestTemplate` montado à mão usa o Jackson 3 puro

**Decision**: sem mudança de código. A verificação fica com os testes de cliente que já
existem (`MockRestServiceServer`).

No Framework 7, o `new RestTemplate()` (usado em `CaptchaService`) e o `RestTemplateBuilder`
instanciado à mão registram o `JacksonJsonHttpMessageConverter`, que usa
`JsonMapper.builder()` sem os padrões do Boot (conferido no fonte de `spring-web:7.0.11`).
No Framework 6, o `Jackson2ObjectMapperBuilder` desligava `FAIL_ON_UNKNOWN_PROPERTIES`. Isso
não muda nada, porque no Jackson 3 esse padrão já é `false` (fonte de
`jackson-databind:3.1.8`). Diferem `FAIL_ON_NULL_FOR_PRIMITIVES` e
`FAIL_ON_TRAILING_TOKENS`, que passam a `true`.

Os DTOs de resposta externa com campo primitivo estão em `shared/source`. O
`SiteListing` (`double number`, `boolean unnumbered`, `int volume`) é lido pelo
`ObjectMapper` injetado, que fica com os padrões do Jackson 2 (R2). Os clientes HTTP
(`MangaDexSource`, `AniListSource`, `CaptchaService`, `PubSubPublisher`,
`CloudRunJobLauncher`, `ResendEmailSender`) têm teste com `MockRestServiceServer` que
desserializa payload realista. Esses testes rodam contra o converter novo.

**Rationale**: o caminho mais simples é deixar o teste mostrar se algum DTO quebra. Se um
quebrar, a correção é no DTO (tipo wrapper ou `@JsonIgnoreProperties`), não um mapper
global.

**Alternatives considered**: injetar o `RestTemplateBuilder` do Boot em todos os clientes,
para herdar os padrões configurados. Rejeitado porque mexe no construtor de seis classes sem
falha concreta que justifique.

## R4. Starters modulares do Boot 4

**Decision**: o Boot 4 dividiu a auto-configuração em módulos. Trocas no `pom.xml`:

| Hoje | Boot 4 | Por quê |
|---|---|---|
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` | nome novo. O antigo continua, mas obsoleto |
| `flyway-core` sozinho | + `spring-boot-starter-flyway` | **crítico**: sem o starter a auto-config do Flyway não existe, as migrations não rodam e o `ddl-auto: validate` falha no startup |
| (vem no `starter-web`) | + `spring-boot-starter-restclient` | `RestTemplateBuilder` mudou para `org.springframework.boot.restclient` |
| (vem no `starter-test`) | + `spring-boot-starter-webmvc-test` (test) | `@AutoConfigureMockMvc` mudou para `org.springframework.boot.webmvc.test.autoconfigure` |

Imports que mudam de pacote (conferidos nos jars 4.1.3):

- `RestTemplateBuilder` → `org.springframework.boot.restclient.RestTemplateBuilder`, em 4
  arquivos de `src/main`.
- `UserDetailsServiceAutoConfiguration` →
  `org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration`, no
  exclude de `BurunaApplication`.
- `AutoConfigureMockMvc` → `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`,
  em 12 testes.
- `ServiceConnection`, `SpringBootTest` e `TestConfiguration` não mudam.

O `flyway-database-postgresql` continua. `spring-security-test` continua como está
(gerenciado pelo BOM).

**Rationale**: FR-012 e FR-013. Sem o starter do Flyway, o app não sobe. Os testes de
integração pegam isso logo, porque o Testcontainers começa com o banco vazio.

**Alternatives considered**: `spring-boot-starter-classic` / `spring-boot-starter-test-classic`,
que trazem toda a auto-configuração como no Boot 3. Rejeitado porque é muleta de transição
e esconde dependência que o projeto tem de fato.

## R5. Testcontainers 2

**Decision**: artefatos `org.testcontainers:testcontainers-postgresql` e
`org.testcontainers:testcontainers-junit-jupiter` (os antigos `postgresql` e `junit-jupiter`
não existem mais no BOM 2.x). Nos 15 testes, `org.testcontainers.containers.PostgreSQLContainer<?>`
vira `org.testcontainers.postgresql.PostgreSQLContainer`, sem genérico. A classe antiga
ainda existe no 2.0.5, mas obsoleta. `@Testcontainers` e `@Container` não mudam de pacote.

O `-Dapi.version=1.41` do surefire continua. Ele fixa a API do Docker que o docker-java
negocia e não depende da versão do Testcontainers. Se o 2.x já negociar sozinho, sair fica
para outra issue.

**Rationale**: FR-013. A suíte de integração inteira depende disso.

## R6. Hibernate 7.4 e JPA 3.2

**Decision**: sem mudança de mapeamento. O que o código usa existe no Hibernate 7:
`@JdbcTypeCode(SqlTypes.NAMED_ENUM)` (14 usos), `@CreationTimestamp`/`@UpdateTimestamp`,
`@Lock`, `@EntityGraph`, `@Query` JPQL (sem nativa, pela constituição) e `@ElementCollection`.
Aviso de obsolescência no build não bloqueia. Erro de compilação ou de validação de esquema
é tratado na hora.

O risco conhecido do Hibernate 7 é o `merge()` de entidade com id gerado que não existe no
banco, que agora lança exceção. Ele não se aplica aqui. As quatro entidades com
`@GeneratedValue(strategy = UUID)` nascem por `new` sem id e vão para `persist`. A única
entidade com id atribuído (`SourceLease`, chave = `SourceName`) tem as linhas criadas pela
migration e só é lida e alterada.

`ddl-auto: validate` continua. Nos testes de integração, ele compara o mapeamento com o
esquema das migrations no Postgres real e pega qualquer diferença de tipo que o Hibernate 7
traga.

**Rationale**: FR-012 (sem migration nova). A volta é só trocar a revisão.

## R7. Spring Security 7

**Decision**: sem mudança. `SecurityConfig` já usa a DSL de lambdas,
`authorizeHttpRequests`, `requestMatchers(String)`/`(HttpMethod, String)` e
`@EnableMethodSecurity`. Nenhuma API removida no 7 (`and()`, `authorizeRequests`,
`AntPathRequestMatcher`, `MvcRequestMatcher`) aparece no código. No 7, o
`requestMatchers(String)` usa `PathPatternRequestMatcher`, e os padrões continuam relativos
ao `context-path` `/api`, como hoje.

O 401 sai por `response.sendError(...)`, que o Boot transforma no JSON de erro padrão dele.
Como nenhum teste confere o corpo desse 401 hoje, a caracterização (R10) passa a conferir.

**Rationale**: FR-010, FR-011. Os testes de integração já cobrem a matriz de permissão
(permitAll, autenticado, papel, posse).

## R8. springdoc e ArchUnit

**Decision**:
- `springdoc-openapi-starter-webmvc-ui` 2.8.17 → **3.1.1**. A linha 3.1 é a do Boot 4.1
  (o parent do 3.1.1 é o Boot 4.1.0). A 3.2.x já mira o Boot 4.2. O Swagger continua
  desligado em produção (`SWAGGER_ENABLED`).
- ArchUnit `archunit-junit5` 1.3.0 continua. O `ArchitectureTest` usa só a API core com
  `@Test` do JUnit Jupiter, não `@ArchTest`. Se o motor do ArchUnit atrapalhar a descoberta
  de testes no JUnit Platform 6, a troca é para o artefato core `com.tngtech.archunit:archunit`
  1.3.0, sem tocar no teste.

## R9. commons-logging

**Decision**: a exclusão de `commons-logging` no `pdfbox` continua. Só o comentário muda. No
Framework 7, o `spring-jcl` saiu e o `spring-core` depende direto de `commons-logging` 1.3.x
(conferido no pom do `spring-core:7.0.11`). A exclusão segue evitando a segunda cópia que o
pdfbox traria, mas o comentário "o Spring Boot já faz a ponte (spring-jcl)" deixa de ser
verdade.

## R10. Rede de segurança antes da troca

**Decision**: a constituição (princípio V) pede teste cobrindo o comportamento atual antes
de refatorar. Hoje nenhum teste confere o formato de data, o `NON_NULL` nem o corpo do 401.
Então o **primeiro commit**, ainda no Boot 3.5, acrescenta asserções aos testes que já
existem e roda verde:

- `IdentityIntegrationTest`: `LoginResponse` sem os campos nulos (`@JsonInclude(NON_NULL)`).
  No login sem 2FA, o corpo bruto não contém `tempToken`.
- `ChapterReadingIntegrationTest`: `urlsExpireAt` (um `Instant`) em ISO-8601 com fuso,
  conferido por regex, e não só `exists()`. Um campo nulo sem `@JsonInclude`
  (`nextChapterId`) aparece como `"nextChapterId":null` no corpo bruto. A conferência é
  por `content().string(containsString(...))`, porque `jsonPath(...).value(nullValue())`
  também passa quando o campo some.
- `WorkIntegrationTest` (`postTag_nonexistentCategory_returns404`): o `ErrorResponse` do
  `GlobalExceptionHandler` tem os cinco campos `status, error, message, path, timestamp`.
- `MangaDownloaderClientTest`: listagem com `"volume": null` dá `volume() == 0`, como hoje.

O corpo do 401 sai por `response.sendError(...)` e é montado pela página de erro do Boot.
O MockMvc não encaminha para essa página, então nenhum teste com MockMvc enxerga esse
corpo. Ele é conferido por uma **baseline gravada à mão** no Boot 3.5: `curl` do app local
em `specs/003-migrar-spring-boot-4/baseline-boot3.md` (401, 404, rota inexistente,
`/health`). Depois da troca, o quickstart compara com ela. Isso evita criar o primeiro teste
`RANDOM_PORT` do projeto só para isso, num módulo (`TestRestTemplate`) que o próprio Boot 4
mudou de lugar.

Depois a troca de versão entra, e essas asserções precisam seguir verdes sem edição. Nos
testes, só a construção do mapper e os imports mudam (R2).

**Rationale**: FR-009 e FR-013, que são o critério de "nada mudou" escrito em teste.
Asserção em teste que já existe segue "um arquivo de teste por classe testada" sem criar
arquivo novo.

## R11. Varredura, deploy e volta

**Decision**:
- **Trivy à mão**, porque não está no CI: `trivy image` na imagem `service` montada pelo
  `backend/Dockerfile`, antes do PR e de novo depois do deploy. Critério: zero CRITICAL,
  sem CVE-2026-47884/47890 (SC-001).
- **Deploy** pelo fluxo normal (PR para `dev`, release `dev` → `main` com versão nova). Não
  há mudança de variável, segredo, Job nem agendamento. O Cloud Run Job `buruna-ingest` usa
  a mesma imagem, então pega a versão nova na próxima execução. Uma execução que já estava
  em andamento termina na antiga, e o processamento é idempotente (ADR-48).
- **Volta**: mandar o tráfego para a revisão anterior do serviço e apontar o Job para a
  imagem anterior. Não há migration, então nada muda no banco (SC-006).

## R12. Documentos que mudam

**Decision**, no mesmo PR (FR-015):
- `.specify/memory/constitution.md`: "Spring Boot 3.5" → "Spring Boot 4.1" em *Padrões
  técnicos*. É emenda PATCH (1.2.0 → 1.2.1), porque só a redação da versão muda.
- `README.md`: o badge e a linha 27 (3.5.16 → 4.1.3).
- `backend/pom.xml`: o comentário do bloco de sobrescritas sai junto com elas.
- `docs/DEVELOPMENT.md`, `DEPLOYMENT.md`, `ARCHITECTURE.md`, `TESTING.md` e `SECURITY.md` não
  citam a versão (conferido por grep). O `TESTING.md` continua certo: `@MockitoBean`
  e `@SpringBootTest` + Testcontainers não mudam de nome.

## R13. Ordem dos commits

**Decision**: três commits, cada um com a suíte verde:
1. `test(backend): caracteriza o formato JSON antes do Boot 4 (#124)`, ainda no Boot 3.5 (R10).
2. `chore(backend): Spring Boot 4.1.3 (#124)`, com pom, imports, `application.yml` e testes
   ajustados (R1 a R9).
3. `docs: Spring Boot 4.1 na constituição e no README (#124)` (R12).

**Rationale**: o primeiro commit prova que os testes novos descrevem o comportamento de
hoje. Sem ele, um teste escrito já no Boot 4 poderia estar descrevendo o comportamento novo.
