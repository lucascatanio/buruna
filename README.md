# Burūna

Biblioteca pessoal de mangás com autenticação, upload e leitor web inline.

**[buruna.com.br](https://buruna.com.br)**

## Stack

Backend em Java 21 + Spring Boot 3.4, Clean Architecture por bounded context
(`identity`, `manga`, `reading`, `engagement`, `admin`), PostgreSQL + Flyway,
JWT + Refresh Token + 2FA (TOTP), arquivos no Google Cloud Storage via URLs
assinadas. Frontend em React 18 + TypeScript, Vite, shadcn/ui, Tailwind CSS.
Deploy em Cloud Run (GCP). Detalhes completos: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Quickstart local

```bash
docker compose up -d postgres   # Postgres em localhost:5433
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.arguments=--app.storage.local.path=/tmp/buruna-storage
cd frontend && npm install && npm run dev
```

Variáveis de ambiente obrigatórias e notas de setup: [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).

## Documentação

| Documento | Conteúdo |
|---|---|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Clean Architecture por bounded context, regra de dependência, fluxos de usuário |
| [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) | Setup local completo (variáveis obrigatórias, comandos) |
| [docs/DATABASE.md](docs/DATABASE.md) | Modelo de dados, constraints, índices, migrations |
| [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) | Infraestrutura GCP (Cloud Run, GCE, GCS, Secret Manager) |
| [docs/TESTING.md](docs/TESTING.md) | Pirâmide de testes, convenções, como rodar |
| [SECURITY.md](SECURITY.md) | JWT/refresh, RBAC, rate limit, 2FA, reportar vulnerabilidade |
| [docs/adr/](docs/adr/) | Decisões de arquitetura (ADR-01 a ADR-39) |
| [docs/glossario-dominio.md](docs/glossario-dominio.md) | Vocabulário de domínio |
| [docs/BACKLOG.md](docs/BACKLOG.md) | Achados fora de escopo, aguardando issue própria |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Fluxo de PR e regras de arquitetura |

## Licença

Ver [LICENSE](LICENSE).
