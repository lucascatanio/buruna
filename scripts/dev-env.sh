#!/usr/bin/env bash
# Ambiente local para validar uma feature no navegador (/validar-quickstart).
# Segue o docs/DEVELOPMENT.md: Postgres do docker compose, backend no profile "local" e Vite.
#
#   scripts/dev-env.sh up                 sobe o que não estiver rodando e espera ficar pronto
#   scripts/dev-env.sh admin [email senha]
#                                         cria (ou promove) uma conta ADMIN ativa para o teste;
#                                         sem argumentos, gera e-mail e senha na primeira vez e
#                                         os guarda em .dev-env/admin.env
#   scripts/dev-env.sh status             mostra o que está no ar
#   scripts/dev-env.sh down               derruba só o que este script subiu
#
# O que já estiver rodando nas portas (5433, 8080, 5173) é reaproveitado e não é derrubado.
# Logs e pids ficam em .dev-env/ (fora do git).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STATE="$ROOT/.dev-env"
DB_PORT=5433
BACKEND_PORT=8080
FRONTEND_PORT=5173
BACKEND_HEALTH="http://localhost:$BACKEND_PORT/api/health"
FRONTEND_URL="http://localhost:$FRONTEND_PORT"
ADMIN_FILE="$STATE/admin.env"

log() { printf '[dev-env] %s\n' "$*"; }
fail() { printf '[dev-env] ERRO: %s\n' "$*" >&2; exit 1; }

load_env() {
    [[ -f "$ROOT/.env" ]] || fail "falta o .env na raiz: copie o .env.example e ajuste (docs/DEVELOPMENT.md)"
    set -a
    # shellcheck disable=SC1091
    . "$ROOT/.env"
    set +a
    : "${DB_NAME:?DB_NAME ausente no .env}" "${DB_USER:?DB_USER ausente no .env}" "${DB_PASSWORD:?DB_PASSWORD ausente no .env}"
}

