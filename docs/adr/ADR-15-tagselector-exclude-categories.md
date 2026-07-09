# ADR-15 — TagSelector oculta "Aviso de Conteúdo" via excludeCategories

**Contexto:** Tags de "Aviso de Conteúdo" (gore, violência, etc.) existem no banco pra marcar mangás, mas não devem aparecer como filtro na busca da biblioteca. Só no formulário de edição/criação.

**Decisão:** O componente `TagSelector` recebe uma prop `excludeCategories` e oculta as categorias listadas. Sem alteração no banco.

**Por quê:** Remover a categoria do banco eliminaria a funcionalidade de marcar conteúdo sensível, que é importante pra experiência do leitor. A filtragem no frontend é simples, flexível (diferentes telas excluem categorias diferentes) e não precisa de lógica no backend.

**Tradeoff:** A categoria ainda é retornada pela API, com overhead mínimo de dados. Se necessário, dá pra adicionar um parâmetro `excludeCategories` na API no futuro.
