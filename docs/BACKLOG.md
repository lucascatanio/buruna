# Backlog

Itens fora do escopo das issues já executadas. Nada aqui deve ser feito sem issue própria.

## Dívida técnica

### Spring Boot 4 para os CVEs do `spring-webmvc`

O Trivy aponta CVE-2026-47884 e CVE-2026-47890 (CRITICAL) no `spring-webmvc` 6.2.19, já a
última versão aberta da linha 6.2. A correção só existe no Spring Framework 7.0.9, ou seja,
Spring Boot 4. É uma migração de versão maior, com issue própria. Ao migrar, remova os
overrides do `pom.xml` que o Boot 4 já cobrir e rode o Trivy de novo.

## Features

- [ ] Trocar volumes por capítulos. Decidir entre criar tabela de capítulo vinculada ao volume,
  ou usar a tabela de volumes como se fosse capítulo. Muda a modelagem do contexto `manga`.
- [ ] Suporte a CBZ e CBR
- [ ] Compressão de PDF no upload
- [ ] Notificações de novos volumes (e-mail e sino no site)
- [ ] Login social com Google (OAuth)
- [ ] Lista de últimas atualizações na tela principal
- [ ] Links de GitHub e LinkedIn na tela de login
- [ ] Suporte a tablet
- [ ] Integração com MyAnimeList e Anilist (em estudo)
- [ ] Identidade visual Mahoraga (em estudo)

## Concluído

