#!/usr/bin/env bash
# =============================================================================
# Burūna — Phase 7 Backend Test Script
# Tests all engagement endpoints (reading list + ratings) against a running backend
# Usage: ./scripts/test-phase7.sh
# Requires: curl, jq, python3, docker
# =============================================================================

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost/api}"
DB_CONTAINER="${DB_CONTAINER:-buruna_postgres}"
DB_USER="${DB_USER:-buruna_user}"
DB_NAME="${DB_NAME:-buruna}"
ADMIN_EMAIL="${ADMIN_EMAIL:-reghina5511@uorak.com}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-12345678}"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
RESET='\033[0m'

PASS=0
FAIL=0

pass()    { echo -e "  ${GREEN}PASS${RESET} — $1"; PASS=$((PASS + 1)); }
fail()    { echo -e "  ${RED}FAIL${RESET} — $1"; FAIL=$((FAIL + 1)); }
section() { echo -e "\n${CYAN}${BOLD}▶ $1${RESET}"; }
info()    { echo -e "  ${YELLOW}→ $1${RESET}"; }

check_status() {
  local label="$1" expected="$2" actual="$3"
  if [ "$actual" -eq "$expected" ]; then
    pass "$label (HTTP $actual)"
  else
    fail "$label — esperado HTTP $expected, recebido HTTP $actual"
  fi
}

http_get() {
  local url="$1" token="${2:-}"
  curl -s -w "\n%{http_code}" "$BASE_URL$url" \
    ${token:+-H "Authorization: Bearer $token"}
}

http_post() {
  local url="$1" body="$2" token="${3:-}"
  curl -s -w "\n%{http_code}" -X POST "$BASE_URL$url" \
    -H "Content-Type: application/json" \
    ${token:+-H "Authorization: Bearer $token"} \
    -d "$body"
}

http_put() {
  local url="$1" body="$2" token="${3:-}"
  curl -s -w "\n%{http_code}" -X PUT "$BASE_URL$url" \
    -H "Content-Type: application/json" \
    ${token:+-H "Authorization: Bearer $token"} \
    -d "$body"
}

http_delete() {
  local url="$1" token="${2:-}"
  curl -s -w "\n%{http_code}" -X DELETE "$BASE_URL$url" \
    ${token:+-H "Authorization: Bearer $token"}
}

http_upload() {
  local url="$1" file="$2" token="$3" extra_fields="${4:-}"
  curl -s -w "\n%{http_code}" -X POST "$BASE_URL$url" \
    -H "Authorization: Bearer $token" \
    -F "file=@$file;type=application/pdf" \
    $extra_fields
}

parse_body()   { echo "$1" | head -n -1; }
parse_status() { echo "$1" | tail -n 1; }
parse_json()   {
  echo "$1" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('$2',''))" 2>/dev/null || echo ""
}


# pre-flight
section "Pre-flight"

echo -n "  Dependências (curl, jq, python3) ... "
command -v curl &>/dev/null && command -v jq &>/dev/null && command -v python3 &>/dev/null \
  && echo -e "${GREEN}OK${RESET}" || { echo -e "${RED}FALTANDO${RESET}"; exit 1; }

echo -n "  Backend em $BASE_URL ... "
curl -s --max-time 5 -o /dev/null "$BASE_URL/auth/login" -X POST \
  -H "Content-Type: application/json" -d '{}' \
  && echo -e "${GREEN}UP${RESET}" || { echo -e "${RED}DOWN${RESET}"; exit 1; }

echo -n "  DB container '$DB_CONTAINER' ... "
docker ps --format '{{.Names}}' | grep -q "^${DB_CONTAINER}$" \
  && echo -e "${GREEN}RUNNING${RESET}" || { echo -e "${RED}NOT FOUND${RESET}"; exit 1; }


# cleanup anterior
section "Cleanup (dados de teste anteriores)"

docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -q -c "
  DELETE FROM ratings       WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%phase7%');
  DELETE FROM reading_list  WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%phase7%');
  DELETE FROM reading_history WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%phase7%');
  DELETE FROM reading_progress WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%phase7%');
  DELETE FROM volumes       WHERE manga_id IN (SELECT id FROM mangas WHERE slug LIKE 'phase7%');
  DELETE FROM mangas        WHERE slug LIKE 'phase7%';
  DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%phase7%');
  DELETE FROM users         WHERE email LIKE '%phase7%';
" && pass "Dados anteriores removidos"


# setup — auth e dados
section "Setup — autenticação e dados"

LOGIN_ADMIN=$(http_post "/auth/login" \
  "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}")
check_status "Login admin" 200 "$(parse_status "$LOGIN_ADMIN")"
ADMIN_TOKEN=$(parse_json "$(parse_body "$LOGIN_ADMIN")" "accessToken")
[ -z "$ADMIN_TOKEN" ] && { fail "Token admin não obtido — abortando"; exit 1; }

BCRYPT_HASH='$2a$10$aGw6owR1pcMYQfdZvSWDTeglPDHItLt7DUt9cCmxHMyXCntVPdmRC'

docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -q -c "
  INSERT INTO users (id, email, username, password_hash, presentation_message,
                     role, status, quota_gb, created_at, updated_at)
  VALUES
    (gen_random_uuid(), 'reader7a@phase7.test', 'phase7_reader_a', '$BCRYPT_HASH', 'test', 'READER', 'ACTIVE', 1.00, NOW(), NOW()),
    (gen_random_uuid(), 'reader7b@phase7.test', 'phase7_reader_b', '$BCRYPT_HASH', 'test', 'READER', 'ACTIVE', 1.00, NOW(), NOW());
" && info "Usuários de teste criados via SQL"

LOGIN_A=$(http_post "/auth/login" '{"email":"reader7a@phase7.test","password":"Test@123456"}')
check_status "Login reader A" 200 "$(parse_status "$LOGIN_A")"
TOKEN_A=$(parse_json "$(parse_body "$LOGIN_A")" "accessToken")

LOGIN_B=$(http_post "/auth/login" '{"email":"reader7b@phase7.test","password":"Test@123456"}')
check_status "Login reader B" 200 "$(parse_status "$LOGIN_B")"
TOKEN_B=$(parse_json "$(parse_body "$LOGIN_B")" "accessToken")

# dois mangás públicos para os testes
MANGA1_RES=$(http_post "/mangas" \
  '{"title":"Phase7 Manga Alpha","alternativeTitles":[],"synopsis":"test","format":"MANGA","statusOrigin":"ONGOING","statusSite":"INCOMPLETE","contentWarnings":[],"tagIds":[]}' \
  "$ADMIN_TOKEN")
check_status "Criar mangá 1" 201 "$(parse_status "$MANGA1_RES")"
MANGA1_ID=$(parse_body "$MANGA1_RES" | jq -r '.id' 2>/dev/null || echo "")
info "Mangá 1 ID: $MANGA1_ID"

MANGA2_RES=$(http_post "/mangas" \
  '{"title":"Phase7 Manga Beta","alternativeTitles":[],"synopsis":"test","format":"MANHWA","statusOrigin":"COMPLETED","statusSite":"COMPLETE","contentWarnings":[],"tagIds":[]}' \
  "$ADMIN_TOKEN")
check_status "Criar mangá 2" 201 "$(parse_status "$MANGA2_RES")"
MANGA2_ID=$(parse_body "$MANGA2_RES" | jq -r '.id' 2>/dev/null || echo "")
info "Mangá 2 ID: $MANGA2_ID"


# 1. lista de Leitura — GET /reading-list
section "1. GET /reading-list"

RES=$(http_get "/reading-list")
check_status "GET /reading-list sem token → 401" 401 "$(parse_status "$RES")"

