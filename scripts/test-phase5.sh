#!/usr/bin/env bash
# =============================================================================
# Burūna — Phase 5 Backend Test Script
# Tests all private manga endpoints against a running backend
# Usage: ./scripts/test-phase5.sh
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

http_upload_private() {
  local url="$1" file="$2" title="$3" volume_number="$4" token="$5"
  curl -s -w "\n%{http_code}" -X POST "$BASE_URL$url" \
    -H "Authorization: Bearer $token" \
    -F "file=@$file;type=application/pdf" \
    -F "title=$title" \
    -F "volumeNumber=$volume_number"
}

http_upload_volume() {
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
  DELETE FROM volumes    WHERE manga_id IN (SELECT id FROM mangas WHERE slug LIKE 'phase5%');
  DELETE FROM mangas     WHERE slug LIKE 'phase5%';
  DELETE FROM refresh_tokens WHERE user_id IN (
    SELECT id FROM users WHERE email IN (
      'reader5@buruna.test','collab5@buruna.test','reader5b@buruna.test'
    )
  );
  DELETE FROM users WHERE email IN (
    'reader5@buruna.test','collab5@buruna.test','reader5b@buruna.test'
  );
" && pass "Dados anteriores removidos"


# setup
section "Setup — autenticação"

LOGIN_ADMIN=$(http_post "/auth/login" \
  "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}")
check_status "Login admin" 200 "$(parse_status "$LOGIN_ADMIN")" "$(parse_body "$LOGIN_ADMIN")"
ADMIN_TOKEN=$(parse_json "$(parse_body "$LOGIN_ADMIN")" "accessToken")
[ -z "$ADMIN_TOKEN" ] && { fail "Token admin não obtido — abortando"; exit 1; }

# cria usuários de teste diretamente via SQL para evitar rate limit do /auth/register
# senha hash = BCrypt de "Test@123456" (gerado offline, válido para todos os usuários de teste)
BCRYPT_HASH='$2a$10$aGw6owR1pcMYQfdZvSWDTeglPDHItLt7DUt9cCmxHMyXCntVPdmRC'

docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -q -c "
  INSERT INTO users (id, email, username, password_hash, presentation_message,
                     role, status, quota_gb, created_at, updated_at)
  VALUES
    (gen_random_uuid(), 'reader5@buruna.test',  'phase5_reader',   '$BCRYPT_HASH', 'test', 'READER',       'ACTIVE', 1.00, NOW(), NOW()),
    (gen_random_uuid(), 'collab5@buruna.test',  'phase5_collab',   '$BCRYPT_HASH', 'test', 'COLLABORATOR', 'ACTIVE', 1.00, NOW(), NOW()),
    (gen_random_uuid(), 'reader5b@buruna.test', 'phase5_reader_b', '$BCRYPT_HASH', 'test', 'READER',       'ACTIVE', 1.00, NOW(), NOW());
" && info "Usuários de teste criados via SQL"

READER_ID=$(docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -t -c \
  "SELECT id FROM users WHERE email = 'reader5@buruna.test';" | tr -d ' \n')
COLLAB_ID=$(docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -t -c \
  "SELECT id FROM users WHERE email = 'collab5@buruna.test';" | tr -d ' \n')
READER_B_ID=$(docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -t -c \
  "SELECT id FROM users WHERE email = 'reader5b@buruna.test';" | tr -d ' \n')

[ -z "$READER_ID" ] || [ -z "$COLLAB_ID" ] || [ -z "$READER_B_ID" ] \
  && { fail "Falha ao criar usuários de teste via SQL — abortando"; exit 1; }

info "IDs: reader=$READER_ID collab=$COLLAB_ID readerB=$READER_B_ID"

LOGIN_READER=$(http_post "/auth/login" '{"email":"reader5@buruna.test","password":"Test@123456"}')
check_status "Login reader" 200 "$(parse_status "$LOGIN_READER")"
READER_TOKEN=$(parse_json "$(parse_body "$LOGIN_READER")" "accessToken")

LOGIN_COLLAB=$(http_post "/auth/login" '{"email":"collab5@buruna.test","password":"Test@123456"}')
check_status "Login collaborator" 200 "$(parse_status "$LOGIN_COLLAB")"
COLLAB_TOKEN=$(parse_json "$(parse_body "$LOGIN_COLLAB")" "accessToken")

LOGIN_READER_B=$(http_post "/auth/login" '{"email":"reader5b@buruna.test","password":"Test@123456"}')
check_status "Login reader B" 200 "$(parse_status "$LOGIN_READER_B")"
READER_B_TOKEN=$(parse_json "$(parse_body "$LOGIN_READER_B")" "accessToken")

info "Cotas definidas para 1 GB (via SQL na criação)"

# PDF de teste
# seed aleatório garante hash único a cada execução — evita conflito com volumes existentes
RAND_SEED=$(date +%s%N)
PDF1=$(mktemp /tmp/phase5_test1_XXXXXX.pdf)
printf '%%PDF-1.4\n%% phase5 seed:%s vol1\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj 2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj 3 0 obj<</Type/Page/MediaBox[0 0 612 792]/Parent 2 0 R>>endobj\nxref\n0 4\n0000000000 65535 f\n0000000009 00000 n\n0000000058 00000 n\n0000000115 00000 n\ntrailer<</Size 4/Root 1 0 R>>\nstartxref\n190\n%%%%EOF' "$RAND_SEED" > "$PDF1"
PDF2=$(mktemp /tmp/phase5_test2_XXXXXX.pdf)
printf '%%PDF-1.4\n%% phase5 seed:%s vol2\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj 2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj 3 0 obj<</Type/Page/MediaBox[0 0 595 842]/Parent 2 0 R>>endobj\nxref\n0 4\n0000000000 65535 f\n0000000024 00000 n\n0000000073 00000 n\n0000000130 00000 n\ntrailer<</Size 4/Root 1 0 R>>\nstartxref\n205\n%%%%EOF' "$RAND_SEED" > "$PDF2"
info "PDFs de teste criados (seed: $RAND_SEED)"


# 1. GET /my/mangas — auth obrigatório
section "1. GET /my/mangas"

RES=$(http_get "/my/mangas")
check_status "GET /my/mangas sem token → 401" 401 "$(parse_status "$RES")"

RES=$(http_get "/my/mangas" "$READER_TOKEN")
check_status "GET /my/mangas autenticado → 200" 200 "$(parse_status "$RES")"
CONTENT=$(parse_body "$RES" | jq -r '.content' 2>/dev/null || echo "null")
[ "$CONTENT" != "null" ] \
  && pass "Resposta paginada com campo 'content'" \
  || fail "Resposta sem campo 'content'"


# 2. GET /my/mangas/quota
section "2. GET /my/mangas/quota"

RES=$(http_get "/my/mangas/quota")
check_status "GET /my/mangas/quota sem token → 401" 401 "$(parse_status "$RES")"

RES=$(http_get "/my/mangas/quota" "$READER_TOKEN")
check_status "GET /my/mangas/quota → 200" 200 "$(parse_status "$RES")"
QUOTA_BYTES=$(parse_body "$RES" | jq -r '.quotaBytes' 2>/dev/null || echo "0")
[ "$QUOTA_BYTES" -gt 0 ] \
  && pass "quotaBytes presente e > 0 ($QUOTA_BYTES bytes)" \
  || fail "quotaBytes ausente ou zero"


# 3. POST /my/mangas — upload privado
section "3. POST /my/mangas (upload privado)"

RES=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/my/mangas" \
  -F "file=@$PDF1;type=application/pdf" \
  -F "title=Phase5 Manga Privado" \
  -F "volumeNumber=1")
check_status "POST /my/mangas sem token → 401" 401 "$(parse_status "$RES")"

UPLOAD_RES=$(http_upload_private "/my/mangas" "$PDF1" "Phase5 Manga Privado" "1" "$READER_TOKEN")
UPLOAD_STATUS=$(parse_status "$UPLOAD_RES")

if [ "$UPLOAD_STATUS" -eq 201 ]; then
  pass "POST /my/mangas → 201 (GCS OK)"
  PRIVATE_ID=$(parse_body "$UPLOAD_RES" | jq -r '.id' 2>/dev/null || echo "")
  info "Mangá privado ID: $PRIVATE_ID"

  # não aparece na biblioteca pública
  PUB_RES=$(http_get "/mangas?title=Phase5+Manga+Privado" "$READER_TOKEN")
  PUB_COUNT=$(parse_body "$PUB_RES" | jq -r '.totalElements' 2>/dev/null || echo "0")
  [ "$PUB_COUNT" -eq 0 ] \
    && pass "Mangá privado não aparece na biblioteca pública" \
    || fail "Mangá privado aparece na biblioteca pública — VAZAMENTO"

  # aparece na coleção do owner
  LIST_RES=$(http_get "/my/mangas" "$READER_TOKEN")
  LIST_COUNT=$(parse_body "$LIST_RES" | jq -r '.totalElements' 2>/dev/null || echo "0")
  [ "$LIST_COUNT" -ge 1 ] \
    && pass "Mangá privado aparece na coleção do owner ($LIST_COUNT)" \
    || fail "Mangá privado não aparece na coleção do owner"

  # outro usuário não enxerga
  LIST_B=$(http_get "/my/mangas" "$READER_B_TOKEN")
  LIST_B_COUNT=$(parse_body "$LIST_B" | jq -r '.totalElements' 2>/dev/null || echo "0")
  [ "$LIST_B_COUNT" -eq 0 ] \
    && pass "Outro usuário não enxerga a coleção privada alheia" \
    || fail "Isolamento de coleção falhou — outro usuário vê $LIST_B_COUNT mangá(s)"

  # hash duplicado
  DUP_RES=$(http_upload_private "/my/mangas" "$PDF1" "Phase5 Duplicado" "1" "$READER_TOKEN")
  check_status "POST /my/mangas hash duplicado → 409" 409 "$(parse_status "$DUP_RES")"


  # 4. PUT /my/mangas/{id}
  section "4. PUT /my/mangas/{id}"

  UPDATE_RES=$(http_put "/my/mangas/$PRIVATE_ID" \
    '{"title":"Phase5 Manga Editado","synopsis":"nova sinopse"}' "$READER_TOKEN")
  check_status "PUT /my/mangas/:id como owner → 200" 200 "$(parse_status "$UPDATE_RES")"
  UPDATED_TITLE=$(parse_body "$UPDATE_RES" | jq -r '.title' 2>/dev/null || echo "")
  [ "$UPDATED_TITLE" = "Phase5 Manga Editado" ] \
    && pass "Título atualizado corretamente" \
    || fail "Título incorreto após update (recebido: '$UPDATED_TITLE')"

  RES=$(http_put "/my/mangas/$PRIVATE_ID" \
    '{"title":"Invasao","synopsis":""}' "$READER_B_TOKEN")
  check_status "PUT /my/mangas/:id por outro usuário → 403" 403 "$(parse_status "$RES")"

  RES=$(http_put "/my/mangas/00000000-0000-0000-0000-000000000000" \
    '{"title":"Fantasma","synopsis":""}' "$READER_TOKEN")
  check_status "PUT /my/mangas/id-inexistente → 404" 404 "$(parse_status "$RES")"


  # 5. POST /my/mangas/{id}/volumes
  section "5. POST /my/mangas/{id}/volumes"

  ADD_RES=$(http_upload_volume "/my/mangas/$PRIVATE_ID/volumes" "$PDF2" "2" "$READER_TOKEN")
  check_status "POST /my/mangas/:id/volumes → 201" 201 "$(parse_status "$ADD_RES")"
  VOL_COUNT=$(parse_body "$ADD_RES" | jq '.volumes | length' 2>/dev/null || echo "0")
  [ "$VOL_COUNT" -ge 2 ] \
    && pass "Response tem $VOL_COUNT volume(s) após adição" \
    || fail "Esperado >= 2 volumes, recebido $VOL_COUNT"

  DUP_VOL=$(http_upload_volume "/my/mangas/$PRIVATE_ID/volumes" "$PDF2" "3" "$READER_TOKEN")
  check_status "POST volumes hash duplicado → 409" 409 "$(parse_status "$DUP_VOL")"

  DUP_NUM=$(http_upload_volume "/my/mangas/$PRIVATE_ID/volumes" "$PDF2" "2" "$READER_TOKEN")
  check_status "POST volumes número duplicado → 409" 409 "$(parse_status "$DUP_NUM")"

  RES=$(http_upload_volume "/my/mangas/$PRIVATE_ID/volumes" "$PDF2" "3" "$READER_B_TOKEN")
  check_status "POST volumes de outro usuário → 403" 403 "$(parse_status "$RES")"


  # 6. DELETE /my/mangas/{id}/volumes/{volumeId}
  section "6. DELETE /my/mangas/{id}/volumes/{volumeId}"

  VOLUME_ID=$(parse_body "$ADD_RES" | jq -r '.volumes[] | select(.volumeNumber == 2) | .id' 2>/dev/null || echo "")
  info "Volume 2 ID: $VOLUME_ID"

  if [ -n "$VOLUME_ID" ]; then
    RES=$(http_delete "/my/mangas/$PRIVATE_ID/volumes/$VOLUME_ID" "$READER_B_TOKEN")
    check_status "DELETE volume por outro usuário → 403" 403 "$(parse_status "$RES")"

    RES=$(http_delete "/my/mangas/$PRIVATE_ID/volumes/$VOLUME_ID" "$READER_TOKEN")
    check_status "DELETE volume como owner → 200" 200 "$(parse_status "$RES")"
    REMAINING=$(parse_body "$RES" | jq '.volumes | length' 2>/dev/null || echo "-1")
    [ "$REMAINING" -eq 1 ] \
      && pass "Volume removido — restam $REMAINING volume(s)" \
      || fail "Esperado 1 volume restante, recebido $REMAINING"

    RES=$(http_delete "/my/mangas/$PRIVATE_ID/volumes/$VOLUME_ID" "$READER_TOKEN")
    check_status "DELETE volume já deletado → 404" 404 "$(parse_status "$RES")"
  else
    fail "Não foi possível obter ID do volume 2 — testes de deleção pulados"
  fi


  # 7. POST /my/mangas/{id}/promote
  section "7. POST /my/mangas/{id}/promote"

  RES=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/my/mangas/$PRIVATE_ID/promote" \
    -H "Authorization: Bearer $READER_TOKEN")
  check_status "POST /promote como READER → 403" 403 "$(parse_status "$RES")"

  # cria mangá privado para o collab promover — usa PDF2 (hash diferente do PDF1 já upado)
  COLLAB_UPLOAD=$(http_upload_private "/my/mangas" "$PDF2" "Phase5 Collab Privado" "1" "$COLLAB_TOKEN")
  check_status "Upload privado pelo collaborator → 201" 201 "$(parse_status "$COLLAB_UPLOAD")"
  COLLAB_PRIVATE_ID=$(parse_body "$COLLAB_UPLOAD" | jq -r '.id' 2>/dev/null || echo "")

  PROMOTE_RES=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/my/mangas/$COLLAB_PRIVATE_ID/promote" \
    -H "Authorization: Bearer $COLLAB_TOKEN")
  check_status "POST /promote como COLLABORATOR → 200" 200 "$(parse_status "$PROMOTE_RES")"

  # após promoção, some da coleção privada
  AFTER_PROMOTE=$(http_get "/my/mangas" "$COLLAB_TOKEN")
  AFTER_COUNT=$(parse_body "$AFTER_PROMOTE" | jq -r '.totalElements' 2>/dev/null || echo "-1")
  [ "$AFTER_COUNT" -eq 0 ] \
    && pass "Mangá promovido não aparece mais na coleção privada" \
    || fail "Mangá ainda aparece na coleção privada após promote ($AFTER_COUNT)"

  # aparece na biblioteca pública
  PUB_AFTER=$(http_get "/mangas?title=Phase5+Collab+Privado" "$COLLAB_TOKEN")
  PUB_AFTER_COUNT=$(parse_body "$PUB_AFTER" | jq -r '.totalElements' 2>/dev/null || echo "0")
  [ "$PUB_AFTER_COUNT" -ge 1 ] \
    && pass "Mangá promovido aparece na biblioteca pública" \
    || fail "Mangá promovido não aparece na biblioteca pública"


  # 8. DELETE /my/mangas/{id}
  section "8. DELETE /my/mangas/{id}"

  RES=$(http_delete "/my/mangas/$PRIVATE_ID" "$READER_B_TOKEN")
  check_status "DELETE /my/mangas/:id por outro usuário → 403" 403 "$(parse_status "$RES")"

  RES=$(http_delete "/my/mangas/$PRIVATE_ID" "$READER_TOKEN")
  check_status "DELETE /my/mangas/:id como owner → 204" 204 "$(parse_status "$RES")"

  COUNT=$(docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -t -c \
    "SELECT COUNT(*) FROM mangas WHERE id = '$PRIVATE_ID';" | tr -d ' \n')
  [ "$COUNT" -eq 0 ] \
    && pass "Mangá removido do banco após DELETE" \
    || fail "Mangá ainda existe no banco após DELETE"

  RES=$(http_delete "/my/mangas/$PRIVATE_ID" "$READER_TOKEN")
  check_status "DELETE /my/mangas/:id já deletado → 404" 404 "$(parse_status "$RES")"

  # cleanup promoted manga
  PROMOTED_DB_ID=$(docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -t -c \
    "SELECT id FROM mangas WHERE title = 'Phase5 Collab Privado' LIMIT 1;" | tr -d ' \n')
  [ -n "$PROMOTED_DB_ID" ] && docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -q -c \
    "DELETE FROM volumes WHERE manga_id = '$PROMOTED_DB_ID'; DELETE FROM mangas WHERE id = '$PROMOTED_DB_ID';"

else
  echo -e "  ${YELLOW}SKIP${RESET} — Upload retornou HTTP $UPLOAD_STATUS (GCS pode estar inacessível)"
  echo -e "  ${YELLOW}       Testes de coleção privada foram pulados${RESET}"
fi

rm -f "$PDF1" "$PDF2"


# cleanup
section "Cleanup"

docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -q -c "
  DELETE FROM volumes WHERE manga_id IN (SELECT id FROM mangas WHERE slug LIKE 'phase5%');
  DELETE FROM mangas WHERE slug LIKE 'phase5%';
  DELETE FROM refresh_tokens WHERE user_id IN (
    SELECT id FROM users WHERE email IN (
      'reader5@buruna.test','collab5@buruna.test','reader5b@buruna.test'
    )
  );
  DELETE FROM users WHERE email IN (
    'reader5@buruna.test','collab5@buruna.test','reader5b@buruna.test'
  );
" && pass "Usuários e mangás de teste removidos"


# resultado
TOTAL=$((PASS + FAIL))
echo -e "\n${BOLD}════════════════════════════════════════${RESET}"
echo -e "${BOLD}  Resultados: $TOTAL testes — ${GREEN}$PASS OK${RESET}${BOLD} — ${RED}$FAIL falhou${RESET}"
echo -e "${BOLD}════════════════════════════════════════${RESET}"
[ "$FAIL" -eq 0 ] \
  && echo -e "${GREEN}  Fase 5 backend OK${RESET}\n" \
  || echo -e "${RED}  $FAIL teste(s) falharam${RESET}\n"

[ "$FAIL" -eq 0 ] && exit 0 || exit 1
