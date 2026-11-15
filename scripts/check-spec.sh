#!/usr/bin/env bash
# Portão do PR (constituição, fluxo de desenvolvimento, regra 9):
#  - PR que muda código de aplicação traz uma pasta em specs/, ou tem a label "sem-spec";
#  - toda pasta de specs/ num PR com código de aplicação tem validation.md APROVADA.
# PR do Dependabot é isento.
#
# No CI: BASE_SHA, HEAD_SHA, PR_LABELS (separadas por vírgula) e PR_AUTHOR vêm do evento.
# Local: scripts/check-spec.sh compara HEAD com origin/dev.
set -euo pipefail

BASE="${BASE_SHA:-origin/dev}"
HEAD="${HEAD_SHA:-HEAD}"
LABELS=",${PR_LABELS:-},"
AUTHOR="${PR_AUTHOR:-}"
APP_CODE='^(backend/src/main/|frontend/src/)'

ok() { printf '✅ %s\n' "$*"; exit 0; }
fail() { printf '❌ %s\n' "$*" >&2; exit 1; }

changed="$(git diff --name-only "$BASE...$HEAD")"
app_files="$(grep -E "$APP_CODE" <<<"$changed" || true)"

[[ -n "$app_files" ]] || ok "o PR não muda código de aplicação; spec não é exigida"
[[ "$AUTHOR" != "dependabot[bot]" ]] || ok "PR do Dependabot; spec não é exigida"
[[ "$LABELS" != *",sem-spec,"* ]] || ok "label sem-spec: mudança sem spec (regra 5 da constituição)"

spec_dirs="$(grep -E '^specs/[^/]+/' <<<"$changed" | cut -d/ -f1-2 | sort -u || true)"
if [[ -z "$spec_dirs" ]]; then
    fail "o PR muda código de aplicação sem pasta em specs/.
   Arquivos: $(head -n 5 <<<"$app_files" | tr '\n' ' ')
   Escreva a spec (/speckit-specify) ou, se for uma exceção da regra 5 da constituição
   (correção de uma linha, bump de dependência, só teste, texto), ponha a label sem-spec."
fi

problems=""
while IFS= read -r dir; do
    validation="$dir/validation.md"
    if ! git cat-file -e "$HEAD:$validation" 2>/dev/null; then
        problems+="   $dir: falta validation.md (rode /validar-quickstart)"$'\n'
    elif ! git show "$HEAD:$validation" | grep -qE '^\*\*Resultado\*\*: APROVADA\b'; then
        result="$(git show "$HEAD:$validation" | grep -oE '^\*\*Resultado\*\*: [A-Z]+' || echo 'sem linha de resultado')"
        problems+="   $dir: validation.md não está APROVADA ($result)"$'\n'
    fi
done <<<"$spec_dirs"

[[ -z "$problems" ]] || fail "spec sem validação aprovada:
$problems"
ok "spec e validação em dia: $(tr '\n' ' ' <<<"$spec_dirs")"
