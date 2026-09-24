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

### Lifecycle rule de 24h no bucket GCS para arquivos órfãos

Duas fontes de órfãos hoje. A primeira é upload iniciado e nunca finalizado (signed URL usada
sem chamar `finalize`). A segunda apareceu no Epic 5: o `DeletePrivateCollectionForUserUseCase`
apaga as linhas do banco dentro da transação e deleta os arquivos do GCS depois, fora dela, em
best effort (ADR-24). Se a deleção no GCS falhar, o banco não reverte e o arquivo fica.

A lifecycle rule é a rede que segura essa decisão de design.

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

- [x] Testes de integração nos fluxos críticos, com `@SpringBootTest` e Testcontainers, rodando
  no GitHub Actions em cada PR e push. Entregue nos Epics 0 a 6: 289 testes.
- [x] Deploy: frontend espera o backend. `needs: deploy-backend` no job do frontend em
  `.github/workflows/deploy.yml` (commit `c1411f9`).