# NewIdeaCase Platform

A portfolio-ready modular backend built with Java 21 and Spring Boot 4.1.

NewIdeaCase demonstrates how I design a backend beyond basic CRUD: clear module boundaries, secure multi-tenant APIs, reliable background processing, caching, observability, containerized local infrastructure, and a local RAG path with explicit production boundaries.

> **Project status:** the core platform, secure API, local infrastructure, monitoring stack, ingestion worker, and local RAG adapter are implemented and reproducible with Docker Compose. AWS deployment, AWS SDK integration, and MongoDB Atlas Vector Search are intentionally not presented as completed work.

## 30-second overview

| Area | Evidence in this repository |
|---|---|
| Backend design | Product, profile, order, and knowledge modules with REST APIs and consistent RFC Problem Details errors |
| Data consistency | Optimistic locking, product snapshots in orders, cursor pagination, idempotent document registration, and an outbox-style ingestion worker |
| Security | OIDC/JWT resource server, audience validation, scopes, backend-derived tenant/role context, and non-disclosing cross-tenant 404 responses |
| Performance | Redis read-through cache and bounded cursor-based queries |
| Testing | Maven verification with Spring Security tests and Testcontainers for MongoDB |
| Operations | Actuator, Prometheus, Grafana, Alertmanager, Loki, Promtail, correlation IDs, and local acceptance scripts |
| AI integration | Spring AI/Ollama retrieval and grounded answers with citations, filtered by tenant and role |

## Why this project exists

The goal is to show the engineering decisions required when a backend grows from a single API into an operable platform:

- keep business modules understandable without prematurely splitting them into microservices;
- make authorization and tenant boundaries server-controlled;
- make retries and partial failures observable;
- expose repeatable local verification instead of relying on architecture claims;
- separate implemented local capabilities from planned cloud integrations.

## Architecture

~~~mermaid
flowchart LR
    Client["Clients / Postman"] --> Caddy["Caddy HTTPS gateway"]
    Caddy --> Core["Core API :8080"]
    Client --> Secure["Secure API :8081"]
    Client --> Rag["RAG API :8082"]

    Core --> Mongo[("MongoDB")]
    Core --> Redis[("Redis")]
    Secure --> Mongo
    Secure --> Keycloak["Keycloak OIDC"]
    Core --> Worker["Ingestion worker"]
    Worker --> Mongo
    Rag --> Mongo
    Rag --> Ollama["Ollama"]

    Core --> Observability["Prometheus / Grafana / Loki / Alertmanager"]
    Secure --> Observability
    Worker --> Observability

    Core -. planned .-> AWS["AWS adapters"]
    Rag -. planned .-> Atlas["Atlas Vector Search"]
~~~

The application is a modular monolith with separate runtime profiles for the default, secured, and RAG paths. This keeps the local system reviewable while preserving explicit ports for future infrastructure adapters.

For module boundaries, trust boundaries, data flows, and architecture decisions, see [ARCHITECTURE.md](ARCHITECTURE.md).

## Implemented capabilities

### Catalog

- Create and retrieve products stored in MongoDB.
- Redis read-through cache for product lookup.
- Opaque cursor pagination ordered newest-first.
- Partial updates with optimistic locking.
- Validation, conflict, not-found, and dependency-failure responses using RFC Problem Details.

### Users and orders

- Read and update the profile associated with the authenticated JWT subject.
- Create owned orders using immutable product snapshots and calculated totals.
- Enforce controlled order-state transitions.
- Prevent lost updates with optimistic locking.

### Knowledge ingestion and RAG

- Register idempotent plain-text knowledge documents.
- Assign tenant and ACL metadata exclusively on the backend.
- Normalize and split documents through a retryable outbox-style worker.
- Retrieve only tenant- and role-authorized chunks.
- Generate grounded answers with source citations through Spring AI and Ollama.
- Return an explicit service-unavailable contract when the RAG adapter is disabled.

### Security

- OIDC/JWT issuer and audience validation.
- Route-level read/write scopes.
- Tenant and role resolution from trusted token claims.
- Cross-tenant and unauthorized document requests return 404 without confirming resource existence.
- Secure local runtime backed by a provisioned Keycloak realm.

### Observability

- Correlation ID propagation with X-Correlation-Id.
- Prometheus metrics for HTTP traffic and ingestion outcomes.
- Grafana dashboard for availability, request rate, 5xx ratio, p95 latency, JVM resources, and queue state.
- Alert rules for application downtime, elevated server errors, backlog, and terminal ingestion failures.
- Centralized application logs through Promtail and Loki.

## Run the verification path

### Prerequisites

- Docker Desktop
- PowerShell
- Java 21 (only required when running Maven outside the containers)

### 1. Configure and start

~~~powershell
Copy-Item .env.example .env
docker compose up --build
~~~

The example environment file contains local-development defaults. Secrets and generated local data are excluded by [.gitignore](.gitignore).

### 2. Check the platform

| Service | Local URL |
|---|---|
| Core API health | http://localhost:8080/actuator/health |
| Operations dashboard | http://localhost:8080/dashboard/ |
| OpenAPI / Swagger UI | http://localhost:8080/swagger-ui.html |
| Secure API | http://localhost:8081 |
| RAG API | http://localhost:8082 |
| Keycloak | http://localhost:8180 |
| Grafana | http://localhost:3000 |
| Prometheus | http://localhost:9090 |
| Alertmanager | http://localhost:9093 |
| Mailpit | http://localhost:8025 |
| Loki | http://localhost:3100 |
| Portainer | http://localhost:9000 |