- [x] Dívidas técnicas revisadas em 2026-10-08:
  - Órfãos em `volumes/` no GCS: job `POST /admin/jobs/storage-orphans` (PR #75) apaga o que
    não tem linha em `volumes` e tem mais de 7 dias, com `dryRun` e trava de proporção. Job
    semanal `buruna-storage-orphans` no Cloud Scheduler desde 2026-10-09.
  - Backend direto pelo `run.app`: segredo compartilhado nginx → backend (PR #76, ADR-43).
    Segredo `buruna-proxy-secret` ligado aos dois serviços em 2026-10-09: chamada direta ao
    `run.app` responde 403.
  - Overrides do `pom.xml` revisados (PR #74): todos ainda necessários, Jackson 2.21.7,
    patches de postgresql/httpclient5/httpcore5 e OpenTelemetry 1.62.0. O Trivy foi de 10 achados
    para 2, que dependem do Spring Boot 4 (ver Dívida técnica).
  - Origens locais no CORS do bucket: removidas e aplicadas no bucket (PR #73).
  - Signed URL não revogada: risco aceito na atualização de 2026-10-08 do ADR-07 (PR #73).
- [x] XSS armazenado em `title` e `synopsis`: `Content-Security-Policy` no nginx do frontend
  (sem script inline nem de origem não listada; libera só o site, o hCaptcha e o GCS). Ficou
  em Report-Only na v1.6.1 e foi validado em produção sem violação antes de bloquear.
  Também saiu o QR do 2FA gerado pelo `api.qrserver.com`, que recebia o segredo TOTP (PR #64).
- [x] Avisos de inatividade em lote: o job junta os avisados e manda tudo numa chamada da API
  de lote do Resend (`EmailSender.sendBatch`, conteúdo próprio por destinatário, fatiado em
  blocos de 100), antes das desativações, em vez de uma chamada por usuário.
- [x] Corrida na cota de storage: dois finalizes concorrentes do mesmo dono, cada um dentro
  da cota, passavam juntos e a estouravam (reproduzido em teste de integração). O
  `QuotaService.assertCanFit` trava os mangás privados do dono (`SELECT ... FOR UPDATE`) antes
  de somar o uso, e a checagem fica serializada por usuário.
- [x] Cadastro não revela e-mail já cadastrado: `POST /auth/register` responde 201 como um
  cadastro novo e manda ao dono do e-mail um aviso com links de login e de recuperação de
  senha, rodando o BCrypt mesmo assim (atualização no ADR-42). Username repetido continua 409.
- [x] DTOs de `identity`, `engagement` e `reading` saem da `web/` para a `application/`
  (PR #59): os 15 Request/Response que o use case recebe ou devolve moram no pacote do use
  case, e a regra `domainAndApplication_shouldNotDependOnWebLayer` do `ArchitectureTest` vale
  para todos os contextos (`MIGRATED_CONTEXTS`).
- [x] 2FA de usuário autenticado responde 403 em código errado ou reutilizado (PR #55):
  `/auth/2fa/verify` e `/auth/2fa/disable` respondiam 401, e o interceptor do axios fazia
  refresh e reenviava o código, contando cada erro duas vezes no bloqueio.
- [x] UI para deletar conta (issue #49): seção "Excluir conta" na página de Segurança, com aviso do
  que é apagado e do que fica, senha e código 2FA quando ativo; o admin mostra contas
  `DELETED` como "Removido", sem edição. Backend no #48 (issue #46).
- [x] Último `@Scheduled` removido (issue #44): a limpeza do `RateLimitFilter` roda no próprio
  filtro, no máximo uma vez por janela, e o `@EnableScheduling` saiu (ADR-03).
- [x] `MangaSubmissionStatus.APPROVED` (issue #40): `Manga.approve` grava `APPROVED` em vez de
  zerar o status (V24 + backfill na V25), e `promoteToPublic` encerra uma submissão aberta,
  que antes deixava o mangá público na fila de revisão do admin.
- [x] CLAUDE.md não cita mais o intervalo de ADRs ("ADR-01 a ADR-41"), que envelhecia a
  cada ADR novo; aponta só para a pasta `docs/adr/` (issue #38).
- [x] `@MockBean` (depreciado desde o Spring Boot 3.4) trocado por `@MockitoBean` nos testes
  de integração (issue #36).
- [x] Migração de pacotes de `manga` e `admin` concluída (issue #34): `controller/` → `web/`,
  `service/` → `application/`, `manga/exception/` → `manga/domain/`, e os `dto/` separados
  entre `application/` (o que o use case recebe ou devolve) e `web/` (só do controller).
  `admin` entrou no `MIGRATED_CONTEXTS`, e o `ArchitectureTest` ganhou a regra
  `domainAndApplication_shouldNotDependOnWebLayer`.
- [x] Status HTTP do catálogo e do upload (issues #29, #30 e #31): título acima de 255
  caracteres e `originCountry` acima de 100 respondem 400 em vez de 409, e o sufixo do slug
  (`-2`, `-3`…) não estoura mais a coluna; `finalize` de upload já movido ou inexistente
  responde 404 em vez de 500; `GET /mangas?sort=` só aceita `title`, `createdAt`,
  `updatedAt`, `avgRating`, `viewCount` e `year`, e outro campo responde 400.
- [x] Reset de senha em tempo constante via Pub/Sub push com OIDC e job de inatividade
  disparado pelo Cloud Scheduler, em vez de `@Scheduled` (PRs #19 e #20, ADR-42).
- [x] Adicionar volume a mangá público recém-criado não retorna mais 500: verificado em
  produção em 2026-09-24 (`POST /mangas` → 201, `upload-url` → 200, `finalize` → 201).
- [x] Revisão de segurança de 2026-09-23: bypass de 2FA, deleção de arquivos de outros
  usuários, rate limit burlável, força bruta e replay de TOTP, segredos com default, captcha
  desligado em produção, tokens em claro e em `localStorage` (PR #9, ADR-40, ADR-41).
- [x] Lifecycle rule no bucket para uploads nunca finalizados (`pending/`, ADR-40).
- [x] E-mails falhando em produção (62 de 84 em 30 dias) por envio `@Async` com CPU cortada
  pelo Cloud Run. Envio síncrono e notificação de admins em lote (PR #11).
- [x] Dependências do backend: Spring Boot 3.4.3 → 3.5.16, de 88 para 1 CVE no trivy (PR #13).
- [x] Exceções do Spring MVC com status 4xx (rota inexistente, header ausente) viravam 500
  no `GlobalExceptionHandler`; agora mantêm o status.
- [x] Testes de integração nos fluxos críticos, com `@SpringBootTest` e Testcontainers, rodando
  no GitHub Actions em cada PR e push. Entregue nos Epics 0 a 6: 289 testes.
- [x] Deploy: frontend espera o backend. `needs: deploy-backend` no job do frontend em
  `.github/workflows/deploy.yml` (commit `c1411f9`).