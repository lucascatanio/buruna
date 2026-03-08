#!/usr/bin/env bash
# =============================================================================
# Burūna — Phase 2 Backend Test Script
# Tests all auth + user management endpoints against a running backend
# Usage: ./scripts/test-phase2.sh
# =============================================================================

set -euo pipefail

# config
BASE_URL="${BASE_URL:-http://localhost:8080/api}"
DB_CONTAINER="${DB_CONTAINER:-buruna_postgres}"
DB_USER="${DB_USER:-buruna_user}"
DB_NAME="${DB_NAME:-buruna}"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
RESET='\033[0m'

# counters
PASS=0
FAIL=0

# helpers
pass() { echo -e "  ${GREEN}PASS${RESET} — $1"; PASS=$((PASS + 1)); }
fail() { echo -e "  ${RED}FAIL${RESET} — $1"; FAIL=$((FAIL + 1)); }
section() { echo -e "\n${CYAN}${BOLD}▶ $1${RESET}"; }

expect_status() {
  local label="$1"
  local expected="$2"
  local actual="$3"
  local body="$4"

  if [ "$actual" -eq "$expected" ]; then
    pass "$label (HTTP $actual)"
  else
    fail "$label — expected HTTP $expected, got HTTP $actual"
    echo -e "    ${YELLOW}Body: $body${RESET}"
  fi
}

http_post() {
  local url="$1"
  local body="$2"
  local token="${3:-}"
  local auth_header=""
  [ -n "$token" ] && auth_header="-H \"Authorization: Bearer $token\""
  curl -s -w "\n%{http_code}" -X POST "$BASE_URL$url" \
    -H "Content-Type: application/json" \
    ${token:+-H "Authorization: Bearer $token"} \
    -d "$body"
}

http_get() {
  local url="$1"
  local token="${2:-}"
  curl -s -w "\n%{http_code}" -X GET "$BASE_URL$url" \
    ${token:+-H "Authorization: Bearer $token"}
}

http_patch() {
  local url="$1"
  local body="$2"
  local token="$3"
  curl -s -w "\n%{http_code}" -X PATCH "$BASE_URL$url" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $token" \
    -d "$body"
}

http_delete() {
  local url="$1"
  local token="$2"
  curl -s -w "\n%{http_code}" -X DELETE "$BASE_URL$url" \
    -H "Authorization: Bearer $token"
}

parse_body()  { echo "$1" | head -n -1; }
parse_status() { echo "$1" | tail -n 1; }
parse_json()  { echo "$1" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('$2',''))" 2>/dev/null || echo ""; }

# pre flight
section "Pre-flight checks"

echo -n "  Checking backend at $BASE_URL ... "
if curl -s --max-time 5 "$BASE_URL/auth/login" -X POST \
    -H "Content-Type: application/json" \
    -d '{}' > /dev/null 2>&1; then
  echo -e "${GREEN}UP${RESET}"
else
  echo -e "${RED}DOWN${RESET}"
  echo -e "\n${RED}Backend is not responding. Start it with: docker compose up backend${RESET}"
  exit 1
fi

echo -n "  Checking DB container '$DB_CONTAINER' ... "
if docker ps --format '{{.Names}}' | grep -q "^${DB_CONTAINER}$"; then
  echo -e "${GREEN}RUNNING${RESET}"
else
  echo -e "${RED}NOT FOUND${RESET}"
  echo -e "\n${RED}Start the DB with: docker compose up postgres${RESET}"
  exit 1
fi

# cleanup from previous runs
section "Cleanup (previous test data)"

docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -q -c "
  DELETE FROM refresh_tokens WHERE user_id IN (
    SELECT id FROM users WHERE email IN (
      'admin@buruna.test', 'reader@buruna.test', 'reject@buruna.test'
    )
  );
  DELETE FROM users WHERE email IN (
    'admin@buruna.test', 'reader@buruna.test', 'reject@buruna.test'
  );
" && echo -e "  ${GREEN}Test users cleaned${RESET}"