RES=$(http_get "/reading-list" "$TOKEN_A")
check_status "GET /reading-list lista vazia → 200" 200 "$(parse_status "$RES")"
COUNT=$(parse_body "$RES" | jq 'length' 2>/dev/null || echo "-1")
[ "$COUNT" -eq 0 ] \
  && pass "Lista vazia retorna array [] (length=0)" \
  || fail "Esperado array vazio, recebido length=$COUNT"


# 2. lista de Leitura — PUT /reading-list/{mangaId}
section "2. PUT /reading-list/{mangaId}"

RES=$(http_put "/reading-list/$MANGA1_ID" '{"status":"WANT_TO_READ"}')
check_status "PUT /reading-list sem token → 401" 401 "$(parse_status "$RES")"

RES=$(http_put "/reading-list/$MANGA1_ID" '{"status":"WANT_TO_READ"}' "$TOKEN_A")
check_status "PUT reading-list (inserir) → 200" 200 "$(parse_status "$RES")"
STATUS_FIELD=$(parse_body "$RES" | jq -r '.status' 2>/dev/null || echo "")
[ "$STATUS_FIELD" = "WANT_TO_READ" ] \
  && pass "Status WANT_TO_READ retornado corretamente" \
  || fail "Status incorreto — esperado WANT_TO_READ, recebido '$STATUS_FIELD'"

MANGA_ID_FIELD=$(parse_body "$RES" | jq -r '.mangaId' 2>/dev/null || echo "")
[ "$MANGA_ID_FIELD" = "$MANGA1_ID" ] \
  && pass "mangaId correto no response" \
  || fail "mangaId incorreto — esperado $MANGA1_ID, recebido '$MANGA_ID_FIELD'"

# atualiza status (upsert)
RES=$(http_put "/reading-list/$MANGA1_ID" '{"status":"READING"}' "$TOKEN_A")
check_status "PUT reading-list (atualizar status) → 200" 200 "$(parse_status "$RES")"
UPDATED_STATUS=$(parse_body "$RES" | jq -r '.status' 2>/dev/null || echo "")
[ "$UPDATED_STATUS" = "READING" ] \
  && pass "Upsert atualiza status corretamente (READING)" \
  || fail "Upsert falhou — esperado READING, recebido '$UPDATED_STATUS'"

# status inválido → 400
RES=$(http_put "/reading-list/$MANGA1_ID" '{"status":"INVALID_STATUS"}' "$TOKEN_A")
check_status "PUT status inválido → 400" 400 "$(parse_status "$RES")"

# status null → 400
RES=$(http_put "/reading-list/$MANGA1_ID" '{}' "$TOKEN_A")
check_status "PUT status null → 400" 400 "$(parse_status "$RES")"

# mangá inexistente → 404
RES=$(http_put "/reading-list/00000000-0000-0000-0000-000000000000" '{"status":"READING"}' "$TOKEN_A")
check_status "PUT mangá inexistente → 404" 404 "$(parse_status "$RES")"

# adiciona segundo mangá
RES=$(http_put "/reading-list/$MANGA2_ID" '{"status":"COMPLETED"}' "$TOKEN_A")
check_status "PUT segundo mangá → 200" 200 "$(parse_status "$RES")"

# reader B adiciona de forma independente
RES=$(http_put "/reading-list/$MANGA1_ID" '{"status":"DROPPED"}' "$TOKEN_B")
check_status "PUT reader B → 200 (isolamento)" 200 "$(parse_status "$RES")"


# 3. lista de Leitura — GET /reading-list (com itens)
section "3. GET /reading-list (com itens)"

RES=$(http_get "/reading-list" "$TOKEN_A")
check_status "GET /reading-list com itens → 200" 200 "$(parse_status "$RES")"
LIST_COUNT=$(parse_body "$RES" | jq 'length' 2>/dev/null || echo "0")
[ "$LIST_COUNT" -eq 2 ] \
  && pass "Reader A tem 2 itens na lista" \
  || fail "Esperado 2 itens, recebido $LIST_COUNT"

