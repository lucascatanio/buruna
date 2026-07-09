# ADR-28 — Refresh token rotation no /auth/refresh

Já implementado desde o MVP

**Contexto:** Sem rotation, um refresh token roubado dava acesso por 7 dias inteiros — o atacante e o usuário legítimo podiam usar o mesmo token em paralelo sem que nenhum dos dois percebesse.

**Decisão:** Cada chamada a `POST /auth/refresh` deleta o token usado e gera um novo. O response devolve accessToken + refreshToken novos, e o frontend atualiza os dois no storage.

**Justificativa:** Se alguém roubar o token e usá-lo, o original morre. Na próxima vez que o usuário legítimo tentar renovar, o token dele já não existe — recebe 401 e precisa relogar. Não é perfeito (o atacante ainda usou uma vez), mas a janela de exploração cai de 7 dias para um único ciclo.

**Tradeoff aceito:** Se por algum motivo o mesmo refresh token for enviado duas vezes (ex: resposta de rede duplicada, retry automático), a segunda chamada dá 401 e força logout. O interceptor Axios do frontend evita isso com uma fila que serializa chamadas ao `/auth/refresh`, então na prática não acontece.
