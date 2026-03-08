# Burūna

Biblioteca pessoal de mangás com autenticação, upload e leitor web inline.

## Stack

| Camada | Tecnologia |
|-------|-----------|
| Backend | Java 21, Spring Boot 3.x, Spring Security, JWT + Refresh Token |
| Banco de dados | PostgreSQL 16, Flyway |
| Storage | Google Cloud Storage (URLs assinadas) |
| Email | Gmail SMTP via Spring Mail |
| Frontend | React 18, TypeScript, Vite, shadcn/ui, Tailwind CSS v4 |
| Proxy | nginx |
| Infra | Docker, Docker Compose, GCP (Cloud Run ou GCE) |

---

## Pré-requisitos

- [Docker](https://www.docker.com/) e Docker Compose instalados
- Conta no [Google Cloud](https://cloud.google.com/) com um bucket GCS criado
- Arquivo `gcs-credentials.json` (Service Account com permissão de Storage Object Admin)
- Conta Gmail com [App Password](https://myaccount.google.com/apppasswords) configurada

---

## Setup local

### 1. Clone o repositório

```bash
git clone https://github.com/seu-usuario/buruna.git
cd buruna
```

### 2. Configure as variáveis de ambiente

```bash
cp .env.example .env
```

Edite o `.env` com seus valores reais

### 3. Adicione as credenciais do GCS

Coloque o arquivo `gcs-credentials.json` na **raiz do projeto**:

```
buruna/
└── gcs-credentials.json
```

### 4. Suba os containers

```bash
docker compose up --build
```

A aplicação estará disponível em: **http://localhost**

---

## Estrutura do repositório

```
buruna/
├── backend/                 Java 21 + Spring Boot
│   ├── src/
│   │   └── main/
│   │       ├── java/com/buruna/
│   │       │   ├── BurunaApplication.java
│   │       │   ├── auth/
│   │       │   ├── user/
│   │       │   ├── manga/
│   │       │   ├── reader/
│   │       │   └── infra/
│   │       └── resources/
│   │           ├── application.yml
│   │           └── db/migration/
│   └── Dockerfile
├── frontend/                React + TypeScript + shadcn/ui
│   ├── src/
│   └── Dockerfile
├── nginx/
│   └── nginx.conf
├── docs/                    Documentação do projeto
│   ├── plan.md
│   ├── dds.md
│   └── tags.md
├── docker-compose.yml
├── .env.example
├── .gitignore
└── README.md
```

---

## Arquitetura de módulos (backend)

Cada módulo segue a estrutura:

```
modulo/
├── controller/    Entrada HTTP
├── service/       Casos de uso
├── domain/        Entidades JPA e enums
├── repository/    Interfaces Spring Data JPA
├── dto/           Records de entrada (Request) e saída (Response)
└── exception/     Exceções de domínio
```

---

## Comandos úteis

```bash
# subir apenas o banco para desv
docker compose up postgres

# ver logs
docker compose logs -f backend

# rebuild
docker compose up --build backend

# Derrubar tudo e limpar volumes
docker compose down -v
```
