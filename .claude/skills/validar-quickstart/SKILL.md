---
name: "validar-quickstart"
description: "Valida uma feature implementada: roda os testes, sobe o app local e segue o quickstart.md no navegador pelo Playwright MCP, gravando o resultado em validation.md. Use depois do /speckit-implement e do /speckit-converge, antes de abrir o PR."
argument-hint: "Pasta da feature (opcional), ex.: specs/001-parar-acompanhar-obra"
user-invocable: true
disable-model-invocation: false
---

## Entrada

```text
$ARGUMENTS
```

## Papel

Você é quem testa, não quem corrige. O trabalho é observar se a feature faz o que a spec
promete e registrar o que viu. Não altere código de aplicação durante a validação: falha vira
registro no `validation.md` e próximo passo para o `/speckit-implement`.

Nunca marque um passo como aprovado sem ter visto o resultado (snapshot da página, resposta
da API ou saída do comando). Passo que não deu para executar fica como **não executado**,
nunca como aprovado.

## Passos

### 1. Achar a feature

- Se a entrada tem uma pasta, use-a. Senão, leia `feature_directory` em
  `.specify/feature.json`. Sem nenhum dos dois, pergunte qual pasta de `specs/`.
- Leia `quickstart.md`, `spec.md` e `tasks.md` da pasta. Sem `quickstart.md`, pare e diga que
  o `/speckit-plan` precisa gerá-lo.
- Se o `tasks.md` tem tarefas sem `[X]` fora da fase de polish, avise e pergunte se é para
  validar mesmo assim.

### 2. Conferir o navegador

Confira se as ferramentas do Playwright MCP (`browser_navigate`, `browser_snapshot`,
`browser_click`, `browser_type`) estão disponíveis. Se não estiverem, pare e diga ao usuário:
o servidor `playwright` está em `.mcp.json` e precisa ser aprovado no Claude Code (`/mcp`),
com o Chrome instalado (ou outro navegador via `--browser`).

### 3. Checagens automáticas

Rode e guarde o resumo de cada uma (total, falhas, nome dos testes que falharam):

- `./mvnw -B clean test` em `backend/` (precisa do Docker para o Testcontainers);
- `npm run build` em `frontend/`, se a feature toca o front.

Docker fora do ar ou comando que não rodou é **não executado**, não aprovado.

### 4. Subir o ambiente

```bash
scripts/dev-env.sh up
scripts/dev-env.sh admin
```

O `up` reaproveita o que já estiver no ar e espera o backend em `localhost:8080` e o Vite em
`localhost:5173`. O `admin` deixa uma conta ADMIN ativa e imprime o e-mail e a senha, que ficam
também em `.dev-env/admin.env` (gerados na primeira vez, só locais; não copie a senha para o
`validation.md`). Se precisar de um usuário comum, cadastre pela tela e aprove
com o ADMIN, como um usuário faria. Se o `up` falhar, mostre o fim do log de `.dev-env/` e
pare.

### 5. Seguir o roteiro no navegador

Para cada passo da seção manual do `quickstart.md`:

1. Faça a ação no navegador pelo Playwright MCP (`browser_navigate`, `browser_snapshot`,
   `browser_click`, `browser_type`, `browser_fill_form`). Use o snapshot para achar os
   elementos pelo texto que o usuário vê, não por seletor CSS. Confirmação do navegador
   (`confirm()`, como em "Ignorar grupo") se responde com `browser_handle_dialog`, e o texto
   dela também é algo a conferir.
2. Quando o passo é uma chamada de API (`GET /api/...`), faça com `curl` em
   `http://localhost:8080/api/...` e guarde o status e o corpo.
3. Compare o observado com o esperado do passo e com o cenário de aceite da spec que ele
   cobre.
4. Em falha, tire um `browser_take_screenshot` com `filename` dentro de `.playwright-mcp/`
   (por exemplo `.playwright-mcp/passo-3.png`; fora do git) e anote a console do navegador
   (`browser_console_messages`) se tiver erro.

Regras do roteiro:

- Fale só com `localhost`. Não entre em serviço externo com conta real.
- O import do MangaDex no local é de verdade: importe o mínimo que o passo pede (prefira
  oneshot) e não repita em laço (docs/DEVELOPMENT.md).
- Passo que só vale em produção (seção "depois do deploy") fica como **não se aplica**.
- Passo ambíguo: siga a spec; se ainda assim não der para decidir, marque **não executado**
  e explique.

### 6. Gravar `validation.md`

Crie (ou substitua) `validation.md` na pasta da feature com exatamente esta estrutura:

```markdown
# Validação: <nome da feature>

**Data**: <AAAA-MM-DD> | **Commit**: <git rev-parse --short HEAD> | **Branch**: <branch>

**Resultado**: <APROVADA, REPROVADA ou INCOMPLETA: uma palavra só, o CI lê esta linha>

## Checagens automáticas

| Checagem | Resultado | Detalhe |
|---|---|---|
| `./mvnw clean test` | ✅ / ❌ / ⏭️ não executado | <n testes, falhas> |
| `npm run build` | ✅ / ❌ / ⏭️ não executado / — não se aplica | <resumo> |

## Roteiro do quickstart

| # | Passo | Esperado | Observado | Resultado |
|---|---|---|---|---|
| 1 | <ação> | <do quickstart> | <o que a tela ou a API mostrou> | ✅ / ❌ / ⏭️ / — |

## Falhas

<uma seção por ❌: passo, o que aconteceu, requisito da spec afetado (FR/SC), screenshot em
.playwright-mcp/ e a hipótese de causa>

## Não executados

<cada ⏭️ com o motivo>
```

- **APROVADA**: nenhuma ❌ e nenhuma ⏭️ fora dos "não se aplica".
- **REPROVADA**: ao menos uma ❌.
- **INCOMPLETA**: sem ❌, mas com ⏭️ que deveria ter rodado.

### 7. Derrubar o ambiente

`scripts/dev-env.sh down`. Ele só derruba o que o `up` subiu.

### 8. Relatar

Diga o resultado, quantos passos passaram e, para cada falha, a tarefa do `tasks.md` (ou o
requisito) que precisa voltar ao `/speckit-implement`. Com resultado APROVADA, o próximo
passo é abrir o PR com o `validation.md` junto.
