# Burūna

![CI](https://github.com/lucascatanio/buruna/actions/workflows/ci.yml/badge.svg?branch=dev)
![Deploy](https://github.com/lucascatanio/buruna/actions/workflows/deploy.yml/badge.svg?branch=main)
![Java](https://img.shields.io/badge/Java-21-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.3-brightgreen)
![React](https://img.shields.io/badge/React-19-blue)
![TypeScript](https://img.shields.io/badge/TypeScript-5.9-blue)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue)
![License](https://img.shields.io/github/license/lucascatanio/buruna)

Biblioteca pessoal de mangás com autenticação, upload e leitor web inline.

**[buruna.com.br](https://buruna.com.br)**

## Stack

Backend em Java 21 + Spring Boot 3.4.3, Clean Architecture por bounded context
(`identity`, `manga`, `reading`, `engagement`, `admin`), PostgreSQL + Flyway,
JWT + Refresh Token + 2FA (TOTP), arquivos no Google Cloud Storage via URLs
assinadas. Frontend em React 19 + TypeScript, Vite, shadcn/ui, Tailwind CSS.
Deploy em Cloud Run (GCP). Detalhes completos: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Destaques técnicos

* Monolito modular com Clean Architecture por bounded context.
* Domínio rico com DDD pragmático.
* Autenticação com JWT, refresh token e 2FA TOTP.
* Upload e leitura de arquivos via Google Cloud Storage com URLs assinadas.
* Migrations versionadas com Flyway.
* Testes automatizados com JUnit, Testcontainers e ArchUnit.
* CI com GitHub Actions.
* Deploy em Cloud Run na Google Cloud Platform.

## Quickstart local

```bash
docker compose up -d postgres   # Postgres em localhost:5433
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.arguments=--app.storage.local.path=/tmp/buruna-storage
cd frontend && npm install && npm run dev
```

Variáveis de ambiente obrigatórias e notas de setup: [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).

## Documentação

| Documento                                              | Conteúdo                                                                        |
| ------------------------------------------------------ | ------------------------------------------------------------------------------- |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)           | Clean Architecture por bounded context, regra de dependência, fluxos de usuário |
| [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)             | Setup local completo, variáveis obrigatórias e comandos                         |
| [docs/DATABASE.md](docs/DATABASE.md)                   | Modelo de dados, constraints, índices e migrations                              |
| [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)               | Infraestrutura GCP, Cloud Run, GCE, GCS e Secret Manager                        |
| [docs/TESTING.md](docs/TESTING.md)                     | Pirâmide de testes, convenções e como rodar                                     |
| [SECURITY.md](SECURITY.md)                             | JWT/refresh, RBAC, rate limit, 2FA e reporte de vulnerabilidades                |
| [docs/adr/](docs/adr/)                                 | Decisões de arquitetura (ADR-01 a ADR-39)                                       |
| [docs/glossario-dominio.md](docs/glossario-dominio.md) | Vocabulário de domínio                                                          |
| [docs/BACKLOG.md](docs/BACKLOG.md)                     | Achados fora de escopo, aguardando issue própria                                |
| [CONTRIBUTING.md](CONTRIBUTING.md)                     | Fluxo de PR e regras de arquitetura                                             |

## Licença

Ver [LICENSE](LICENSE).
