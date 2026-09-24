# Backlog

Itens fora do escopo das issues já executadas. Nada aqui deve ser feito sem issue própria.

## Bugs

### Adicionar volume a mangá público recém-criado retorna 500

Reportado antes da refatoração. **Verificar se ainda ocorre.** O Epic 4 corrigiu um 500 nesse
mesmo caminho: a `InsufficientStorageQuotaException` devolvia 500 porque o
`GlobalExceptionHandler` ignorava o `@ResponseStatus`. Hoje devolve 422. Pode ter sido o mesmo
bug. Se ainda reproduzir, o fluxo agora tem cobertura de integração em `MangaIntegrationTest`,
o que facilita o diagnóstico.

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

### Adicionar `MangaSubmissionStatus.APPROVED`

O fluxo de submissão é assimétrico. `REJECTED` é estado persistido (`Manga.reject`), mas a
aprovação não tem estado próprio: `Manga.approve` marca `isPublic=true` e zera
`submissionStatus`, saindo do fluxo sem deixar rastro no enum. Confunde quem lê o domínio
(ver `docs/glossario-dominio.md`). Achado na Fase 4, investigação read-only.

Escopo: enum `{PENDING, APPROVED, REJECTED}`, migration, ajuste em `ReviewSubmissionUseCase` e
`Manga.approve`, teste de regressão.

### Rename `controller/` para `web/` e `service/` para `application/`

`manga/controller/` (MangaController, PrivateMangaController, VolumeController) convive com
`manga/web/` (só TagController). `admin/controller/` e `admin/service/` nunca foram renomeados.
Investigado no [6.3]: sem duplicação de rota, tudo vivo e chamado pelo frontend e pelos testes.
É inconsistência de nomenclatura de migração incompleta, não código morto.

Escopo: rename puro, sem mudança de comportamento.

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

### `@MockBean` depreciado

Os testes de integração usam `@MockBean`, depreciado desde o Spring Boot 3.4 e marcado para
remoção. Troca mecânica por `@MockitoBean`.

### Origins locais no CORS do bucket de produção

O `gcs-cors.json` (espelho do bucket de produção) aceita `http://localhost` e
`http://192.168.100.192`, que só servem para testar contra o bucket de produção a partir da
máquina ou rede de desenvolvimento. Remover se não forem mais usados.

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