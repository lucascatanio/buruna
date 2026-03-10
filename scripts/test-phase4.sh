#!/usr/bin/env bash
# =============================================================================
# Burūna — Phase 4 Backend Test Script
# Tests all manga + volume endpoints against a running backend
# Usage: ./scripts/test-phase4.sh
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
  local label="$1" expected="$2" actual="$3" body="${4:-}"
  if [ "$actual" -eq "$expected" ]; then
    pass "$label (HTTP $actual)"
  else
    fail "$label — esperado HTTP $expected, recebido HTTP $actual"
    [ -n "$body" ] && echo -e "    ${YELLOW}Body: $body${RESET}"
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
  local url="$1" body="$2" token="$3"
  curl -s -w "\n%{http_code}" -X PUT "$BASE_URL$url" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $token" \
    -d "$body"
}

http_delete() {
  local url="$1" token="$2"
  curl -s -w "\n%{http_code}" -X DELETE "$BASE_URL$url" \
    -H "Authorization: Bearer $token"
}

http_upload() {
  local url="$1" file="$2" volume_number="$3" token="$4"
  curl -s -w "\n%{http_code}" -X POST "$BASE_URL$url" \
    -H "Authorization: Bearer $token" \
    -F "file=@$file;type=application/pdf" \
    -F "volumeNumber=$volume_number"
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


# cleanup
section "Cleanup (dados de teste anteriores)"

docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -q -c "
  DELETE FROM volumes      WHERE file_hash LIKE 'phase4test%';
  DELETE FROM manga_tags   WHERE manga_id IN (SELECT id FROM mangas WHERE slug LIKE 'phase4%');
  DELETE FROM mangas       WHERE slug LIKE 'phase4%';
  DELETE FROM refresh_tokens WHERE user_id IN (
    SELECT id FROM users WHERE email IN ('collab@buruna.test','reader2@buruna.test')
  );
  DELETE FROM users WHERE email IN ('collab@buruna.test','reader2@buruna.test');
" && pass "Dados anteriores removidos"


# setup tokens
section "Setup — autenticação"

LOGIN_ADMIN=$(http_post "/auth/login" \
  "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}")
check_status "Login admin" 200 "$(parse_status "$LOGIN_ADMIN")" "$(parse_body "$LOGIN_ADMIN")"
ADMIN_TOKEN=$(parse_json "$(parse_body "$LOGIN_ADMIN")" "accessToken")
[ -z "$ADMIN_TOKEN" ] && { fail "Token admin não obtido — abortando"; exit 1; }

REG_COLLAB=$(http_post "/auth/register" '{
  "email": "collab@buruna.test",
  "username": "phase4_collab",
  "password": "Collab@123456",
  "presentationMessage": "test collaborator phase4"
}')
check_status "Register collaborator" 201 "$(parse_status "$REG_COLLAB")" "$(parse_body "$REG_COLLAB")"

COLLAB_ID=$(docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -t -c \
  "SELECT id FROM users WHERE email = 'collab@buruna.test';" | tr -d ' \n')

docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -q -c \
  "UPDATE users SET role = 'COLLABORATOR', status = 'ACTIVE' WHERE id = '$COLLAB_ID';" \
  && info "Collaborator promovido via SQL (id: $COLLAB_ID)"

LOGIN_COLLAB=$(http_post "/auth/login" \
  '{"email":"collab@buruna.test","password":"Collab@123456"}')
check_status "Login collaborator" 200 "$(parse_status "$LOGIN_COLLAB")" "$(parse_body "$LOGIN_COLLAB")"
COLLAB_TOKEN=$(parse_json "$(parse_body "$LOGIN_COLLAB")" "accessToken")

REG_READER=$(http_post "/auth/register" '{
  "email": "reader2@buruna.test",
  "username": "phase4_reader",
  "password": "Reader@123456",
  "presentationMessage": "test reader phase4"
}')
check_status "Register reader" 201 "$(parse_status "$REG_READER")" "$(parse_body "$REG_READER")"

READER_ID=$(docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -t -c \
  "SELECT id FROM users WHERE email = 'reader2@buruna.test';" | tr -d ' \n')

docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -q -c \
  "UPDATE users SET status = 'ACTIVE' WHERE id = '$READER_ID';" > /dev/null

LOGIN_READER=$(http_post "/auth/login" \
  '{"email":"reader2@buruna.test","password":"Reader@123456"}')
check_status "Login reader" 200 "$(parse_status "$LOGIN_READER")" "$(parse_body "$LOGIN_READER")"
READER_TOKEN=$(parse_json "$(parse_body "$LOGIN_READER")" "accessToken")


# 1. GET /mangas — autenticação obrigatória
section "1. GET /mangas (autenticação obrigatória)"

RES=$(http_get "/mangas")
check_status "GET /mangas sem token → 401" 401 "$(parse_status "$RES")"

RES=$(http_get "/mangas" "$COLLAB_TOKEN")
check_status "GET /mangas autenticado → 200" 200 "$(parse_status "$RES")"

CONTENT=$(parse_body "$RES" | jq -r '.content' 2>/dev/null || echo "null")
[ "$CONTENT" != "null" ] \
  && pass "Resposta tem campo 'content' (paginação correta)" \
  || fail "Resposta sem campo 'content' — paginação quebrada"


# 2. POST /mangas — criar mangá
section "2. POST /mangas"

TAG_ID=$(docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -t -c \
  "SELECT id FROM tags WHERE deleted_at IS NULL LIMIT 1;" | tr -d ' \n')
info "Tag real do banco: $TAG_ID"

RES=$(http_post "/mangas" '{
  "title":"Phase4 Sem Permissao",
  "format":"MANGA",
  "statusOrigin":"ONGOING",
  "statusSite":"INCOMPLETE"
}' "$READER_TOKEN")
check_status "POST /mangas como READER → 403" 403 "$(parse_status "$RES")"

RES=$(http_post "/mangas" '{
  "title":"Phase4 Sem Token",
  "format":"MANGA",
  "statusOrigin":"ONGOING",
  "statusSite":"INCOMPLETE"
}')
check_status "POST /mangas sem token → 401" 401 "$(parse_status "$RES")"

RES=$(http_post "/mangas" '{
  "title":"",
  "format":"MANGA",
  "statusOrigin":"ONGOING",
  "statusSite":"INCOMPLETE"
}' "$COLLAB_TOKEN")
check_status "POST /mangas título vazio → 400" 400 "$(parse_status "$RES")"

CREATE=$(http_post "/mangas" "{
  \"title\": \"Phase4 Manga Teste\",
  \"alternativeTitles\": [\"Phase4 Alt Title\"],
  \"synopsis\": \"Sinopse de teste da fase 4\",
  \"format\": \"MANGA\",
  \"originCountry\": \"Japan\",
  \"statusOrigin\": \"ONGOING\",
  \"statusSite\": \"INCOMPLETE\",
  \"year\": 2020,
  \"contentWarnings\": [],
  \"tagIds\": [\"$TAG_ID\"]
}" "$COLLAB_TOKEN")
check_status "POST /mangas como COLLABORATOR → 201" 201 "$(parse_status "$CREATE")" "$(parse_body "$CREATE")"

MANGA_BODY=$(parse_body "$CREATE")
MANGA_ID=$(echo "$MANGA_BODY"   | jq -r '.id'   2>/dev/null || echo "")
MANGA_SLUG=$(echo "$MANGA_BODY" | jq -r '.slug' 2>/dev/null || echo "")

[ -n "$MANGA_ID" ] && [ "$MANGA_ID" != "null" ] \
  && pass "Mangá criado (id: $MANGA_ID, slug: $MANGA_SLUG)" \
  || fail "ID do mangá inválido — $(parse_body "$CREATE")"

CREATE_ADMIN=$(http_post "/mangas" '{
  "title": "Phase4 Manga Admin",
  "format": "MANHWA",
  "statusOrigin": "COMPLETED",
  "statusSite": "COMPLETE"
}' "$ADMIN_TOKEN")
check_status "POST /mangas como ADMIN → 201" 201 "$(parse_status "$CREATE_ADMIN")"
MANGA_ADMIN_ID=$(parse_body "$CREATE_ADMIN" | jq -r '.id' 2>/dev/null || echo "")

DUP=$(http_post "/mangas" '{
  "title": "Phase4 Manga Teste",
  "format": "MANGA",
  "statusOrigin": "ONGOING",
  "statusSite": "INCOMPLETE"
}' "$COLLAB_TOKEN")
check_status "POST /mangas título duplicado → 409" 409 "$(parse_status "$DUP")"


# 3. GET /mangas e /mangas/{slug}
section "3. GET /mangas + /mangas/{slug}"

RES=$(http_get "/mangas?title=Phase4" "$COLLAB_TOKEN")
check_status "GET /mangas?title=Phase4 → 200" 200 "$(parse_status "$RES")"
COUNT=$(parse_body "$RES" | jq -r '.totalElements' 2>/dev/null || echo "0")
[ "$COUNT" -ge 1 ] \
  && pass "Filtro por título retornou $COUNT resultado(s)" \
  || fail "Filtro por título não retornou resultados"

RES=$(http_get "/mangas?format=MANHWA" "$COLLAB_TOKEN")
check_status "GET /mangas?format=MANHWA → 200" 200 "$(parse_status "$RES")"

RES=$(http_get "/mangas/$MANGA_SLUG" "$COLLAB_TOKEN")
check_status "GET /mangas/$MANGA_SLUG → 200" 200 "$(parse_status "$RES")"
SLUG_CHECK=$(parse_body "$RES" | jq -r '.slug' 2>/dev/null || echo "")
[ "$SLUG_CHECK" = "$MANGA_SLUG" ] \
  && pass "Slug bate no detalhe" \
  || fail "Slug incorreto na resposta (esperado: $MANGA_SLUG, recebido: $SLUG_CHECK)"

VOLUMES_FIELD=$(parse_body "$RES" | jq -r '.volumes' 2>/dev/null || echo "null")
[ "$VOLUMES_FIELD" != "null" ] \
  && pass "Campo 'volumes' presente no detalhe" \
  || fail "Campo 'volumes' ausente no detalhe"

RES=$(http_get "/mangas/slug-que-nao-existe-xyz" "$COLLAB_TOKEN")
check_status "GET /mangas/slug-inexistente → 404" 404 "$(parse_status "$RES")"

RES=$(http_get "/mangas/$MANGA_SLUG")
check_status "GET /mangas/:slug sem token → 401" 401 "$(parse_status "$RES")"


# 4. PUT /mangas/{id} — edição
section "4. PUT /mangas/{id}"

UPDATE=$(http_put "/mangas/$MANGA_ID" '{
  "title": "Phase4 Manga Teste Editado",
  "format": "MANGA",
  "statusOrigin": "HIATUS",
  "statusSite": "INCOMPLETE"
}' "$COLLAB_TOKEN")
check_status "PUT /mangas/:id como owner → 200" 200 "$(parse_status "$UPDATE")"

UPDATED_TITLE=$(parse_body "$UPDATE" | jq -r '.title' 2>/dev/null || echo "")
[ "$UPDATED_TITLE" = "Phase4 Manga Teste Editado" ] \
  && pass "Título atualizado corretamente" \
  || fail "Título não atualizado (recebido: '$UPDATED_TITLE')"

UPDATE_ADMIN=$(http_put "/mangas/$MANGA_ID" '{
  "title": "Phase4 Manga Editado pelo Admin",
  "format": "MANGA",
  "statusOrigin": "ONGOING",
  "statusSite": "INCOMPLETE"
}' "$ADMIN_TOKEN")
check_status "PUT /mangas/:id como ADMIN (não owner) → 200" 200 "$(parse_status "$UPDATE_ADMIN")"

RES=$(http_put "/mangas/$MANGA_ID" '{
  "title": "Invadindo",
  "format": "MANGA",
  "statusOrigin": "ONGOING",
  "statusSite": "INCOMPLETE"
}' "$READER_TOKEN")
check_status "PUT /mangas/:id como READER → 403" 403 "$(parse_status "$RES")"

RES=$(http_put "/mangas/00000000-0000-0000-0000-000000000000" '{
  "title": "Fantasma",
  "format": "MANGA",
  "statusOrigin": "ONGOING",
  "statusSite": "INCOMPLETE"
}' "$ADMIN_TOKEN")
check_status "PUT /mangas/id-inexistente → 404" 404 "$(parse_status "$RES")"


# 5. upload de volume — POST /mangas/{id}/volumes
section "5. POST /mangas/{id}/volumes (upload)"

PDF_FILE=$(mktemp /tmp/phase4_test_XXXXXX.pdf)
printf '%%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj 2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj 3 0 obj<</Type/Page/MediaBox[0 0 612 792]/Parent 2 0 R>>endobj\nxref\n0 4\n0000000000 65535 f\n0000000009 00000 n\n0000000058 00000 n\n0000000115 00000 n\ntrailer<</Size 4/Root 1 0 R>>\nstartxref\n190\n%%%%EOF' > "$PDF_FILE"
info "PDF de teste criado em $PDF_FILE"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/mangas/$MANGA_ID/volumes" \
  -F "file=@$PDF_FILE;type=application/pdf" -F "volumeNumber=1")
check_status "POST volumes sem token → 401" 401 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/mangas/$MANGA_ID/volumes" \
  -H "Authorization: Bearer $READER_TOKEN" \
  -F "file=@$PDF_FILE;type=application/pdf" -F "volumeNumber=1")
check_status "POST volumes como READER → 403" 403 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
  "$BASE_URL/mangas/00000000-0000-0000-0000-000000000000/volumes" \
  -H "Authorization: Bearer $COLLAB_TOKEN" \
  -F "file=@$PDF_FILE;type=application/pdf" -F "volumeNumber=1")