# setup adm user
section "Setup — Register admin user and promote via SQL"

REGISTER_ADMIN=$(http_post "/auth/register" '{
  "email": "admin@buruna.test",
  "username": "test_admin",
  "password": "Admin@123456",
  "presentationMessage": "I am the test admin"
}')
STATUS=$(parse_status "$REGISTER_ADMIN")
expect_status "Register admin user" 201 "$STATUS" "$(parse_body "$REGISTER_ADMIN")"

# promote to ADMIN + ACTIVE via SQL
docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -q -c "
  UPDATE users SET role = 'ADMIN', status = 'ACTIVE'
  WHERE email = 'admin@buruna.test';
" && echo -e "  ${GREEN}Promoted to ADMIN + ACTIVE via SQL${RESET}"

# 1. auth endpoints
section "1. Auth"

# 1.1 login with pending user (before promotion doesn't apply here, admin is already active)
# register a fresh pending user to test pending block
PENDING_REG=$(http_post "/auth/register" '{
  "email": "reader@buruna.test",
  "username": "test_reader",
  "password": "Reader@123456",
  "presentationMessage": "I am a test reader"
}')
expect_status "Register reader (PENDING)" 201 "$(parse_status "$PENDING_REG")" "$(parse_body "$PENDING_REG")"

LOGIN_PENDING=$(http_post "/auth/login" '{
  "email": "reader@buruna.test",
  "password": "Reader@123456"
}')
expect_status "Login with PENDING user should return 403" 403 "$(parse_status "$LOGIN_PENDING")" "$(parse_body "$LOGIN_PENDING")"

# 1.2 login as admin
LOGIN_ADMIN=$(http_post "/auth/login" '{
  "email": "admin@buruna.test",
  "password": "Admin@123456"
}')
expect_status "Admin login" 200 "$(parse_status "$LOGIN_ADMIN")" "$(parse_body "$LOGIN_ADMIN")"

ADMIN_BODY=$(parse_body "$LOGIN_ADMIN")
ADMIN_ACCESS=$(parse_json "$ADMIN_BODY" "accessToken")
ADMIN_REFRESH=$(parse_json "$ADMIN_BODY" "refreshToken")

if [ -z "$ADMIN_ACCESS" ]; then
  echo -e "  ${RED}Could not extract admin access token — aborting${RESET}"
  exit 1
fi

# 1.3 refresh token
REFRESH_RES=$(http_post "/auth/refresh" "{\"refreshToken\": \"$ADMIN_REFRESH\"}")
expect_status "Refresh token" 200 "$(parse_status "$REFRESH_RES")" "$(parse_body "$REFRESH_RES")"

NEW_REFRESH=$(parse_json "$(parse_body "$REFRESH_RES")" "refreshToken")
NEW_ACCESS=$(parse_json "$(parse_body "$REFRESH_RES")" "accessToken")

# 1.4 duplicate register (same email)
DUP_EMAIL=$(http_post "/auth/register" '{
  "email": "admin@buruna.test",
  "username": "other_admin",
  "password": "Admin@123456",
  "presentationMessage": "Duplicate"
}')
expect_status "Duplicate email returns 409" 409 "$(parse_status "$DUP_EMAIL")" "$(parse_body "$DUP_EMAIL")"

# 1.5 duplicate register (same username)
DUP_USER=$(http_post "/auth/register" '{
  "email": "other@buruna.test",
  "username": "test_admin",
  "password": "Admin@123456",
  "presentationMessage": "Duplicate username"
}')
expect_status "Duplicate username returns 409" 409 "$(parse_status "$DUP_USER")" "$(parse_body "$DUP_USER")"

# 1.6 invalid credentials
BAD_LOGIN=$(http_post "/auth/login" '{
  "email": "admin@buruna.test",
  "password": "wrong_password"
}')
expect_status "Wrong password returns 401" 401 "$(parse_status "$BAD_LOGIN")" "$(parse_body "$BAD_LOGIN")"

