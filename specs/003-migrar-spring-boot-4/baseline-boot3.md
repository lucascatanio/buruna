# Baseline do Boot 3.5.16

Gravada em 2026-11-21, na `dev` em `42a3aa2`, antes da migração (tasks T002–T003). Serve de
régua para o T021 e para os passos 15–15c do [quickstart](quickstart.md).

## Suíte

`./mvnw clean test`: `Tests run: 754, Failures: 0, Errors: 0, Skipped: 0` (67 suítes).

## Respostas HTTP

Capturadas com `capture.sh`. Tokens e cookie mascarados.

### Saúde

```http
$ curl -s -i http://localhost:8080/api/health
HTTP/1.1 200 
Vary: Origin
Vary: Access-Control-Request-Method
Vary: Access-Control-Request-Headers
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Content-Type: application/json
Transfer-Encoding: chunked
Date: Sat, 21 Nov 2026 20:51:11 GMT

{"status":"UP"}
```

### Sem token (401)

```http
$ curl -s -i http://localhost:8080/api/works
HTTP/1.1 401 
Vary: Origin
Vary: Access-Control-Request-Method
Vary: Access-Control-Request-Headers
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Content-Length: 0
Date: Sat, 21 Nov 2026 20:51:11 GMT


```

### Login do admin

```http
$ curl -s -i -X POST http://localhost:8080/api/auth/login (e-mail e senha do admin local)
HTTP/1.1 200 
Vary: Origin
Vary: Access-Control-Request-Method
Vary: Access-Control-Request-Headers
Set-Cookie: buruna_refresh=***; Path=/api/auth; Max-Age=604800; Expires=Sat, 28 Nov 2026 20:51:11 GMT; HttpOnly; SameSite=Strict
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Content-Type: application/json
Transfer-Encoding: chunked
Date: Sat, 21 Nov 2026 20:51:11 GMT

{"requires2FA":false,"accessToken":"***","expiresIn":3600}
```

### Rota inexistente, com token

```http
$ curl -s -i -H Authorization: Bearer *** http://localhost:8080/api/nao-existe
HTTP/1.1 404 
Vary: Origin
Vary: Access-Control-Request-Method
Vary: Access-Control-Request-Headers
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Content-Type: application/json
Transfer-Encoding: chunked
Date: Sat, 21 Nov 2026 20:51:11 GMT

{"status":404,"error":"Not Found","message":"No static resource nao-existe.","path":"/api/nao-existe","timestamp":"2026-11-21T20:51:11.369750976Z"}
```

### Obra inexistente, com token (404)

```http
$ curl -s -i -H Authorization: Bearer *** http://localhost:8080/api/works/00000000-0000-0000-0000-000000000000
HTTP/1.1 404 
Vary: Origin
Vary: Access-Control-Request-Method
Vary: Access-Control-Request-Headers
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Content-Type: application/json
Transfer-Encoding: chunked
Date: Sat, 21 Nov 2026 20:51:11 GMT

{"status":404,"error":"Not Found","message":"Mangá não encontrado com slug: 00000000-0000-0000-0000-000000000000","path":"/api/works/00000000-0000-0000-0000-000000000000","timestamp":"2026-11-21T20:51:11.438821582Z"}
```

### Preflight CORS, origem do front

```http
$ curl -s -i -X OPTIONS -H Origin: http://localhost:5173 -H Access-Control-Request-Method: GET http://localhost:8080/api/works
HTTP/1.1 200 
Vary: Origin
Vary: Access-Control-Request-Method
Vary: Access-Control-Request-Headers
Access-Control-Allow-Origin: http://localhost:5173
Access-Control-Allow-Methods: GET,POST,PUT,PATCH,DELETE,OPTIONS
Access-Control-Allow-Credentials: true
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Content-Length: 0
Date: Sat, 21 Nov 2026 20:51:11 GMT


```

### Preflight CORS, origem estranha

```http
$ curl -s -i -X OPTIONS -H Origin: http://evil.invalid -H Access-Control-Request-Method: GET http://localhost:8080/api/works
HTTP/1.1 403 
Vary: Origin
Vary: Access-Control-Request-Method
Vary: Access-Control-Request-Headers
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Transfer-Encoding: chunked
Date: Sat, 21 Nov 2026 20:51:11 GMT

Invalid CORS request
```