# valida campos obrigatórios
ITEM=$(parse_body "$RES" | jq '.[0]' 2>/dev/null || echo "{}")
F_MANGA_ID=$(echo "$ITEM" | jq -r '.mangaId' 2>/dev/null || echo "")
F_TITLE=$(echo "$ITEM" | jq -r '.mangaTitle' 2>/dev/null || echo "")
F_STATUS=$(echo "$ITEM" | jq -r '.status' 2>/dev/null || echo "")
F_UPDATED=$(echo "$ITEM" | jq -r '.updatedAt' 2>/dev/null || echo "")
[ -n "$F_MANGA_ID" ] && [ -n "$F_TITLE" ] && [ -n "$F_STATUS" ] && [ -n "$F_UPDATED" ] \
  && pass "Campos mangaId, mangaTitle, status, updatedAt presentes" \
  || fail "Campos ausentes (mangaId='$F_MANGA_ID', mangaTitle='$F_TITLE', status='$F_STATUS', updatedAt='$F_UPDATED')"

# isolamento por usuário
RES_B=$(http_get "/reading-list" "$TOKEN_B")
LIST_B=$(parse_body "$RES_B" | jq 'length' 2>/dev/null || echo "0")
[ "$LIST_B" -eq 1 ] \
  && pass "Reader B tem 1 item (isolamento correto)" \
  || fail "Isolamento falhou — reader B tem $LIST_B itens (esperado 1)"


# 4. lista de Leitura — DELETE /reading-list/{mangaId}
section "4. DELETE /reading-list/{mangaId}"

RES=$(http_delete "/reading-list/$MANGA2_ID")
check_status "DELETE /reading-list sem token → 401" 401 "$(parse_status "$RES")"

RES=$(http_delete "/reading-list/$MANGA2_ID" "$TOKEN_A")
check_status "DELETE item existente → 204" 204 "$(parse_status "$RES")"

# verifica remoção
RES=$(http_get "/reading-list" "$TOKEN_A")
LIST_AFTER=$(parse_body "$RES" | jq 'length' 2>/dev/null || echo "0")
[ "$LIST_AFTER" -eq 1 ] \
  && pass "Lista reduzida para 1 após delete" \
  || fail "Lista incorreta após delete — esperado 1, recebido $LIST_AFTER"

# delete de item inexistente → 404
RES=$(http_delete "/reading-list/$MANGA2_ID" "$TOKEN_A")
check_status "DELETE item já removido → 404" 404 "$(parse_status "$RES")"

# delete de mangá inexistente → 404
RES=$(http_delete "/reading-list/00000000-0000-0000-0000-000000000000" "$TOKEN_A")
check_status "DELETE mangá inexistente → 404" 404 "$(parse_status "$RES")"


# 5. avaliações — POST /mangas/{id}/rating
section "5. POST /mangas/{mangaId}/rating"

RES=$(http_post "/mangas/$MANGA1_ID/rating" '{"score":4}')
check_status "POST /rating sem token → 401" 401 "$(parse_status "$RES")"

RES=$(http_post "/mangas/$MANGA1_ID/rating" '{"score":4}' "$TOKEN_A")
check_status "POST rating (score 4) → 201" 201 "$(parse_status "$RES")"
SCORE=$(parse_body "$RES" | jq -r '.score' 2>/dev/null || echo "0")
[ "$SCORE" -eq 4 ] \
  && pass "Score 4 retornado corretamente" \
  || fail "Score incorreto — esperado 4, recebido $SCORE"

AVG=$(parse_body "$RES" | jq -r '.avgRating' 2>/dev/null || echo "0")
COUNT=$(parse_body "$RES" | jq -r '.ratingCount' 2>/dev/null || echo "0")
[ "$COUNT" -eq 1 ] \
  && pass "ratingCount=1 após primeira avaliação" \
  || fail "ratingCount incorreto — esperado 1, recebido $COUNT"
info "avgRating após 1 voto: $AVG"

# score inválido: 0 → 400
RES=$(http_post "/mangas/$MANGA1_ID/rating" '{"score":0}' "$TOKEN_B")
check_status "POST score 0 → 400" 400 "$(parse_status "$RES")"