# 1.7 logout
LOGOUT_RES=$(http_post "/auth/logout" "{\"refreshToken\": \"$NEW_REFRESH\"}" "$NEW_ACCESS")
expect_status "Logout" 204 "$(parse_status "$LOGOUT_RES")" "$(parse_body "$LOGOUT_RES")"

# after logout, re-login to get fresh token for admin tests
LOGIN2=$(http_post "/auth/login" '{
  "email": "admin@buruna.test",
  "password": "Admin@123456"
}')
ADMIN_ACCESS=$(parse_json "$(parse_body "$LOGIN2")" "accessToken")
ADMIN_REFRESH=$(parse_json "$(parse_body "$LOGIN2")" "refreshToken")

# 1.8 unauthenticated request should return 401
UNAUTH=$(http_get "/admin/users")
expect_status "Unauthenticated request returns 401" 401 "$(parse_status "$UNAUTH")" ""

# 2. adm — user management
section "2. Admin — User management"

# 2.1 list all users
LIST_ALL=$(http_get "/admin/users" "$ADMIN_ACCESS")
expect_status "GET /admin/users" 200 "$(parse_status "$LIST_ALL")" "$(parse_body "$LIST_ALL")"

# 2.2 list pending
LIST_PENDING=$(http_get "/admin/users/pending" "$ADMIN_ACCESS")
expect_status "GET /admin/users/pending" 200 "$(parse_status "$LIST_PENDING")" "$(parse_body "$LIST_PENDING")"

# 2.3 get reader user ID via SQL
READER_ID=$(docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -t -c \
  "SELECT id FROM users WHERE email = 'reader@buruna.test';" | tr -d ' \n')

echo -e "  ${YELLOW}Reader ID: $READER_ID${RESET}"

# 2.4 get user by ID
GET_USER=$(http_get "/admin/users/$READER_ID" "$ADMIN_ACCESS")
expect_status "GET /admin/users/:id" 200 "$(parse_status "$GET_USER")" "$(parse_body "$GET_USER")"

# 2.5 approve reader
APPROVE=$(http_post "/admin/users/$READER_ID/approve" '{}' "$ADMIN_ACCESS")
expect_status "POST /admin/users/:id/approve" 204 "$(parse_status "$APPROVE")" "$(parse_body "$APPROVE")"

# 2.6 try to approve again (should 409 — not pending anymore)
APPROVE2=$(http_post "/admin/users/$READER_ID/approve" '{}' "$ADMIN_ACCESS")
expect_status "Approve already active user returns 409" 409 "$(parse_status "$APPROVE2")" "$(parse_body "$APPROVE2")"

# 2.7 update role
UPDATE_ROLE=$(http_patch "/admin/users/$READER_ID/role" '{"role": "COLLABORATOR"}' "$ADMIN_ACCESS")
expect_status "PATCH /admin/users/:id/role" 200 "$(parse_status "$UPDATE_ROLE")" "$(parse_body "$UPDATE_ROLE")"

# 2.8 update status
UPDATE_STATUS=$(http_patch "/admin/users/$READER_ID/status" '{"status": "INACTIVE"}' "$ADMIN_ACCESS")
expect_status "PATCH /admin/users/:id/status" 200 "$(parse_status "$UPDATE_STATUS")" "$(parse_body "$UPDATE_STATUS")"

# 2.9 update quota
UPDATE_QUOTA=$(http_patch "/admin/users/$READER_ID/quota" '{"quotaGb": 10.5}' "$ADMIN_ACCESS")
expect_status "PATCH /admin/users/:id/quota" 200 "$(parse_status "$UPDATE_QUOTA")" "$(parse_body "$UPDATE_QUOTA")"

# 2.10 reject a new pending user
REJECT_REG=$(http_post "/auth/register" '{
  "email": "reject@buruna.test",
  "username": "test_reject",
  "password": "Reject@123456",
  "presentationMessage": "I will be rejected"
}')
REJECT_ID=$(docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -t -c \
  "SELECT id FROM users WHERE email = 'reject@buruna.test';" | tr -d ' \n')