### 3. Run automated verification

~~~powershell
.\mvnw.cmd -Prag clean verify

.\scripts\acceptance.ps1
.\scripts\monitoring-acceptance.ps1
.\scripts\platform-services-acceptance.ps1
.\scripts\rag-acceptance.ps1
~~~

The acceptance scripts exercise the local platform after Compose is running. Detailed prerequisites and expected results are documented in [docs/ACCEPTANCE.md](docs/ACCEPTANCE.md).

## Example API flow

Create a product:

~~~powershell
$body = @{
  sku = 'SKU-001'
  name = 'Example product'
  description = 'First vertical slice'
  amount = 1299.00
  currency = 'TWD'
} | ConvertTo-Json

$product = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/products -ContentType application/json -Body $body
~~~

Follow cursor pagination:

~~~powershell
$firstPage = Invoke-RestMethod -Uri 'http://localhost:8080/api/v1/products?limit=2'
$secondPage = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/products?limit=2&cursor=$($firstPage.nextCursor)"
~~~

Update with optimistic locking:

~~~powershell
$update = @{
  name = 'Updated product'
  amount = 1399.00
  currency = 'TWD'
  version = $product.version
} | ConvertTo-Json

Invoke-RestMethod -Method Patch -Uri "http://localhost:8080/api/v1/products/$($product.id)" -ContentType application/json -Body $update
~~~

A malformed cursor returns 400. Updating with an outdated version returns 409.

## Security model

Enable the resource server with:

~~~text
OIDC_ISSUER_URI
OIDC_AUDIENCE
OIDC_TENANT_CLAIM=tenant_id
OIDC_ROLES_CLAIM=roles
APP_SECURITY_ENABLED=true
~~~

| Route | Required authority |
|---|---|
| GET /api/v1/products/** | SCOPE_catalog.read |
| POST or PATCH /api/v1/products/** | SCOPE_catalog.write |
| GET /api/v1/knowledge/documents/** | SCOPE_knowledge.read |
| POST /api/v1/knowledge/documents | SCOPE_knowledge.write |
| POST /api/v1/rag/answers | SCOPE_knowledge.read |
| GET /api/v1/users/me | SCOPE_profile.read |
| PUT /api/v1/users/me | SCOPE_profile.write |
| GET /api/v1/orders/** | SCOPE_orders.read |
| POST or PATCH /api/v1/orders/** | SCOPE_orders.write |

The default API on port 8080 is intentionally convenient for local exploration. The secure API on port 8081 validates tokens from the local Keycloak realm. A production environment must supply its own identity provider and secret management.

## Design decisions worth reviewing

- **Modular monolith first:** business boundaries remain explicit without adding distributed-system overhead before it is justified.
- **Server-owned authorization context:** clients cannot select a tenant or submit ACL filters.
- **Optimistic concurrency:** conflicting writes fail explicitly instead of silently overwriting newer state.
- **Cursor pagination:** avoids offset drift as the product collection changes.
- **Outbox-style ingestion:** document registration and asynchronous chunking have a recoverable, observable boundary.
- **Honest adapter boundaries:** local Ollama retrieval is implemented; Atlas and AWS remain documented extension points.

## Current limits

- Local RAG rebuilds an in-memory SimpleVectorStore from a bounded set of authorized MongoDB chunks per request. It is a local verification path, not the production-scale design.
- MongoDB Atlas Vector Search, persistent production embeddings, and golden-dataset evaluation remain future work.
- AWS SDK integration and live AWS deployment are not part of the active topology.
- The Compose monitoring endpoints are development-only. A production deployment should keep metrics services private and protect Grafana with organization authentication.

See [docs/AWS-ADAPTER-PLAN.md](docs/AWS-ADAPTER-PLAN.md) for the planned cloud boundary.

## Repository guide

| Path | Purpose |
|---|---|
| src/main | Application modules and configuration |
| src/test | Unit and integration verification |
| scripts | Repeatable local acceptance checks |
| monitoring | Prometheus, Grafana, Loki, and alerting configuration |
| keycloak | Local identity-provider configuration |
| docs/ACCEPTANCE.md | Verification guide and expected outcomes |
| ARCHITECTURE.md | Detailed architecture and trust boundaries |
| docs/AWS-ADAPTER-PLAN.md | Explicitly planned, not-yet-implemented cloud integration |

## Tech stack

Java 21 · Spring Boot 4.1 · Spring Security · Spring Data MongoDB · Spring Data Redis · Spring AI · Testcontainers · Docker Compose · Keycloak · Caddy · Prometheus · Grafana · Loki · Alertmanager · Ollama

## Review order

For a focused code review:

1. Start with [ARCHITECTURE.md](ARCHITECTURE.md).
2. Review the product API for caching, cursor pagination, and optimistic locking.
3. Review the security configuration and tenant/role context.
4. Review knowledge registration and the ingestion worker.
5. Run the verification path above and inspect [docs/ACCEPTANCE.md](docs/ACCEPTANCE.md).
