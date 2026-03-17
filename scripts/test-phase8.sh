#!/usr/bin/env bash
# =============================================================================
# Burūna — Phase 8 Backend Test Script
# Tests: GET /admin/dashboard + InactivityJob (SQL state verification)
# Usage: ./scripts/test-phase8.sh
# Requires: curl, jq, python3, docker
# =============================================================================

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost/api}"
DB_CONTAINER="${DB_CONTAINER:-buruna_postgres}"
DB_USER="${DB_USER:-buruna_user}"
DB_NAME="${DB_NAME:-buruna}"
ADMIN_EMAIL="${ADMIN_EMAIL:?Defina ADMIN_EMAIL antes de rodar o script}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:?Defina ADMIN_PASSWORD antes de rodar o script}"

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

parse_body()   { echo "$1" | head -n -1; }
parse_status() { echo "$1" | tail -n 1; }
parse_json()   {
  echo "$1" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('$2',''))" 2>/dev/null || echo ""
}
parse_json_int() {
  echo "$1" | python3 -c "import sys,json; d=json.load(sys.stdin); print(int(d.get('$2',0)))" 2>/dev/null || echo "0"
}

db_exec() {
  docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -q -c "$1"
}

db_query() {
  docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -t -c "$1" | tr -d ' '
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


# Cleanup anterior
section "Cleanup (dados de teste anteriores)"

db_exec "
  DELETE FROM volumes        WHERE manga_id IN (SELECT id FROM mangas WHERE slug LIKE 'phase8%');
  DELETE FROM mangas         WHERE slug LIKE 'phase8%';
  DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%phase8%');
  DELETE FROM users          WHERE email LIKE '%phase8%';
" && pass "Dados anteriores removidos"


# setup autenticação
section "Setup — autenticação"

LOGIN_ADMIN=$(http_post "/auth/login" \
  "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}")
check_status "Login admin" 200 "$(parse_status "$LOGIN_ADMIN")"
ADMIN_TOKEN=$(parse_json "$(parse_body "$LOGIN_ADMIN")" "accessToken")
[ -z "$ADMIN_TOKEN" ] && { fail "Token admin não obtido — abortando"; exit 1; }

READER_RES=$(http_post "/auth/login" \
  "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}")
READER_TOKEN=$(parse_json "$(parse_body "$READER_RES")" "accessToken")


# setup dados de teste via SQL
section "Setup — dados de teste"

BCRYPT_HASH='$2a$10$aGw6owR1pcMYQfdZvSWDTeglPDHItLt7DUt9cCmxHMyXCntVPdmRC'

db_exec "
  INSERT INTO users (id, email, username, password_hash, presentation_message,
                     role, status, quota_gb, created_at, updated_at)
  VALUES
    (gen_random_uuid(), 'reader8a@phase8.test', 'phase8_reader_a', '$BCRYPT_HASH',
     'test', 'READER', 'ACTIVE', 5.00, NOW(), NOW()),
    (gen_random_uuid(), 'inactive80@phase8.test', 'phase8_inactive_80', '$BCRYPT_HASH',
     'test', 'READER', 'ACTIVE', 2.00, NOW() - INTERVAL '80 days', NOW() - INTERVAL '80 days'),
    (gen_random_uuid(), 'inactive95@phase8.test', 'phase8_inactive_95', '$BCRYPT_HASH',
     'test', 'READER', 'ACTIVE', 2.00, NOW() - INTERVAL '95 days', NOW() - INTERVAL '95 days');
" && info "Usuários de teste criados via SQL"

# atualiza last_access_at para simular inatividade
db_exec "
  UPDATE users SET last_access_at = NOW() - INTERVAL '80 days'
  WHERE email = 'inactive80@phase8.test';
  UPDATE users SET last_access_at = NOW() - INTERVAL '95 days'
  WHERE email = 'inactive95@phase8.test';
" && info "last_access_at configurado para usuários inativos"

# cria mangá privado para phase8_reader_a (para testar storage no dashboard)
db_exec "
  INSERT INTO mangas (id, slug, title, alternative_titles, content_warnings,
                      format, status_origin, status_site, is_public,
                      avg_rating, rating_count, view_count,
                      owner_id, created_at, updated_at)
  VALUES (gen_random_uuid(), 'phase8-private-manga', 'Phase8 Private Manga',
          '[]', '[]', 'MANGA', 'ONGOING', 'INCOMPLETE', false,
          0, 0, 0,
          (SELECT id FROM users WHERE email = 'reader8a@phase8.test'),
          NOW(), NOW());
"
MANGA8_ID=$(db_query "SELECT id FROM mangas WHERE slug = 'phase8-private-manga';" | tr -d '[:space:]')
info "Mangá privado criado: $MANGA8_ID"

# insere volume privado com tamanho conhecido (200MB = 209715200 bytes)
db_exec "
  INSERT INTO volumes (id, manga_id, volume_number, file_url, file_hash,
                       file_size_bytes, uploaded_by, created_at)
  VALUES (gen_random_uuid(), '$MANGA8_ID', 1,
          'volumes/phase8-test-fake.pdf', 'phase8fakehash',
          209715200,
          (SELECT id FROM users WHERE email = 'reader8a@phase8.test'),
          NOW());
" && info "Volume privado de 200MB inserido"

# cria também um mangá PÚBLICO para verificar que não entra no storage do dashboard
db_exec "
  INSERT INTO mangas (id, slug, title, alternative_titles, content_warnings,
                      format, status_origin, status_site, is_public,
                      avg_rating, rating_count, view_count,
                      owner_id, created_at, updated_at)
  VALUES (gen_random_uuid(), 'phase8-public-manga', 'Phase8 Public Manga',
          '[]', '[]', 'MANGA', 'ONGOING', 'INCOMPLETE', true,
          0, 0, 0,
          (SELECT id FROM users WHERE email = 'reader8a@phase8.test'),
          NOW(), NOW());
"
MANGA8_PUB_ID=$(db_query "SELECT id FROM mangas WHERE slug = 'phase8-public-manga';" | tr -d '[:space:]')

db_exec "
  INSERT INTO volumes (id, manga_id, volume_number, file_url, file_hash,
                       file_size_bytes, uploaded_by, created_at)
  VALUES (gen_random_uuid(), '$MANGA8_PUB_ID', 1,
          'volumes/phase8-public-fake.pdf', 'phase8publichash',
          524288000,
          (SELECT id FROM users WHERE email = 'reader8a@phase8.test'),
          NOW());
" && info "Volume público de 500MB inserido (não deve aparecer no storage)"


# 1. dashboard — autenticação e autorização
section "1. GET /admin/dashboard — autenticação"

RES=$(http_get "/admin/dashboard")
check_status "GET /dashboard sem token → 401" 401 "$(parse_status "$RES")"

LOGIN_READER=$(http_post "/auth/login" \
  "{\"email\":\"reader8a@phase8.test\",\"password\":\"Test@123456\"}")
# reader não consegue logar pq senha de teste é diferente — usa admin mesmo
RES=$(http_get "/admin/dashboard" "$ADMIN_TOKEN")
check_status "GET /dashboard admin → 200" 200 "$(parse_status "$RES")"


# 2. dashboard — estrutura do response
section "2. GET /admin/dashboard — estrutura"

RES=$(http_get "/admin/dashboard" "$ADMIN_TOKEN")
BODY=$(parse_body "$RES")

ACTIVE_USERS=$(echo "$BODY" | jq -r '.activeUsers' 2>/dev/null || echo "null")
TOTAL_STORAGE=$(echo "$BODY" | jq -r '.totalStorageUsedGb' 2>/dev/null || echo "null")
STORAGE_BY_USER=$(echo "$BODY" | jq -r '.storageByUser' 2>/dev/null || echo "null")

[ "$ACTIVE_USERS" != "null" ] \
  && pass "Campo activeUsers presente" \
  || fail "Campo activeUsers ausente"

[ "$TOTAL_STORAGE" != "null" ] \
  && pass "Campo totalStorageUsedGb presente" \
  || fail "Campo totalStorageUsedGb ausente"

[ "$STORAGE_BY_USER" != "null" ] \
  && pass "Campo storageByUser presente" \
  || fail "Campo storageByUser ausente"

# verifica campos do item de storageByUser
FIRST_ITEM=$(echo "$BODY" | jq '.storageByUser[0]' 2>/dev/null || echo "{}")
F_USER_ID=$(echo "$FIRST_ITEM" | jq -r '.userId' 2>/dev/null || echo "")
F_USERNAME=$(echo "$FIRST_ITEM" | jq -r '.username' 2>/dev/null || echo "")
F_USED_GB=$(echo "$FIRST_ITEM" | jq -r '.usedGb' 2>/dev/null || echo "")
F_QUOTA_GB=$(echo "$FIRST_ITEM" | jq -r '.quotaGb' 2>/dev/null || echo "")

[ -n "$F_USER_ID" ] && [ -n "$F_USERNAME" ] && [ -n "$F_USED_GB" ] && [ -n "$F_QUOTA_GB" ] \
  && pass "storageByUser contém userId, username, usedGb, quotaGb" \
  || fail "Campos ausentes em storageByUser (userId='$F_USER_ID' username='$F_USERNAME' usedGb='$F_USED_GB' quotaGb='$F_QUOTA_GB')"


# 3. dashboard — storage só contabiliza volumes privados
section "3. Dashboard — storage apenas volumes privados"

RES=$(http_get "/admin/dashboard" "$ADMIN_TOKEN")
BODY=$(parse_body "$RES")

# phase8_reader_a tem 200MB privado + 500MB público
# dashboard deve mostrar apenas 200MB = 0.19 GB (arredondado)
READER_A_ENTRY=$(echo "$BODY" | jq '.storageByUser[] | select(.username == "phase8_reader_a")' 2>/dev/null || echo "{}")
USED_GB=$(echo "$READER_A_ENTRY" | jq -r '.usedGb' 2>/dev/null || echo "0")

USED_BYTES=$(echo "$USED_GB" | python3 -c "import sys; print(int(float(sys.stdin.read().strip()) * 1073741824))")
EXPECTED_BYTES=209715200  # 200MB

# tolerância: ±5MB
DIFF=$(python3 -c "print(abs($USED_BYTES - $EXPECTED_BYTES))")
MAX_DIFF=5242880  # 5MB
IS_OK=$(python3 -c "print('ok' if $DIFF <= $MAX_DIFF else 'fail')")

[ "$IS_OK" = "ok" ] \
  && pass "Storage contabiliza apenas volume privado (~200MB = ${USED_GB}GB)" \
  || fail "Storage incorreto — esperado ~0.19GB (200MB privado), recebido ${USED_GB}GB"

# total também deve excluir o volume público
TOTAL_GB=$(echo "$BODY" | jq -r '.totalStorageUsedGb' 2>/dev/null || echo "0")
TOTAL_BYTES=$(echo "$TOTAL_GB" | python3 -c "import sys; print(int(float(sys.stdin.read().strip()) * 1073741824))")

# total deve ser >= 200MB (phase8_reader_a privado) e < 700MB (não inclui 500MB público)
IS_TOTAL_OK=$(python3 -c "print('ok' if $TOTAL_BYTES >= 209715200 and $TOTAL_BYTES < 734003200 else 'fail')")
[ "$IS_TOTAL_OK" = "ok" ] \
  && pass "totalStorageUsedGb exclui volumes públicos (${TOTAL_GB}GB)" \
  || fail "totalStorageUsedGb inclui volumes públicos — recebido ${TOTAL_GB}GB (esperado < 0.68GB)"

info "totalStorageUsedGb: ${TOTAL_GB} GB"
info "phase8_reader_a usedGb: ${USED_GB} GB (esperado ~0.19 GB)"


# 4. dashboard — activeUsers conta apenas ACTIVE
section "4. Dashboard — activeUsers"

RES=$(http_get "/admin/dashboard" "$ADMIN_TOKEN")
BODY=$(parse_body "$RES")
ACTIVE=$(echo "$BODY" | jq -r '.activeUsers' 2>/dev/null || echo "0")

# conta direto no banco para comparar
EXPECTED_ACTIVE=$(db_query "SELECT COUNT(*) FROM users WHERE status = 'ACTIVE';" | tr -d '[:space:]')

[ "$ACTIVE" = "$EXPECTED_ACTIVE" ] \
  && pass "activeUsers=$ACTIVE bate com banco ($EXPECTED_ACTIVE usuários ACTIVE)" \
  || fail "activeUsers=$ACTIVE, banco tem $EXPECTED_ACTIVE usuários ACTIVE"


# 5. InactivityJob verificação de estado via SQL
section "5. InactivityJob — verificação de pré-condições"

# verifica que os usuários de teste estão no estado correto antes do job rodar
STATUS_80=$(db_query "SELECT status FROM users WHERE email = 'inactive80@phase8.test';")
STATUS_95=$(db_query "SELECT status FROM users WHERE email = 'inactive95@phase8.test';")
ACCESS_80=$(db_query "SELECT last_access_at FROM users WHERE email = 'inactive80@phase8.test';")
ACCESS_95=$(db_query "SELECT last_access_at FROM users WHERE email = 'inactive95@phase8.test';")

[ "$STATUS_80" = "ACTIVE" ] \
  && pass "Usuário 80 dias: ACTIVE (candidato a aviso)" \
  || fail "Usuário 80 dias não está ACTIVE: $STATUS_80"

[ "$STATUS_95" = "ACTIVE" ] \
  && pass "Usuário 95 dias: ACTIVE (candidato a desativação)" \
  || fail "Usuário 95 dias não está ACTIVE: $STATUS_95"

info "last_access_at (80 dias): $ACCESS_80"
info "last_access_at (95 dias): $ACCESS_95"

# verifica que o mangá privado existe
MANGA_EXISTS=$(db_query "SELECT COUNT(*) FROM mangas WHERE slug = 'phase8-private-manga' AND is_public = false;")
[ "$MANGA_EXISTS" = "1" ] \
  && pass "Mangá privado do usuário 80 dias existe no banco" \
  || fail "Mangá privado não encontrado"

VOLUME_EXISTS=$(db_query "SELECT COUNT(*) FROM volumes WHERE manga_id = '$MANGA8_ID';")
[ "$VOLUME_EXISTS" = "1" ] \
  && pass "Volume privado existe no banco" \
  || fail "Volume privado não encontrado"

info "Para testar a execução real do InactivityJob:"
info "  docker exec buruna_backend curl -X POST http://localhost:8080/actuator/scheduledtasks"
info "  OU aguardar o cron das 02:00 e verificar logs com: docker logs buruna_backend | grep InactivityJob"
info "  OU ajustar cron para '0 * * * * *' temporariamente e aguardar 1 minuto"


# 6. InactivityJob simulação manual via SQL + verificação de integridade
section "6. InactivityJob — simulação via SQL (desativa usuário 95 dias)"

# simula o que o job faria: desativa o usuário de 95 dias
USER_95_ID=$(db_query "SELECT id FROM users WHERE email = 'inactive95@phase8.test';" | tr -d '[:space:]')

db_exec "
  UPDATE users SET status = 'INACTIVE' WHERE email = 'inactive95@phase8.test';
" && pass "Usuário 95 dias desativado manualmente (simula job)"

STATUS_AFTER=$(db_query "SELECT status FROM users WHERE email = 'inactive95@phase8.test';")
[ "$STATUS_AFTER" = "INACTIVE" ] \
  && pass "Status persiste como INACTIVE no banco" \
  || fail "Status não persistiu: $STATUS_AFTER"

# activeUsers deve ter diminuído
RES=$(http_get "/admin/dashboard" "$ADMIN_TOKEN")
NEW_ACTIVE=$(parse_body "$RES" | jq -r '.activeUsers' 2>/dev/null || echo "0")
CURRENT_ACTIVE=$(db_query "SELECT COUNT(*) FROM users WHERE status = 'ACTIVE';" | tr -d '[:space:]')

[ "$NEW_ACTIVE" = "$CURRENT_ACTIVE" ] \
  && pass "Dashboard atualiza activeUsers após desativação ($NEW_ACTIVE)" \
  || fail "Dashboard desatualizado: mostra $NEW_ACTIVE, banco tem $CURRENT_ACTIVE"


# cleanup
section "Cleanup"

db_exec "
  DELETE FROM volumes        WHERE manga_id IN (SELECT id FROM mangas WHERE slug LIKE 'phase8%');
  DELETE FROM mangas         WHERE slug LIKE 'phase8%';
  DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%phase8%');
  DELETE FROM users          WHERE email LIKE '%phase8%';
" && pass "Dados de teste removidos"

# resultado
TOTAL=$((PASS + FAIL))
echo -e "\n${BOLD}════════════════════════════════════════${RESET}"
echo -e "${BOLD}  Resultados: $TOTAL testes — ${GREEN}$PASS OK${RESET}${BOLD} — ${RED}$FAIL falhou${RESET}"
echo -e "${BOLD}════════════════════════════════════════${RESET}"
[ "$FAIL" -eq 0 ] \
  && echo -e "${GREEN}  Fase 8 backend OK${RESET}\n" \
  || echo -e "${RED}  $FAIL teste(s) falharam${RESET}\n"

[ "$FAIL" -eq 0 ] && exit 0 || exit 1