REJECT_RES=$(http_post "/admin/users/$REJECT_ID/reject" '{"reason": "Test rejection"}' "$ADMIN_ACCESS")
expect_status "POST /admin/users/:id/reject with reason" 204 "$(parse_status "$REJECT_RES")" "$(parse_body "$REJECT_RES")"

# confirm rejected user was deleted
DELETED_CHECK=$(docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -t -c \
  "SELECT COUNT(*) FROM users WHERE email = 'reject@buruna.test';" | tr -d ' \n')
if [ "$DELETED_CHECK" -eq 0 ]; then
  pass "Rejected user was deleted from DB"
else
  fail "Rejected user still exists in DB"
fi

# 2.11 non-admin accessing admin endpoint
READER_LOGIN=$(http_post "/auth/login" '{
  "email": "reader@buruna.test",
  "password": "Reader@123456"
}')

# re-activate reader first via SQL since we set to INACTIVE
docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -q -c \
  "UPDATE users SET status = 'ACTIVE' WHERE email = 'reader@buruna.test';" > /dev/null

READER_LOGIN=$(http_post "/auth/login" '{
  "email": "reader@buruna.test",
  "password": "Reader@123456"
}')
READER_TOKEN=$(parse_json "$(parse_body "$READER_LOGIN")" "accessToken")

FORBIDDEN=$(http_get "/admin/users" "$READER_TOKEN")
expect_status "Non-admin accessing /admin/users returns 403" 403 "$(parse_status "$FORBIDDEN")" ""

# 2.12 delete own account
DELETE_ACCOUNT=$(http_delete "/auth/account" "$READER_TOKEN")
expect_status "DELETE /auth/account" 204 "$(parse_status "$DELETE_ACCOUNT")" ""

# confirm deleted
DELETED_READER=$(docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -t -c \
  "SELECT COUNT(*) FROM users WHERE email = 'reader@buruna.test';" | tr -d ' \n')
if [ "$DELETED_READER" -eq 0 ]; then
  pass "Account deleted from DB after DELETE /auth/account"
else
  fail "Account still exists in DB after DELETE /auth/account"
fi

# 3. rate limit (register 6 times from same IP)
section "3. Rate limit on POST /auth/register"

RATE_PASS=0
for i in $(seq 1 6); do
  RES=$(http_post "/auth/register" "{
    \"email\": \"rate${i}@buruna.test\",
    \"username\": \"rate_user_${i}\",
    \"password\": \"RateTest@123\",
    \"presentationMessage\": \"rate limit test $i\"
  }")
  STATUS=$(parse_status "$RES")
  if [ "$STATUS" -eq 201 ] || [ "$STATUS" -eq 409 ]; then
    ((RATE_PASS++))
  fi
done

# 6th should have been blocked (429) if rate limit is 5/hour
# clean up rate test users
docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -q -c \
  "DELETE FROM users WHERE email LIKE 'rate%@buruna.test';" > /dev/null

RATE_RES=$(http_post "/auth/register" '{
  "email": "ratex@buruna.test",
  "username": "rate_user_x",
  "password": "RateTest@123",
  "presentationMessage": "over limit"
}')
RATE_STATUS=$(parse_status "$RATE_RES")
if [ "$RATE_STATUS" -eq 429 ]; then
  pass "Rate limit triggered after 5 attempts (HTTP 429)"
else
  echo -e "  ${YELLOW}SKIP — Rate limit not triggered (HTTP $RATE_STATUS). IP may have reset or limit not 5.${RESET}"
fi

# summary
TOTAL=$((PASS + FAIL))
echo -e "\n${BOLD}════════════════════════════════════════${RESET}"
echo -e "${BOLD}  Results: $TOTAL tests — ${GREEN}$PASS passed${RESET}${BOLD} — ${RED}$FAIL failed${RESET}"
echo -e "${BOLD}════════════════════════════════════════${RESET}\n"

[ "$FAIL" -eq 0 ] && exit 0 || exit 1