# score inválido: 6 → 400
RES=$(http_post "/mangas/$MANGA1_ID/rating" '{"score":6}' "$TOKEN_B")
check_status "POST score 6 → 400" 400 "$(parse_status "$RES")"

# segundo usuário avalia
RES=$(http_post "/mangas/$MANGA1_ID/rating" '{"score":2}' "$TOKEN_B")
check_status "POST rating reader B (score 2) → 201" 201 "$(parse_status "$RES")"
AVG2=$(parse_body "$RES" | jq -r '.avgRating' 2>/dev/null || echo "0")
COUNT2=$(parse_body "$RES" | jq -r '.ratingCount' 2>/dev/null || echo "0")
[ "$COUNT2" -eq 2 ] \
  && pass "ratingCount=2 após segunda avaliação" \
  || fail "ratingCount incorreto — esperado 2, recebido $COUNT2"
# avg de (4+2)/2 = 3.0
AVG2_INT=$(echo "$AVG2" | python3 -c "import sys; print(int(float(sys.stdin.read().strip())))")
[ "$AVG2_INT" -eq 3 ] \
  && pass "avgRating calculado corretamente ((4+2)/2 = $AVG2)" \
  || fail "avgRating incorreto — esperado 3.0, recebido $AVG2"

# avaliação duplicada → 409
RES=$(http_post "/mangas/$MANGA1_ID/rating" '{"score":5}' "$TOKEN_A")
check_status "POST rating duplicado → 409" 409 "$(parse_status "$RES")"

# mangá inexistente → 404
RES=$(http_post "/mangas/00000000-0000-0000-0000-000000000000/rating" '{"score":3}' "$TOKEN_A")
check_status "POST rating mangá inexistente → 404" 404 "$(parse_status "$RES")"


# 6. avaliações — PUT /mangas/{id}/rating
section "6. PUT /mangas/{mangaId}/rating"

RES=$(http_put "/mangas/$MANGA1_ID/rating" '{"score":5}')
check_status "PUT /rating sem token → 401" 401 "$(parse_status "$RES")"

RES=$(http_put "/mangas/$MANGA1_ID/rating" '{"score":5}' "$TOKEN_A")
check_status "PUT rating (score 5) → 200" 200 "$(parse_status "$RES")"
UPDATED_SCORE=$(parse_body "$RES" | jq -r '.score' 2>/dev/null || echo "0")
[ "$UPDATED_SCORE" -eq 5 ] \
  && pass "Score atualizado para 5" \
  || fail "Score incorreto após update — esperado 5, recebido $UPDATED_SCORE"

# avg deve ter recalculado: (5+2)/2 = 3.5
AVG3=$(parse_body "$RES" | jq -r '.avgRating' 2>/dev/null || echo "0")
AVG3_FLOAT=$(echo "$AVG3" | python3 -c "import sys; print(float(sys.stdin.read().strip()))")
EXPECTED="3.5"
[ "$AVG3_FLOAT" = "$EXPECTED" ] \
  && pass "avgRating recalculado após update ((5+2)/2 = $AVG3)" \
  || fail "avgRating incorreto após update — esperado 3.5, recebido $AVG3"

# PUT em avaliação inexistente → 404
RES=$(http_put "/mangas/$MANGA2_ID/rating" '{"score":3}' "$TOKEN_A")
check_status "PUT rating inexistente → 404" 404 "$(parse_status "$RES")"

# score inválido → 400
RES=$(http_put "/mangas/$MANGA1_ID/rating" '{"score":0}' "$TOKEN_A")
check_status "PUT score inválido (0) → 400" 400 "$(parse_status "$RES")"


# 7. avaliações — DELETE /mangas/{id}/rating
section "7. DELETE /mangas/{mangaId}/rating"

RES=$(http_delete "/mangas/$MANGA1_ID/rating")
check_status "DELETE /rating sem token → 401" 401 "$(parse_status "$RES")"

