# ADR-35 — Autorização unificada e fim do acoplamento user↔manga

**Status:** Aceita (Fase 2 da refatoração)
**Contexto:** A autorização está espalhada em três mecanismos: `@PreAuthorize` (7 controllers), regras de URL no `SecurityConfig`, e **checagem manual de role dentro dos services** (`VolumeService.assertCanModify`, `MangaService`, `PrivateMangaService.promote`). Além disso, o módulo `user` (`InactivityJob`) importa repositórios e entidades internas de `manga`, e `manga` importa internals de `user` — acoplamento bidirecional.

**Decisão:**
1. **Autorização por papel (RBAC) fica na borda**, via `@PreAuthorize` nos controllers, de forma consistente em todos os endpoints protegidos. As regras de URL no `SecurityConfig` cobrem só o que é por natureza de path (público vs autenticado, header de job).
2. **Autorização por posse (ownership) é responsabilidade do caso de uso**, garantida por `actorId` (ex.: query `findOwnedPrivate(mangaId, actorId)` ou comparação explícita), em vez de `if (role == ...)` solto no service. Quando a regra for "dono **ou** ADMIN", o use case recebe `actorId` **e** o papel como **primitivos** (`Role`/`boolean`).
3. **O agregado de domínio NUNCA recebe a entidade `User` de outro contexto.** Qualquer invariante de domínio usa apenas primitivos/VOs do próprio contexto (ex.: `Manga.promoteToPublic()` sem parâmetro; um eventual `ownerId` é UUID, não `User`). Passar `User` para `manga.domain` violaria a regra de dependência (ADR-31) — foi explicitamente corrigido no exemplo §7 da arquitetura-alvo.
4. **Remover a checagem de role solta dos services.**
5. **Contextos se comunicam só por casos de uso públicos.** O job de inatividade (contexto `identity`) deixa de importar `manga.repository`/`manga.domain`; passa a chamar um caso de uso publicado pelo contexto `manga`, **`DeletePrivateCollectionForUserUseCase(userId)`** (fronteira transacional e I/O externo detalhados na Fase 3).

**Por quê:** Um lugar para RBAC (borda) e um lugar para ownership (domínio) torna trivial responder "quem pode o quê" numa revisão de PR — importante para contribuidores. Comunicação via use case quebra o acoplamento `user↔manga` e respeita a regra de dependência (ADR-31).

**Tradeoff:** Defense-in-depth (RBAC na borda + ownership no domínio) significa a regra de acesso aparecer em dois níveis; é intencional e cada nível tem responsabilidade distinta (papel × posse). Publicar um use case de exclusão de coleção cria uma dependência explícita `identity → manga` (direção única, aceitável) em vez do acoplamento bidirecional atual.