port_open() { (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null; }
http_ok() { curl -sf -o /dev/null --max-time 3 "$1"; }

random_hex() { od -An -tx1 -N"$1" /dev/urandom | tr -d ' \n'; }

wait_for() { # segundos, comando...
    local seconds="$1"
    shift
    for _ in $(seq "$seconds"); do
        if "$@"; then return 0; fi
        sleep 1
    done
    return 1
}

start_background() { # nome, diretório, comando...
    local name="$1" dir="$2"
    shift 2
    mkdir -p "$STATE"
    # set -m: o processo ganha um grupo próprio, para o "down" derrubar também os filhos (o java do mvnw)
    set -m
    (cd "$dir" && exec "$@") >"$STATE/$name.log" 2>&1 &
    echo $! >"$STATE/$name.pid"
    set +m
}

stop_background() {
    local name="$1" pid_file="$STATE/$1.pid"
    [[ -f "$pid_file" ]] || return 0
    local pid
    pid="$(cat "$pid_file")"
    if kill -0 "$pid" 2>/dev/null; then
        kill -TERM -- "-$pid" 2>/dev/null || kill -TERM "$pid" 2>/dev/null || true
        log "$name parado (pid $pid)"
    fi
    rm -f "$pid_file"
}

up_postgres() {
    if port_open "$DB_PORT"; then
        log "Postgres já está em localhost:$DB_PORT, reaproveitado"
        return
    fi
    log "subindo o Postgres (docker compose)"
    (cd "$ROOT" && docker compose up -d postgres)
    touch "$STATE/postgres.started"
    wait_for 60 port_open "$DB_PORT" || fail "o Postgres não respondeu em localhost:$DB_PORT"
}

up_backend() {
    if http_ok "$BACKEND_HEALTH"; then
        log "backend já está em localhost:$BACKEND_PORT, reaproveitado"
        return
    fi
    log "subindo o backend no profile local (o primeiro build pode levar alguns minutos)"
    mkdir -p "$STATE/storage"
    # HCAPTCHA_SECRET vazio: captcha desligado, aceito só no profile local
    start_background backend "$ROOT/backend" env \
        DB_URL="jdbc:postgresql://localhost:$DB_PORT/$DB_NAME" \
        DB_USER="$DB_USER" DB_PASSWORD="$DB_PASSWORD" \
        JWT_SECRET="${JWT_SECRET:-$(random_hex 32)}" \
        ADMIN_EMAIL="${ADMIN_EMAIL:-admin@buruna.test}" \
        GCS_BUCKET_NAME=dummy GCS_CREDENTIALS_PATH=dummy \
        APP_JOBS_SECRET="${APP_JOBS_SECRET:-dev-secret}" \
        HCAPTCHA_SECRET= \
        ./mvnw -q spring-boot:run \
        -Dspring-boot.run.profiles=local \
        "-Dspring-boot.run.arguments=--app.storage.local.path=$STATE/storage --app.storage.local.base-url=$FRONTEND_URL"
    if ! wait_for 420 http_ok "$BACKEND_HEALTH"; then
        tail -n 40 "$STATE/backend.log" >&2 || true
        fail "o backend não respondeu em $BACKEND_HEALTH (log completo em .dev-env/backend.log)"
    fi
    log "backend pronto"
}

up_frontend() {
    if http_ok "$FRONTEND_URL"; then
        log "frontend já está em $FRONTEND_URL, reaproveitado"
        return
    fi
    if [[ ! -d "$ROOT/frontend/node_modules" ]]; then
        log "instalando as dependências do frontend"
        (cd "$ROOT/frontend" && npm install --no-audit --no-fund)
    fi
    log "subindo o frontend (Vite)"
    # sem site key, o cadastro usa o token de desenvolvimento e o hCaptcha não aparece
    start_background frontend "$ROOT/frontend" env VITE_HCAPTCHA_SITE_KEY= \
        npm run dev -- --port "$FRONTEND_PORT" --strictPort
    if ! wait_for 90 http_ok "$FRONTEND_URL"; then
        tail -n 40 "$STATE/frontend.log" >&2 || true
        fail "o frontend não respondeu em $FRONTEND_URL (log completo em .dev-env/frontend.log)"
    fi
    log "frontend pronto"
}

run_sql() {
    if command -v psql >/dev/null 2>&1; then
        PGPASSWORD="$DB_PASSWORD" psql -h localhost -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 -tAc "$1"
    else
        (cd "$ROOT" && docker compose exec -T postgres psql -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 -tAc "$1")
    fi
}

ensure_admin() {
    local email password
    [[ $# -eq 0 || $# -eq 2 ]] || fail "use 'admin' sem argumentos ou 'admin <email> <senha>'"
    if [[ $# -eq 2 ]]; then
        email="$1" password="$2"
    elif [[ -f "$ADMIN_FILE" ]]; then
        # shellcheck disable=SC1090
        . "$ADMIN_FILE"
        email="$ADMIN_LOGIN_EMAIL" password="$ADMIN_LOGIN_PASSWORD"
    else
        email="e2e-admin-$(random_hex 3)@buruna.test" password="$(random_hex 12)"
    fi
    [[ "$email" =~ ^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]+$ ]] || fail "e-mail inválido: $email"
    [[ ${#password} -ge 8 && "$password" != *[\"\\]* ]] || fail "senha precisa de 8+ caracteres, sem aspas nem barra invertida"
    http_ok "$BACKEND_HEALTH" || fail "backend fora do ar; rode 'scripts/dev-env.sh up' antes"
    local username="${email%%@*}"
    username="${username//[^A-Za-z0-9]/}"
    local body status
    body=$(printf '{"email":"%s","username":"%s","password":"%s","presentationMessage":"Conta de validação local","captchaToken":"dev-skip-captcha"}' \
        "$email" "$username" "$password")
    status=$(curl -s -o /dev/null -w '%{http_code}' -H 'Content-Type: application/json' \
        -d "$body" "http://localhost:$BACKEND_PORT/api/auth/register")
    # 201 também para e-mail já cadastrado (ADR-42); 409 é username repetido de outra conta
    case "$status" in
        201) ;;
        409) log "username '$username' já existe; seguindo para a promoção pelo e-mail" ;;
        *) fail "cadastro respondeu HTTP $status" ;;
    esac
    local updated
    updated=$(run_sql "UPDATE users SET role = 'ADMIN', status = 'ACTIVE' WHERE email = '$email' RETURNING id;")
    [[ -n "$updated" ]] || fail "nenhuma conta com o e-mail $email (o username '$username' pode ser de outra conta; passe outro e-mail)"
    mkdir -p "$STATE"
    (umask 077 && printf 'ADMIN_LOGIN_EMAIL=%s\nADMIN_LOGIN_PASSWORD=%s\n' "$email" "$password" >"$ADMIN_FILE")
    log "ADMIN pronto: $email / $password (só local; guardado em .dev-env/admin.env)"
}

report() { # nome, endereço, comando...
    local name="$1" address="$2"
    shift 2
    if "$@"; then log "$name: no ar ($address)"; else log "$name: fora do ar"; fi
}

status() {
    report Postgres "localhost:$DB_PORT" port_open "$DB_PORT"
    report backend "$BACKEND_HEALTH" http_ok "$BACKEND_HEALTH"
    report frontend "$FRONTEND_URL" http_ok "$FRONTEND_URL"
}

main() {
    local command="${1:-}"
    shift || true
    case "$command" in
        up)
            load_env
            mkdir -p "$STATE"
            up_postgres
            up_backend
            up_frontend
            log "pronto: $FRONTEND_URL"
            ;;
        admin)
            load_env
            ensure_admin "$@"
            ;;
        status)
            status
            ;;
        down)
            stop_background frontend
            stop_background backend
            if [[ -f "$STATE/postgres.started" ]]; then
                (cd "$ROOT" && docker compose stop postgres)
                rm -f "$STATE/postgres.started"
            fi
            ;;
        *)
            sed -n '2,14p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
            exit 2
            ;;
    esac
}

main "$@"
