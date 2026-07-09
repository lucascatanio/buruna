# ADR-18 — Promote valida unicidade só contra mangás públicos

**Contexto:** Ao promover um mangá privado pra público, precisa verificar se não há conflitos de título e hash de volumes.

**Decisão:** Validar unicidade de título e hashes de volumes só contra mangás com `is_public = true`.

**Por quê:** Consequência direta da ADR-17. Mangás privados são invisíveis entre si. Se "One Piece Vol 1" existe em duas coleções privadas e um é promovido, o outro continua privado e não gera conflito. Validar contra todos os mangás (públicos + privados) impediria promotes legítimos e exporia informações sobre coleções de outros usuários, o que seria uma violação de privacidade.