check_status "POST volumes manga inexistente → 404" 404 "$STATUS"

info "Tentando upload real para o GCS..."
UPLOAD_RES=$(http_upload "/mangas/$MANGA_ID/volumes" "$PDF_FILE" "1" "$COLLAB_TOKEN")
UPLOAD_STATUS=$(parse_status "$UPLOAD_RES")

if [ "$UPLOAD_STATUS" -eq 201 ]; then
  pass "POST /mangas/:id/volumes → 201 (GCS OK)"
  VOLUME_ID=$(parse_body "$UPLOAD_RES" | jq -r '.id' 2>/dev/null || echo "")
  info "Volume ID: $VOLUME_ID"

  DUP_HASH=$(http_upload "/mangas/$MANGA_ID/volumes" "$PDF_FILE" "2" "$COLLAB_TOKEN")
  check_status "POST volumes hash duplicado → 409" 409 "$(parse_status "$DUP_HASH")"

  PDF2=$(mktemp /tmp/phase4_test2_XXXXXX.pdf)
  printf '%%PDF-1.4\n%% volume 2 diferente\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj 2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj 3 0 obj<</Type/Page/MediaBox[0 0 595 842]/Parent 2 0 R>>endobj\nxref\n0 4\n0000000000 65535 f\n0000000024 00000 n\n0000000073 00000 n\n0000000130 00000 n\ntrailer<</Size 4/Root 1 0 R>>\nstartxref\n205\n%%%%EOF' > "$PDF2"
  DUP_NUM=$(http_upload "/mangas/$MANGA_ID/volumes" "$PDF2" "1" "$COLLAB_TOKEN")
  check_status "POST volumes número duplicado → 409" 409 "$(parse_status "$DUP_NUM")"
  rm -f "$PDF2"

  RES=$(http_get "/mangas/$MANGA_ID/volumes" "$COLLAB_TOKEN")
  check_status "GET /mangas/:id/volumes → 200" 200 "$(parse_status "$RES")"
  VOL_COUNT=$(parse_body "$RES" | jq 'length' 2>/dev/null || echo "0")
  [ "$VOL_COUNT" -ge 1 ] \
    && pass "Volume listado após upload ($VOL_COUNT volume(s))" \
    || fail "Volume não aparece na listagem após upload"

  section "6. DELETE /mangas/:id/volumes/:volumeId"

  RES=$(http_delete "/mangas/$MANGA_ID/volumes/$VOLUME_ID" "$READER_TOKEN")
  check_status "DELETE volume como READER → 403" 403 "$(parse_status "$RES")"

  RES=$(http_delete "/mangas/$MANGA_ID/volumes/$VOLUME_ID" "$COLLAB_TOKEN")
  check_status "DELETE volume como owner → 204" 204 "$(parse_status "$RES")"

  RES=$(http_delete "/mangas/$MANGA_ID/volumes/$VOLUME_ID" "$COLLAB_TOKEN")
  check_status "DELETE volume já deletado → 404" 404 "$(parse_status "$RES")"

