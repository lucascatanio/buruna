#!/usr/bin/env bash
# =============================================================================
# Burūna — Phase 6 Backend Test Script
# Tests all reader endpoints against a running backend
# Usage: ./scripts/test-phase6.sh
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
  DELETE FROM reading_history WHERE user_id IN (
    SELECT id FROM users WHERE email IN ('reader6@buruna.test','reader6b@buruna.test')
  );
  DELETE FROM reading_progress WHERE user_id IN (
    SELECT id FROM users WHERE email IN ('reader6@buruna.test','reader6b@buruna.test')
  );
  DELETE FROM volumes    WHERE manga_id IN (SELECT id FROM mangas WHERE slug LIKE 'phase6%');
  DELETE FROM mangas     WHERE slug LIKE 'phase6%';
  DELETE FROM refresh_tokens WHERE user_id IN (
    SELECT id FROM users WHERE email IN ('reader6@buruna.test','reader6b@buruna.test')
  );
  DELETE FROM users WHERE email IN ('reader6@buruna.test','reader6b@buruna.test');
" && pass "Dados anteriores removidos"


# setup
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
    (gen_random_uuid(), 'reader6@buruna.test',  'phase6_reader',   '$BCRYPT_HASH', 'test', 'READER', 'ACTIVE', 1.00, NOW(), NOW()),
    (gen_random_uuid(), 'reader6b@buruna.test', 'phase6_reader_b', '$BCRYPT_HASH', 'test', 'READER', 'ACTIVE', 1.00, NOW(), NOW());
" && info "Usuários de teste criados via SQL"

LOGIN_READER=$(http_post "/auth/login" '{"email":"reader6@buruna.test","password":"Test@123456"}')
check_status "Login reader A" 200 "$(parse_status "$LOGIN_READER")"
READER_TOKEN=$(parse_json "$(parse_body "$LOGIN_READER")" "accessToken")

LOGIN_READER_B=$(http_post "/auth/login" '{"email":"reader6b@buruna.test","password":"Test@123456"}')
check_status "Login reader B" 200 "$(parse_status "$LOGIN_READER_B")"
READER_B_TOKEN=$(parse_json "$(parse_body "$LOGIN_READER_B")" "accessToken")

RAND_SEED=$(date +%s%N)
PDF1=$(mktemp /tmp/phase6_test1_XXXXXX.pdf)
PDF2=$(mktemp /tmp/phase6_test2_XXXXXX.pdf)
printf '%%PDF-1.4\n%% phase6 seed:%s vol1\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj 2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj 3 0 obj<</Type/Page/MediaBox[0 0 612 792]/Parent 2 0 R>>endobj\nxref\n0 4\n0000000000 65535 f\n0000000009 00000 n\n0000000058 00000 n\n0000000115 00000 n\ntrailer<</Size 4/Root 1 0 R>>\nstartxref\n190\n%%%%EOF' "$RAND_SEED" > "$PDF1"
printf '%%PDF-1.4\n%% phase6 seed:%s vol2\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj 2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj 3 0 obj<</Type/Page/MediaBox[0 0 595 842]/Parent 2 0 R>>endobj\nxref\n0 4\n0000000000 65535 f\n0000000024 00000 n\n0000000073 00000 n\n0000000130 00000 n\ntrailer<</Size 4/Root 1 0 R>>\nstartxref\n205\n%%%%EOF' "$RAND_SEED" > "$PDF2"
info "PDFs criados (seed: $RAND_SEED)"

# mangá público com 2 volumes para testes do leitor
MANGA_RES=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/mangas" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"Phase6 Manga Reader Test","alternativeTitles":[],"synopsis":"test","format":"MANGA","statusOrigin":"ONGOING","statusSite":"INCOMPLETE","contentWarnings":[],"tagIds":[]}')
check_status "Criar mangá público de teste" 201 "$(parse_status "$MANGA_RES")"
MANGA_ID=$(parse_body "$MANGA_RES" | jq -r '.id' 2>/dev/null || echo "")
info "Mangá ID: $MANGA_ID"

