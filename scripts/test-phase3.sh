#!/bin/bash

# config
BASE_URL="http://localhost/api"
ADMIN_EMAIL="${ADMIN_EMAIL:-reghina5511@uorak.com}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-12345678}"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
RESET='\033[0m'

PASS=0
FAIL=0

# helpers
pass() { echo -e "  ${GREEN}PASS${RESET} — $1"; PASS=$((PASS + 1)); }
fail() { echo -e "  ${RED}FAIL${RESET} — $1"; FAIL=$((FAIL + 1)); }
info() { echo -e "${YELLOW}→ $1${RESET}"; }

check_status() {
  local label=$1 expected=$2 actual=$3
  if [ "$actual" -eq "$expected" ]; then pass "$label (HTTP $actual)"
  else fail "$label (esperado $expected, recebido $actual)"; fi
}

echo -n "  Checking dependencies (curl, jq, docker) ... "
if command -v curl &>/dev/null && command -v jq &>/dev/null && command -v docker &>/dev/null; then
  echo -e "${GREEN}OK${RESET}"
else
  echo -e "${RED}MISSING — install curl, jq and docker${RESET}"
  exit 1
fi


# auth
echo ""
info "Autenticando como admin..."
LOGIN=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}")
check_status "Login admin" 200 "$LOGIN"

TOKEN=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" | jq -r '.accessToken')

if [ -z "$TOKEN" ] || [ "$TOKEN" = "null" ]; then
  fail "Token não obtido — abortando testes"
  exit 1
fi

AUTH="Authorization: Bearer $TOKEN"

# GETs públicos
echo ""
info "GETs públicos (sem autenticação)..."

STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/tag-categories")
check_status "GET /tag-categories público" 200 "$STATUS"

COUNT=$(curl -s "$BASE_URL/tag-categories" | jq 'length')
[ "$COUNT" -ge 5 ] && pass "Categorias seedadas ($COUNT encontradas)" \
                    || fail "Poucas categorias ($COUNT)"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/tags")
check_status "GET /tags público" 200 "$STATUS"

TAG_COUNT=$(curl -s "$BASE_URL/tags" | jq 'length')
[ "$TAG_COUNT" -ge 10 ] && pass "Tags seedadas ($TAG_COUNT encontradas)" \
                         || fail "Poucas tags ($TAG_COUNT)"

# GETs protegidos sem token
echo ""
info "Endpoints admin sem token devem retornar 401..."

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/tag-categories" \
  -H "Content-Type: application/json" -d '{"name":"Sem Auth"}')
check_status "POST /tag-categories sem token → 401" 401 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/tags" \
  -H "Content-Type: application/json" -d '{"name":"x","slug":"x","categoryId":"invalid"}')
check_status "POST /tags sem token → 401" 401 "$STATUS"

# criar categoria
echo ""
info "CRUD de categorias..."

CATEGORY=$(curl -s -X POST "$BASE_URL/tag-categories" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"name":"Categoria Teste Phase3"}')
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/tag-categories" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"name":"Categoria Teste Phase3 B"}')
check_status "POST /tag-categories" 201 "$STATUS"

CATEGORY_ID=$(echo "$CATEGORY" | jq -r '.id')
[ "$CATEGORY_ID" != "null" ] && [ -n "$CATEGORY_ID" ] \
  && pass "Categoria criada (id: $CATEGORY_ID)" \
  || fail "ID da categoria inválido"

# duplicata
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/tag-categories" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"name":"Categoria Teste Phase3"}')
check_status "POST /tag-categories duplicata → 409" 409 "$STATUS"

# criar tag
echo ""
info "CRUD de tags..."

TAG=$(curl -s -X POST "$BASE_URL/tags" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"name\":\"Tag Teste\",\"slug\":\"tag-teste-phase3\",\"categoryId\":\"$CATEGORY_ID\"}")
TAG_ID=$(echo "$TAG" | jq -r '.id')
STATUS=$(echo "$TAG" | jq -r 'if .id then "201" else "error" end')
[ "$TAG_ID" != "null" ] && [ -n "$TAG_ID" ] \
  && pass "POST /tags — tag criada (id: $TAG_ID)" \
  || fail "POST /tags — falhou: $(echo $TAG | jq -r '.message // .')"

# slug duplicado
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/tags" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"name\":\"Tag Teste 2\",\"slug\":\"tag-teste-phase3\",\"categoryId\":\"$CATEGORY_ID\"}")
check_status "POST /tags slug duplicado → 409" 409 "$STATUS"

# categoria inexistente
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/tags" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"name":"x","slug":"slug-unico-xyz","categoryId":"00000000-0000-0000-0000-000000000000"}')
check_status "POST /tags categoria inexistente → 404" 404 "$STATUS"

# ── editar tag ────────────────────────────────────────────────────────────────
echo ""
info "Editando tag..."

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$BASE_URL/tags/$TAG_ID" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"name\":\"Tag Teste Editada\",\"slug\":\"tag-teste-phase3-editada\",\"categoryId\":\"$CATEGORY_ID\"}")
check_status "PUT /tags/{id}" 200 "$STATUS"

UPDATED_NAME=$(curl -s "$BASE_URL/tags" | jq -r ".[] | select(.id==\"$TAG_ID\") | .name")
[ "$UPDATED_NAME" = "Tag Teste Editada" ] \
  && pass "Nome atualizado corretamente" \
  || fail "Nome não atualizado (encontrado: '$UPDATED_NAME')"

# ── soft delete ───────────────────────────────────────────────────────────────
echo ""
info "Soft delete..."

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$BASE_URL/tags/$TAG_ID" \
  -H "$AUTH")
check_status "DELETE /tags/{id} → 204" 204 "$STATUS"

FOUND=$(curl -s "$BASE_URL/tags" | jq -r ".[] | select(.id==\"$TAG_ID\") | .id")
[ -z "$FOUND" ] \
  && pass "Tag removida da listagem pública após soft delete" \
  || fail "Tag ainda aparece na listagem após soft delete"

# segundo delete deve retornar 404
STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$BASE_URL/tags/$TAG_ID" \
  -H "$AUTH")
check_status "DELETE /tags/{id} já deletado → 404" 404 "$STATUS"

# ── validações ────────────────────────────────────────────────────────────────
echo ""
info "Validações de payload..."

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/tags" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"name":"","slug":"","categoryId":null}')
check_status "POST /tags payload inválido → 400" 400 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/tag-categories" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"name":""}')
check_status "POST /tag-categories nome vazio → 400" 400 "$STATUS"

# cleanup ───────────────────────────────────────────────────────────────────
info "Limpando dados de teste..."
docker exec buruna_postgres psql -U buruna_user -d buruna -q \
  -c "DELETE FROM tags WHERE slug LIKE '%phase3%';
      DELETE FROM tag_categories WHERE name LIKE '%Phase3%';" 2>/dev/null \
  && pass "Dados de teste removidos" || fail "Erro no cleanup"

# resultado
echo ""
echo "────────────────────────────────────────"
echo -e "  ${GREEN}Passou: $PASS${RESET}  |  ${RED}Falhou: $FAIL${RESET}"
echo "────────────────────────────────────────"
[ "$FAIL" -eq 0 ] && echo -e "${GREEN}  Fase 3 backend OK${RESET}" \
                  || echo -e "${RED}  $FAIL teste(s) falharam${RESET}"
echo ""
