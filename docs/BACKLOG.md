# Backlog

Itens fora do escopo das issues já executadas. Nada aqui deve ser feito sem issue própria.

## Dívida técnica

### Spring Boot 4 para os CVEs do `spring-webmvc`

O Trivy aponta CVE-2026-47884 e CVE-2026-47890 (CRITICAL) no `spring-webmvc` 6.2.19, já a
última versão aberta da linha 6.2. A correção só existe no Spring Framework 7.0.9, ou seja,
Spring Boot 4. É uma migração de versão maior, com issue própria. Ao migrar, remova os
overrides do `pom.xml` que o Boot 4 já cobrir e rode o Trivy de novo.

### Corrida no logout (500 raro)

`POST /auth/logout` deu 500 uma vez em 30 dias (2026-10-09, 1 em 18):
`TokenService.deleteRefreshToken` busca e apaga a entidade, e um refresh concorrente já tinha
apagado o token (`StaleObjectStateException`). Correção provável: apagar por consulta
(`deleteByToken`), idempotente.

### 2FA ativado antes da v1.6.0

Quem ativou 2FA antes da v1.6.0 teve o segredo TOTP enviado ao `api.qrserver.com` para gerar o
QR code (corrigido no #64). Falta decidir se pedimos para essas pessoas desativarem e ativarem
o 2FA de novo, com segredo novo.

## Features

- [ ] **E8: migração do legado (último épico do redesenho de capítulos).** Plano aprovado em
  2026-10-09 (`ADR-52` a escrever): quadrinho fora do MangaDex vira um capítulo por volume;
  livro vira capítulo-arquivo apontando para o mesmo PDF; obra que existe no MangaDex é
  vinculada e re-importada, mantendo os volumes não cobertos; progresso mapeado e histórico em
  snapshot; quota em bytes, tolerando estouro; `/leitor/{volumeId}` redireciona; depois de 30
  dias, apagar só os PDFs totalmente cobertos e a tabela `volumes`. Dry-run numa cópia do banco
  de produção antes. Até lá, volume legado conta como pt-BR no catálogo (ADR-50).
- [ ] **Fontes pelo manga-downloader, com metadados do AniList (ADR-54).** O sync, a
  migração das obras do MangaDex legado e o desligamento do adaptador atual ficam para uma ADR
  própria. Passos, em ordem:
  1. PR `list --json` no manga-downloader. Se for recusado ou demorar, wrapper Go AGPL num repo
     público separado;
  2. porta de metadados (AniList), com a tabela de tags (rank ≥ 80) e a sinopse revisada no
     import;
  3. ~~porta de capítulos (manga-downloader): `MANGA_DOWNLOADER` com URL da série, várias fontes
     por obra~~ (feito, import só vinculando a uma obra existente);
  4. ~~imagem própria do Job, com o binário e o Chromium, como segundo target do Dockerfile, e o
     Dependabot para a versão~~ (feito);
  - import criando a obra pelo AniList, com a coluna `anilist_id` (antes da tela);
  - colisão com capítulo de outra fonte ou de upload vira `IGNORED` (hoje `FAILED`, com o motivo),
    e o "usar esta versão" procura o capítulo na obra inteira (exige substituir capítulo enviado à
    mão no `work`);
  5. versão leve das páginas gerada no ingest;
  6. tela do import.

  **Não subir para a `main`** antes de o manga-downloader publicar uma release com o `list` (PR
  elboletaire/manga-downloader#449) e de o Dockerfile apontar para ela: a v24.1.0 não tem o
  subcomando, e a listagem falha em produção.
- [ ] Termos de uso e política de privacidade: caixa de aceite obrigatória no cadastro, com
  link para o texto, e tela ou modal pedindo o aceite a quem já tem conta. Decidir antes: quem
  escreve o texto; se o aceite guarda versão e data (para pedir de novo quando mudar); o que
  acontece se a pessoa recusar; se vale igual para leitores e colaboradores.
- [ ] Inatividade só apaga a coleção privada, sem desativar a conta (pedido de 2026-10-10).
  Hoje: 75 dias sem acesso → e-mail de aviso; 90 → `INACTIVE` + coleção apagada. Decidir
  antes: se o aviso continua (com texto novo), o que fazer com quem já está `INACTIVE` e se os
  limiares mudam. Atualizar ADR-36, glossário §7 e `InactivityJobIntegrationTest`.
- [ ] Fonte de livros em domínio público (Project Gutenberg via Gutendex, ou Standard Ebooks
  via OPDS), como segundo adaptador do `sourcing` (ADR-46): importa EPUB como edição. Exige
  tirar o MangaDex fixo do `sourcing` (lease, link e crédito da fonte); a ADR-54 já divide a
  porta e generaliza a fonte.
- [ ] Medir a leitura em 4G (DevTools "Fast 4G" e um celular): tempo até a primeira página e
  bytes por sessão, no leitor de capítulo e no de PDF.
- [ ] Compressão de PDF no upload
- [ ] Notificações de novos volumes (e-mail e sino no site)
- [ ] Login social com Google (OAuth)
- [ ] Lista de últimas atualizações na tela principal
- [ ] Links de GitHub e LinkedIn na tela de login
- [ ] Suporte a tablet
- [ ] Integração com MyAnimeList e Anilist (em estudo)
- [ ] Identidade visual Mahoraga (em estudo)

## Concluído

- [x] Redesenho de capítulos, épicos E0 a E7 (v2.0.0 em 2026-10-29, v2.1.0 em 2026-10-29):
  capítulo como unidade de leitura, upload em CBZ, CBR e PDF num Cloud Run Job, livros em PDF
  e EPUB, import e sync do MangaDex, idiomas no catálogo. Ver ADR-44 a ADR-53. Falta o E8
  (acima).

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