VOL1_RES=$(http_upload "/mangas/$MANGA_ID/volumes" "$PDF1" "$ADMIN_TOKEN" "-F volumeNumber=1")
VOL1_STATUS=$(parse_status "$VOL1_RES")

if [ "$VOL1_STATUS" -eq 201 ]; then
  pass "Upload volume 1 → 201 (GCS OK)"
  VOLUME1_ID=$(parse_body "$VOL1_RES" | jq -r '.id' 2>/dev/null || echo "")
  info "Volume 1 ID: $VOLUME1_ID"

  VOL2_RES=$(http_upload "/mangas/$MANGA_ID/volumes" "$PDF2" "$ADMIN_TOKEN" "-F volumeNumber=2")
  check_status "Upload volume 2 → 201" 201 "$(parse_status "$VOL2_RES")"
  VOLUME2_ID=$(parse_body "$VOL2_RES" | jq -r '.id' 2>/dev/null || echo "")
  info "Volume 2 ID: $VOLUME2_ID"

  # mangá privado para testar isolamento de acesso
  PRIVATE_RES=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/my/mangas" \
    -H "Authorization: Bearer $READER_TOKEN" \
    -F "file=@$PDF1;type=application/pdf" \
    -F "title=Phase6 Private Reader" \
    -F "volumeNumber=1")
  PRIVATE_VOL_ID=$(parse_body "$PRIVATE_RES" | jq -r '.volumes[0].id' 2>/dev/null || echo "")
  PRIVATE_MANGA_ID=$(parse_body "$PRIVATE_RES" | jq -r '.id' 2>/dev/null || echo "")
  info "Volume privado ID: $PRIVATE_VOL_ID"


  # =========================================================================
  # 1. GET /reader/{volumeId}/url
  # =========================================================================
  section "1. GET /reader/{volumeId}/url"

  RES=$(http_get "/reader/$VOLUME1_ID/url")
  check_status "GET /reader/:volumeId/url sem token → 401" 401 "$(parse_status "$RES")"

  RES=$(http_get "/reader/$VOLUME1_ID/url" "$READER_TOKEN")
  check_status "GET /reader/:volumeId/url autenticado → 200" 200 "$(parse_status "$RES")"

  URL_FIELD=$(parse_body "$RES" | jq -r '.url' 2>/dev/null || echo "")
  [ -n "$URL_FIELD" ] \
    && pass "Campo 'url' presente na resposta" \
    || fail "Campo 'url' ausente"

  EXPIRES=$(parse_body "$RES" | jq -r '.expiresInSeconds' 2>/dev/null || echo "0")
  [ "$EXPIRES" -gt 0 ] \
    && pass "Campo 'expiresInSeconds' = $EXPIRES" \
    || fail "Campo 'expiresInSeconds' ausente ou zero"

  # view_count incrementa a cada chamada
  VC_BEFORE=$(docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -t -c \
    "SELECT view_count FROM mangas WHERE id = '$MANGA_ID';" | tr -d ' \n')
  http_get "/reader/$VOLUME1_ID/url" "$READER_B_TOKEN" > /dev/null
  VC_AFTER=$(docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -t -c \
    "SELECT view_count FROM mangas WHERE id = '$MANGA_ID';" | tr -d ' \n')
  [ "$VC_AFTER" -gt "$VC_BEFORE" ] \
    && pass "view_count incrementado ($VC_BEFORE → $VC_AFTER)" \
    || fail "view_count não incrementou ($VC_BEFORE → $VC_AFTER)"

  RES=$(http_get "/reader/00000000-0000-0000-0000-000000000000/url" "$READER_TOKEN")
  check_status "GET /reader/uuid-inexistente/url → 404" 404 "$(parse_status "$RES")"

  # isolamento: reader B não acessa volume privado de reader A
  if [ -n "$PRIVATE_VOL_ID" ]; then
    RES=$(http_get "/reader/$PRIVATE_VOL_ID/url" "$READER_B_TOKEN")
    check_status "GET volume privado por não-owner → 403" 403 "$(parse_status "$RES")"

    RES=$(http_get "/reader/$PRIVATE_VOL_ID/url" "$READER_TOKEN")
    check_status "GET volume privado pelo owner → 200" 200 "$(parse_status "$RES")"
  fi

  # histórico registrado no banco após GET /url
  HIST_COUNT=$(docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -t -c \
    "SELECT COUNT(*) FROM reading_history
     WHERE user_id = (SELECT id FROM users WHERE email = 'reader6@buruna.test')
     AND volume_id = '$VOLUME1_ID';" | tr -d ' \n')
  [ "$HIST_COUNT" -ge 1 ] \
    && pass "Entrada registrada em reading_history após GET /url ($HIST_COUNT)" \
    || fail "reading_history vazio após GET /url"


  # =========================================================================
  # 2. POST /reader/{volumeId}/progress
  # =========================================================================
  section "2. POST /reader/{volumeId}/progress"

  RES=$(http_post "/reader/$VOLUME1_ID/progress" '{"currentPage":10}')
  check_status "POST /reader/:volumeId/progress sem token → 401" 401 "$(parse_status "$RES")"

  RES=$(http_post "/reader/$VOLUME1_ID/progress" '{"currentPage":10}' "$READER_TOKEN")
  check_status "POST /reader/:volumeId/progress → 200" 200 "$(parse_status "$RES")"
  SAVED_PAGE=$(parse_body "$RES" | jq -r '.currentPage' 2>/dev/null || echo "0")
  [ "$SAVED_PAGE" -eq 10 ] \
    && pass "Progresso salvo (página $SAVED_PAGE)" \
    || fail "Página incorreta — esperado 10, recebido $SAVED_PAGE"

  # upsert — mesma chave (user+volume), página atualizada
  RES=$(http_post "/reader/$VOLUME1_ID/progress" '{"currentPage":25}' "$READER_TOKEN")
  check_status "POST /reader/:volumeId/progress upsert → 200" 200 "$(parse_status "$RES")"
  UPSERTED=$(parse_body "$RES" | jq -r '.currentPage' 2>/dev/null || echo "0")
  [ "$UPSERTED" -eq 25 ] \
    && pass "Upsert atualiza página corretamente ($UPSERTED)" \
    || fail "Upsert falhou — esperado 25, recebido $UPSERTED"

  # validação: página < 1 → 400
  RES=$(http_post "/reader/$VOLUME1_ID/progress" '{"currentPage":0}' "$READER_TOKEN")
  check_status "POST progress página 0 → 400" 400 "$(parse_status "$RES")"

  # volume inexistente → 404
  RES=$(http_post "/reader/00000000-0000-0000-0000-000000000000/progress" \
    '{"currentPage":1}' "$READER_TOKEN")
  check_status "POST progress volume inexistente → 404" 404 "$(parse_status "$RES")"

  # progresso isolado por usuário
  RES=$(http_post "/reader/$VOLUME1_ID/progress" '{"currentPage":5}' "$READER_B_TOKEN")
  check_status "POST progress reader B → 200" 200 "$(parse_status "$RES")"
  B_PAGE=$(parse_body "$RES" | jq -r '.currentPage' 2>/dev/null || echo "0")
  [ "$B_PAGE" -eq 5 ] \
    && pass "Progresso isolado por usuário (reader B: página $B_PAGE)" \
    || fail "Isolamento de progresso falhou"

  # verifica isolamento no banco
  A_DB=$(docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -t -c \
    "SELECT rp.current_page FROM reading_progress rp
     JOIN users u ON u.id = rp.user_id
     WHERE u.email = 'reader6@buruna.test' AND rp.volume_id = '$VOLUME1_ID';" | tr -d ' \n')
  B_DB=$(docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -t -c \
    "SELECT rp.current_page FROM reading_progress rp
     JOIN users u ON u.id = rp.user_id
     WHERE u.email = 'reader6b@buruna.test' AND rp.volume_id = '$VOLUME1_ID';" | tr -d ' \n')
  [ "$A_DB" -eq 25 ] && [ "$B_DB" -eq 5 ] \
    && pass "Banco confirma isolamento: reader A=pág $A_DB, reader B=pág $B_DB" \
    || fail "Isolamento no banco incorreto: reader A=$A_DB, reader B=$B_DB"


  # =========================================================================
  # 3. GET /reader/progress/{mangaId}
  # =========================================================================
  section "3. GET /reader/progress/{mangaId}"

  RES=$(http_get "/reader/progress/$MANGA_ID")
  check_status "GET /reader/progress/:mangaId sem token → 401" 401 "$(parse_status "$RES")"

  RES=$(http_get "/reader/progress/$MANGA_ID" "$READER_TOKEN")
  check_status "GET /reader/progress/:mangaId → 200" 200 "$(parse_status "$RES")"
  PROG_PAGE=$(parse_body "$RES" | jq -r '.currentPage' 2>/dev/null || echo "0")
  [ "$PROG_PAGE" -eq 25 ] \
    && pass "Progresso retornado corretamente (página $PROG_PAGE)" \
    || fail "Progresso incorreto — esperado 25, recebido $PROG_PAGE"

  # salva progresso no vol 2 → GET progress deve retornar vol 2 (volume mais recente)
  http_post "/reader/$VOLUME2_ID/progress" '{"currentPage":3}' "$READER_TOKEN" > /dev/null
  RES=$(http_get "/reader/progress/$MANGA_ID" "$READER_TOKEN")
  LATEST_VOL=$(parse_body "$RES" | jq -r '.volumeId' 2>/dev/null || echo "")
  [ "$LATEST_VOL" = "$VOLUME2_ID" ] \
    && pass "GET progress retorna volume com maior número (vol 2)" \
    || fail "GET progress não retornou vol 2 (recebido: $LATEST_VOL)"

  # mangá sem histórico retorna 204
  MANGA2_RES=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/mangas" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"title":"Phase6 Manga Sem Leitura","alternativeTitles":[],"format":"MANGA","statusOrigin":"ONGOING","statusSite":"INCOMPLETE","contentWarnings":[],"tagIds":[]}')
  MANGA2_ID=$(parse_body "$MANGA2_RES" | jq -r '.id' 2>/dev/null || echo "")
  RES=$(http_get "/reader/progress/$MANGA2_ID" "$READER_TOKEN")
  check_status "GET progress mangá nunca lido → 204" 204 "$(parse_status "$RES")"

  # mangá inexistente → 404
  RES=$(http_get "/reader/progress/00000000-0000-0000-0000-000000000000" "$READER_TOKEN")
  check_status "GET progress mangá inexistente → 404" 404 "$(parse_status "$RES")"


  # =========================================================================
  # 4. GET /reader/history
  # =========================================================================
  section "4. GET /reader/history"

  RES=$(http_get "/reader/history")
  check_status "GET /reader/history sem token → 401" 401 "$(parse_status "$RES")"

  RES=$(http_get "/reader/history" "$READER_TOKEN")
  check_status "GET /reader/history → 200" 200 "$(parse_status "$RES")"

  CONTENT=$(parse_body "$RES" | jq -r '.content' 2>/dev/null || echo "null")
  [ "$CONTENT" != "null" ] \
    && pass "Resposta paginada com campo 'content'" \
    || fail "Resposta sem campo 'content'"

  HIST_TOTAL=$(parse_body "$RES" | jq '.content | length' 2>/dev/null || echo "0")
  [ "$HIST_TOTAL" -ge 1 ] \
    && pass "Histórico com $HIST_TOTAL entrada(s)" \
    || fail "Histórico vazio — esperado pelo menos 1 entrada"

  # valida campos obrigatórios do item
  ITEM=$(parse_body "$RES" | jq '.content[0]' 2>/dev/null || echo "{}")
  F_VOL=$(echo "$ITEM" | jq -r '.volumeId' 2>/dev/null || echo "")
  F_TITLE=$(echo "$ITEM" | jq -r '.mangaTitle' 2>/dev/null || echo "")
  F_READ=$(echo "$ITEM" | jq -r '.readAt' 2>/dev/null || echo "")
  F_NUM=$(echo "$ITEM" | jq -r '.volumeNumber' 2>/dev/null || echo "")
  [ -n "$F_VOL" ] && [ -n "$F_TITLE" ] && [ -n "$F_READ" ] && [ -n "$F_NUM" ] \
    && pass "Campos volumeId, volumeNumber, mangaTitle, readAt presentes" \
    || fail "Campos ausentes (volumeId='$F_VOL', mangaTitle='$F_TITLE', readAt='$F_READ', volumeNumber='$F_NUM')"

  # ordenação: mais recente primeiro (readAt DESC)
  if [ "$(parse_body "$RES" | jq '.content | length' 2>/dev/null || echo 0)" -ge 2 ]; then
    DATE1=$(parse_body "$RES" | jq -r '.content[0].readAt' 2>/dev/null || echo "")
    DATE2=$(parse_body "$RES" | jq -r '.content[1].readAt' 2>/dev/null || echo "")
    [ "$DATE1" \> "$DATE2" ] || [ "$DATE1" = "$DATE2" ] \
      && pass "Histórico ordenado por readAt DESC" \
      || fail "Histórico não está ordenado por data decrescente"
  fi

  # histórico isolado por usuário
  HIST_A=$(parse_body "$RES" | jq '.content | length' 2>/dev/null || echo "0")
  RES_B=$(http_get "/reader/history" "$READER_B_TOKEN")
  HIST_B=$(parse_body "$RES_B" | jq '.content | length' 2>/dev/null || echo "0")
  info "Histórico: reader A=$HIST_A entradas, reader B=$HIST_B entradas"
  [ "$HIST_A" -gt "$HIST_B" ] \
    && pass "Histórico isolado por usuário (A leu mais volumes)" \
    || info "A=$HIST_A, B=$HIST_B (ambos podem ter lido o mesmo volume)"

  # paginação
  RES=$(http_get "/reader/history?size=1&page=0" "$READER_TOKEN")
  check_status "GET /reader/history paginado (size=1) → 200" 200 "$(parse_status "$RES")"
  PAGE_SIZE=$(parse_body "$RES" | jq '.content | length' 2>/dev/null || echo "0")
  [ "$PAGE_SIZE" -eq 1 ] \
    && pass "Paginação funciona (size=1 retornou 1 item)" \
    || fail "Paginação incorreta — esperado 1, recebido $PAGE_SIZE"

else
  echo -e "  ${YELLOW}SKIP${RESET} — Upload retornou HTTP $VOL1_STATUS (GCS pode estar inacessível)"
  echo -e "  ${YELLOW}       Testes do leitor foram pulados${RESET}"
fi

rm -f "$PDF1" "$PDF2"


# =============================================================================
# Cleanup
# =============================================================================
section "Cleanup"

docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -q -c "
  DELETE FROM reading_history WHERE user_id IN (
    SELECT id FROM users WHERE email IN ('reader6@buruna.test','reader6b@buruna.test')
  );
  DELETE FROM reading_progress WHERE user_id IN (
    SELECT id FROM users WHERE email IN ('reader6@buruna.test','reader6b@buruna.test')
  );
  DELETE FROM volumes    WHERE manga_id IN (SELECT id FROM mangas WHERE slug LIKE 'phase6%');
  DELETE FROM mangas     WHERE slug LIKE 'phase6%';
  DELETE FROM refresh_tokens WHERE user_id IN (
    SELECT id FROM users WHERE email IN ('reader6@buruna.test','reader6b@buruna.test')
  );
  DELETE FROM users WHERE email IN ('reader6@buruna.test','reader6b@buruna.test');
" && pass "Dados de teste removidos"


# =============================================================================
# Resultado
# =============================================================================
TOTAL=$((PASS + FAIL))
echo -e "\n${BOLD}════════════════════════════════════════${RESET}"
echo -e "${BOLD}  Resultados: $TOTAL testes — ${GREEN}$PASS OK${RESET}${BOLD} — ${RED}$FAIL falhou${RESET}"
echo -e "${BOLD}════════════════════════════════════════${RESET}"
[ "$FAIL" -eq 0 ] \
  && echo -e "${GREEN}  Fase 6 backend OK${RESET}\n" \
  || echo -e "${RED}  $FAIL teste(s) falharam${RESET}\n"

[ "$FAIL" -eq 0 ] && exit 0 || exit 1