# verifica avg antes do delete (reader B ainda tem score 2)
RES=$(http_delete "/mangas/$MANGA1_ID/rating" "$TOKEN_A")
check_status "DELETE rating reader A → 204" 204 "$(parse_status "$RES")"

# verifica recalculo: só reader B com score 2 → avg=2.0, count=1
RES=$(http_get "/mangas/phase7-manga-alpha" "$TOKEN_A")
check_status "GET mangá após delete rating → 200" 200 "$(parse_status "$RES")"
AVG_AFTER=$(parse_body "$RES" | jq -r '.avgRating' 2>/dev/null || echo "0")
COUNT_AFTER=$(parse_body "$RES" | jq -r '.ratingCount' 2>/dev/null || echo "0")
[ "$COUNT_AFTER" -eq 1 ] \
  && pass "ratingCount=1 após delete (reader B ainda tem avaliação)" \
  || fail "ratingCount incorreto após delete — esperado 1, recebido $COUNT_AFTER"
AVG_AFTER_INT=$(echo "$AVG_AFTER" | python3 -c "import sys; print(int(float(sys.stdin.read().strip())))")
[ "$AVG_AFTER_INT" -eq 2 ] \
  && pass "avgRating=2.0 após delete do reader A" \
  || fail "avgRating incorreto após delete — esperado 2.0, recebido $AVG_AFTER"

# delete inexistente → 404
RES=$(http_delete "/mangas/$MANGA1_ID/rating" "$TOKEN_A")
check_status "DELETE rating já removido → 404" 404 "$(parse_status "$RES")"

# delete último rating: avg e count voltam a zero
RES=$(http_delete "/mangas/$MANGA1_ID/rating" "$TOKEN_B")
check_status "DELETE último rating → 204" 204 "$(parse_status "$RES")"

RES=$(http_get "/mangas/phase7-manga-alpha" "$TOKEN_A")
COUNT_ZERO=$(parse_body "$RES" | jq -r '.ratingCount' 2>/dev/null || echo "-1")
AVG_ZERO=$(parse_body "$RES" | jq -r '.avgRating' 2>/dev/null || echo "-1")
[ "$COUNT_ZERO" -eq 0 ] \
  && pass "ratingCount=0 após remover todos os votos" \
  || fail "ratingCount incorreto — esperado 0, recebido $COUNT_ZERO"
AVG_ZERO_FLOAT=$(echo "$AVG_ZERO" | python3 -c "import sys; print(float(sys.stdin.read().strip()))")
[ "$AVG_ZERO_FLOAT" = "0.0" ] \
  && pass "avgRating=0.0 após remover todos os votos" \
  || fail "avgRating incorreto — esperado 0.0, recebido $AVG_ZERO"


# cleanup
section "Cleanup"

docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -q -c "
  DELETE FROM ratings       WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%phase7%');
  DELETE FROM reading_list  WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%phase7%');
  DELETE FROM reading_history WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%phase7%');
  DELETE FROM reading_progress WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%phase7%');
  DELETE FROM volumes       WHERE manga_id IN (SELECT id FROM mangas WHERE slug LIKE 'phase7%');
  DELETE FROM mangas        WHERE slug LIKE 'phase7%';
  DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%phase7%');
  DELETE FROM users         WHERE email LIKE '%phase7%';
" && pass "Dados de teste removidos"


# Resultado
TOTAL=$((PASS + FAIL))
echo -e "\n${BOLD}════════════════════════════════════════${RESET}"
echo -e "${BOLD}  Resultados: $TOTAL testes — ${GREEN}$PASS OK${RESET}${BOLD} — ${RED}$FAIL falhou${RESET}"
echo -e "${BOLD}════════════════════════════════════════${RESET}"
[ "$FAIL" -eq 0 ] \
  && echo -e "${GREEN}  Fase 7 backend OK${RESET}\n" \
  || echo -e "${RED}  $FAIL teste(s) falharam${RESET}\n"

[ "$FAIL" -eq 0 ] && exit 0 || exit 1