else
  echo -e "  ${YELLOW}SKIP${RESET} — Upload retornou HTTP $UPLOAD_STATUS (GCS pode estar inacessível)"
  echo -e "  ${YELLOW}       Os testes de volume (hash dup, número dup, listagem, deleção) foram pulados${RESET}"
fi

rm -f "$PDF_FILE"


# 7. GET /mangas/{mangaId}/volumes — edge cases
section "7. GET /mangas/:id/volumes — edge cases"

RES=$(http_get "/mangas/00000000-0000-0000-0000-000000000000/volumes" "$COLLAB_TOKEN")
check_status "GET volumes de manga inexistente → 404" 404 "$(parse_status "$RES")"

RES=$(http_get "/mangas/$MANGA_ID/volumes")
check_status "GET volumes sem token → 401" 401 "$(parse_status "$RES")"


# 8. DELETE /mangas/{id}
section "8. DELETE /mangas/{id}"

RES=$(http_delete "/mangas/$MANGA_ID" "$READER_TOKEN")
check_status "DELETE /mangas/:id como READER → 403" 403 "$(parse_status "$RES")"

RES=$(http_delete "/mangas/$MANGA_ADMIN_ID" "$COLLAB_TOKEN")
check_status "DELETE /mangas/:id como non-owner COLLABORATOR → 403" 403 "$(parse_status "$RES")"

