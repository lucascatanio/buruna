# Backlog

Itens fora do escopo das issues já executadas. Nada aqui deve ser feito sem issue própria.

## Bugs

### UI para deletar conta

`DELETE /auth/account` existe e tem teste no backend, mas não há UI no frontend que o chame.
Achado no [6.3], investigação read-only.

## Dívida técnica

### Arquivos órfãos em `volumes/` quando a deleção no GCS falha

O `DeletePrivateCollectionForUserUseCase` (e os demais deletes de volume) apagam as linhas do
banco dentro da transação e deletam os arquivos do GCS depois, fora dela, em best effort
(ADR-24). Se a deleção no GCS falhar, o banco não reverte e o arquivo fica em `volumes/`.

A lifecycle rule aplicada em 2026-09-24 (`gcs-lifecycle.json`, ADR-40) só cobre `pending/`,
os uploads que nunca chegaram ao finalize. Órfãos em `volumes/` precisam de outra rede, por
exemplo um job que compare o bucket com a tabela `volumes`.

### `application/` importando DTO da `web/` em identity, engagement e reading

O ADR-31 manda a dependência apontar só para dentro (`web → application`), mas 6 classes da
`application/` desses contextos importam Request/Response da própria `web/` (3 em
`identity`, 2 em `engagement`, 1 em `reading`). `manga` e `admin` já foram alinhados
(issue #34).

Escopo: mover para a `application/` os DTOs que o use case recebe ou devolve e incluir os
três contextos em `WEB_INDEPENDENT_CONTEXTS` no `ArchitectureTest`.

### Signed URL não é revogada imediatamente

Limitação conhecida do GCS. A URL assinada continua válida até expirar, mesmo que o acesso do
usuário seja revogado antes disso.

### Backend acessível direto pelo `run.app`

O `buruna-backend` tem ingress `all` e `allUsers` como invoker, porque o nginx do frontend
faz `proxy_pass` para a URL pública. Quem chama o `run.app` direto, sem passar pelo nginx,
ainda influencia a entrada do `X-Forwarded-For` que o rate limit lê (`APP_TRUSTED_PROXY_HOPS`,
ver DEPLOYMENT.md).

Escopo: ingress interno no backend e saída do frontend pela VPC. Atenção: o Cloud Scheduler
precisa passar a chamar o job por um caminho permitido, e o `APP_TRUSTED_PROXY_HOPS` precisa
ser recalibrado porque o caminho do header muda.

### Avisos de inatividade em lote

Desde que o envio de e-mail passou a ser síncrono (PR #11), o job envia um aviso por usuário
dentro da própria requisição. Com muitos inativos no mesmo dia, o job fica lento. A API de
lote do Resend (já usada nas notificações de admin) aceita e-mails com conteúdo diferente
por destinatário.

### Overrides de versão no `pom.xml`

O `pom.xml` sobrescreve versões gerenciadas pelo Spring Boot 3.5.16 (Tomcat, pgjdbc, Jackson,
HttpComponents, commons-lang3, log4j-api) para pegar correções de CVE. Ao atualizar o Boot,
remova cada override que ele já cobrir. Pendente: OpenTelemetry 1.49 → 1.62 (CVE média numa
extensão não usada), deixado de fora porque só o cliente do GCS depende dele e os testes o
mockam. Rodar `trivy fs backend/` depois de cada atualização.

### Origins locais no CORS do bucket de produção

O `gcs-cors.json` (espelho do bucket de produção) aceita `http://localhost` e
`http://192.168.100.192`, que só servem para testar contra o bucket de produção a partir da
máquina ou rede de desenvolvimento. Remover se não forem mais usados.

## Segurança (hardening)

Achados de baixa severidade do teste ativo de 2026-09-24 e do ADR-42. Nenhum é explorável
hoje; são defesa em profundidade.

### XSS armazenado em `title` e `synopsis`

Os campos são gravados e devolvidos sem sanitização. Não é explorável no frontend atual (o
React escapa e nenhuma página usa `dangerouslySetInnerHTML`), mas qualquer consumidor que
renderize HTML ficaria exposto. A defesa adequada é um `Content-Security-Policy` no nginx do
frontend, não escapar no backend.

### Possível corrida na cota de storage

O `QuotaService` soma o uso a cada finalize, sem reserva atômica nem lock. Dois finalizes
concorrentes, cada um dentro da cota, poderiam ultrapassá-la juntos. Não reproduzido: a cota
mínima ajustável pela API (0,1 GB) é grande demais para o teste. Escrever primeiro um teste de
integração que reproduza a corrida; corrigir só se ele falhar.

### Cadastro revela e-mail já cadastrado

`POST /auth/register` responde 409 "Already exists an user with this email". É enumeração de
conta por outro caminho que o ADR-42 não cobre, mais cara que o antigo timing do forgot porque
exige hCaptcha a cada tentativa.

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