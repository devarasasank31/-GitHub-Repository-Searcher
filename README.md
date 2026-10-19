# GitHub Repository Searcher

A production-quality Spring Boot REST API that searches GitHub repositories,
persists results in PostgreSQL, and serves them with consistent error handling
and full test coverage.

## Features

- `POST /api/github/search` — search GitHub repositories by free-text query with
  optional language filter, sort order (`stars`, `forks`, `updated`) and page size
- Results are upserted into PostgreSQL (same repository never duplicated),
  so repeated searches enrich a local historical view of what you queried
- Bounded retry with backoff for transient GitHub failures (network, timeout, 5xx);
  rate limits and client errors are never retried
- Consistent JSON error contract: `400` for bad input, `429` for GitHub rate limits,
  `502` for GitHub upstream/auth failures, `503` when GitHub is unreachable
- Flyway-managed schema, HikariCP connection pooling, Actuator health endpoint,
  OpenAPI/Swagger UI
- Production-ready Docker image (multi-stage, non-root) and a one-command
  Compose stack

## Tech stack

| Concern        | Choice                                                    |
|----------------|-----------------------------------------------------------|
| Runtime        | Java 21, Spring Boot 3.5.4                                |
| Database       | PostgreSQL 16, Flyway migrations, Spring JDBC batch upsert|
| HTTP client    | Spring `RestClient` (JDK `HttpClient`, explicit timeouts) |
| API docs       | springdoc-openapi (Swagger UI)                            |
| Tests          | JUnit 5, Mockito, MockMvc, WireMock 3.13, Testcontainers  |
| Coverage       | JaCoCo — build fails below 80% line / 70% branch          |
| Packaging      | Multi-stage Dockerfile, Docker Compose                    |

## Quick start (Docker)

```bash
cp .env.example .env          # adjust values if needed
docker compose up --build
```

The stack starts PostgreSQL (healthy-checked) and the application, then:

```bash
curl -s -X POST http://localhost:8080/api/github/search \
  -H 'Content-Type: application/json' \
  -d '{"query": "spring boot", "language": "Java", "sort": "stars", "maxResults": 5}'
```

- Health: `GET http://localhost:8080/actuator/health`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

Stop with `docker compose down` (add `-v` to also drop the database volume).

## Local development

Requires JDK 21, Maven and a running PostgreSQL
(`docker compose up postgres -d` is the easiest way):

```bash
mvn test        # unit + slice tests (Testcontainers tests skip without Docker)
mvn verify      # tests + packaging + JaCoCo report and coverage gate
mvn spring-boot:run
```

## Configuration

All settings are externalised; see `.env.example` for the full list.

| Variable              | Default                  | Purpose                                   |
|-----------------------|--------------------------|-------------------------------------------|
| `SPRING_DATASOURCE_URL`      | `jdbc:postgresql://localhost:5432/github_searcher` | JDBC URL      |
| `SPRING_DATASOURCE_USERNAME` | `postgres`               | Database user                              |
| `SPRING_DATASOURCE_PASSWORD` | `postgres`               | Database password                          |
| `GITHUB_TOKEN`                | *(empty)*                | Optional; raises rate limit 10→30/min     |
| `GITHUB_API_BASE_URL`         | `https://api.github.com` | Override for tests/mocks                  |
| `SERVER_PORT`                 | `8080`                   | HTTP port                                  |

Credentials are never hard-coded — they flow in through environment variables.

## API reference

### `POST /api/github/search`

Request body:

```json
{
  "query": "spring boot",
  "language": "Java",
  "sort": "stars",
  "maxResults": 30
}
```

| Field        | Type   | Required | Constraints                          |
|--------------|--------|----------|--------------------------------------|
| `query`      | string | yes      | 1–256 characters, non-blank          |
| `language`   | string | no       | ≤100 characters; appends `language:` qualifier |
| `sort`       | string | no       | `stars` (default), `forks`, `updated` |
| `maxResults` | int    | no       | 1–100, default 30                    |

Response `200 OK`:

```json
{
  "totalCount": 1,
  "repositories": [
    {
      "id": 1296269,
      "name": "Hello-World",
      "description": "My first repository on GitHub!",
      "owner": "octocat",
      "language": "C",
      "stars": 2543,
      "forks": 1251,
      "lastUpdated": "2024-01-01T12:00:00Z"
    }
  ]
}
```

Errors share one stable body:

```json
{
  "timestamp": "2026-10-19T16:00:00Z",
  "status": 429,
  "error": "Too Many Requests",
  "message": "GitHub API rate limit exceeded; retry after 30 seconds",
  "path": "/api/github/search"
}
```

| Status | When                                                       |
|--------|------------------------------------------------------------|
| 400    | Validation failure, unsupported `sort`, malformed JSON      |
| 429    | GitHub rate limit (HTTP 429, or 403 with quota exhausted)   |
| 502    | GitHub rejected credentials (401) or answered 4xx/malformed |
| 503    | GitHub unreachable after retries (network/timeout)          |
| 500    | Unexpected server error (details are never leaked)          |

## Testing

Tests are layered and each layer has a distinct job:

- **Unit** — mapper, sort mapping, request DTO validation, service orchestration
  (Mockito; verifies persistence failures degrade gracefully instead of failing searches)
- **Client** — `GitHubRestClient` against WireMock: payload mapping, retry
  budgets, scenario-based recovery, rate-limit/auth/upstream/unavailable translation
- **Web slice** — `@WebMvcTest` + MockMvc: status codes, error contract, request binding
- **Persistence** — Testcontainers PostgreSQL for Flyway schema and the batched
  `INSERT ... ON CONFLICT` upsert
- **Integration** — `@SpringBootTest` end-to-end: HTTP → controller → service →
  WireMock GitHub → PostgreSQL

Notes:

- Coverage is enforced during `mvn verify` (JaCoCo: ≥80% line, ≥70% branch bundle-wide).
  Current coverage: **96.7% line / 90.0% branch**.
- Testcontainers tests are annotated `@Testcontainers(disabledWithoutDocker = true)`,
  so `mvn test` stays green on machines without Docker — but the persistence and
  integration suites only actually run when Docker is available. Run them with
  Docker to exercise the full suite (92 tests).

## Design decisions

- **Bounded retry** (`github.api.retry-max-attempts`, default 2) applies only to
  network failures, timeouts and upstream 5xx. Rate limits (429) and client errors
  are surfaced immediately — retrying them only burns quota.
- **Batched upsert** — one `INSERT ... ON CONFLICT (id) DO UPDATE` batch per search,
  with in-batch de-duplication, keeps repeated searches cheap and idempotent.
- **Best-effort persistence** — a storage failure is logged and swallowed so a
  database blip never fails an otherwise successful search.
- **Timeouts are explicit** — connect and read timeouts come from configuration
  (`5s`/`10s` defaults) instead of relying on client defaults.

## Project structure

```
src/main/java/com/devarasasank/githubsearcher/
├── client/        GitHub API client, DTOs, retry/error translation
├── config/        Externalised properties, RestClient wiring
├── controller/    POST /api/github/search
├── dto/           Request/response contracts, sort enum, ApiError
├── entity/        JPA entity for github_repositories
├── exception/     Domain exceptions + global handler
├── mapper/        Client/entity/response mapping
├── repository/    JPA repository + batched JDBC upserter
└── service/       Search orchestration
```

## Postman

Import [`postman/GitHub-Repository-Searcher.postman_collection.json`](postman/GitHub-Repository-Searcher.postman_collection.json)
and set the `baseUrl` variable (default `http://localhost:8080`).