RES=$(http_delete "/mangas/$MANGA_ID" "$COLLAB_TOKEN")
check_status "DELETE /mangas/:id como owner → 204" 204 "$(parse_status "$RES")"

COUNT=$(docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -t -c \
  "SELECT COUNT(*) FROM mangas WHERE id = '$MANGA_ID';" | tr -d ' \n')
[ "$COUNT" -eq 0 ] \
  && pass "Mangá removido do banco após DELETE" \
  || fail "Mangá ainda existe no banco após DELETE"

RES=$(http_get "/mangas/$MANGA_SLUG" "$COLLAB_TOKEN")
check_status "GET /mangas/slug após deleção → 404" 404 "$(parse_status "$RES")"

RES=$(http_delete "/mangas/$MANGA_ADMIN_ID" "$ADMIN_TOKEN")
check_status "DELETE /mangas/:id como ADMIN (não owner) → 204" 204 "$(parse_status "$RES")"


# cleanup
section "Cleanup"

docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -q -c "
  DELETE FROM refresh_tokens WHERE user_id IN (
    SELECT id FROM users WHERE email IN ('collab@buruna.test','reader2@buruna.test')
  );
  DELETE FROM users WHERE email IN ('collab@buruna.test','reader2@buruna.test');
" && pass "Usuários de teste removidos"


# resultado
TOTAL=$((PASS + FAIL))
echo -e "\n${BOLD}════════════════════════════════════════${RESET}"
echo -e "${BOLD}  Resultados: $TOTAL testes — ${GREEN}$PASS OK${RESET}${BOLD} — ${RED}$FAIL falhou${RESET}"
echo -e "${BOLD}════════════════════════════════════════${RESET}"
[ "$FAIL" -eq 0 ] \
  && echo -e "${GREEN}  Fase 4 backend OK${RESET}\n" \
  || echo -e "${RED}  $FAIL teste(s) falharam${RESET}\n"

[ "$FAIL" -eq 0 ] && exit 0 || exit 1
