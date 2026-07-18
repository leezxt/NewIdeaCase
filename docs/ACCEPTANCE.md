# Acceptance Status

Latest complete non-AWS local acceptance: **PASS** at 2026-07-17 03:41 Asia/Taipei.

- Maven: 49 tests passed with `-Prag clean verify`.
- Security: mock JWT verifies `401`, `403`, catalog/profile/order/knowledge scopes, JWT subject/tenant/roles extraction, and tenant/role concealment.
- Compose acceptance: product cursor overlap 0, stale update 409, Redis cache rebuilt after restart.
- Knowledge acceptance: idempotent retry, 3 deterministic chunks, completed outbox, no source content in API responses.
- Runtime: app/MongoDB/Redis running, MongoDB and Redis healthy, app non-root `uid=100`.
- Mongo ACL round trip: non-empty document roles persist to every generated chunk in a real MongoDB Testcontainer.
- Queue state: 0 `FAILED` and 0 `PROCESSING` ingestion tasks after acceptance.
- Monitoring build: Prometheus registry, ingestion metrics, Compose topology, and provisioning files compile; `docker compose config --quiet` passes.
- Monitoring runtime: application metrics found, Prometheus target value `1`, four alert rules loaded, Grafana database `ok`, and dashboard UID `newideacase-platform` provisioned.
- Operations dashboard: `/dashboard/` serves the responsive ECharts surface and reads same-origin Actuator metrics without authentication.
- Platform runtime: Caddy HTTPS/redirect, Keycloak token and secure API, Mailpit/Alertmanager email, Loki/Promtail logs, Grafana Loki datasource, and Portainer `2.27.9` passed.
- Business runtime: user profile persisted the Keycloak `sub`; an owned order preserved product snapshots and totals, transitioned `PENDING -> CONFIRMED`, and rejected a stale version with `409`.
- Local RAG runtime: Ollama `qwen2.5:0.5b`, `nomic-embed-text`, grounded fact retrieval, and source-document citation passed on port `8082`.
- AWS gate: Compose contains no AWS service and the Maven dependency tree contains no AWS SDK.

## Locally accepted

The following behavior is executable and must pass before a release candidate is handed off:

- Spring Boot 4.1.0 application builds with Java 21 and the Spring AI 2.0.0 Maven profile.
- Docker Compose starts the application, MongoDB replica set, and authenticated Redis.
- Product create/get/list/update, cursor pagination, Redis cache, validation, and optimistic locking work end to end.
- Knowledge registration is tenant-scoped, size-limited, source-private, and idempotent by SHA-256 checksum.
- Knowledge documents and chunks persist backend-derived `allowedRoles`; role or tenant mismatches are concealed as `404`.
- MongoDB transaction creates the knowledge document and outbox task atomically.
- The worker atomically claims tasks, uses a lease, retries failures, recovers expired leases, and dead-letters terminal failures.
- Text normalization and overlapping chunks are deterministic. Chunk IDs and unique indexes make replay idempotent.
- Documents stop at `CHUNKED`; they are never reported as `READY` before embeddings exist.
- OpenAPI matches the implemented routes. Port `8080` returns the disabled RAG `503`; port `8082` serves the enabled local RAG adapter.
- Local RAG filters MongoDB chunks by backend tenant/roles before embedding, retrieves with Spring AI `SimpleVectorStore`, treats context as untrusted, and returns citations.
- No AWS SDK or live AWS resource is required.
- `/actuator/prometheus` exposes JVM, HTTP, and knowledge-ingestion metrics.
- Prometheus reports the `newideacase-app` target as `up` and loads the application alert rules.
- Grafana reports a healthy database and provisions dashboard UID `newideacase-platform`.
- Caddy serves local HTTPS and redirects HTTP; Keycloak issues subject/tenant/roles/scopes used by the secure app.
- User profile and order APIs enforce JWT subject ownership and route scopes.
- Alertmanager delivers an acceptance alert to Mailpit; Promtail ships application logs to Loki; Portainer authenticates against its persisted local state.

Run from the project root:

```powershell
.\mvnw.cmd -B -ntp -Prag verify
docker compose up -d --build app
.\scripts\acceptance.ps1
.\scripts\monitoring-acceptance.ps1
.\scripts\platform-services-acceptance.ps1
.\scripts\rag-acceptance.ps1
docker compose config --quiet
.\mvnw.cmd -B -ntp dependency:tree "-Dincludes=software.amazon.awssdk:*"
```

## Deferred external acceptance

These items require infrastructure or credentials that are intentionally not connected yet:

- Production OIDC issuer, organization identity mapping, and production `401`/`403` flows. Local Keycloak JWT validation and ACL enforcement are accepted.
- MongoDB Atlas Vector Search index and isolated Atlas integration tests.
- Production embedding/chat provider credentials, persistent-vector behavior, and golden-dataset relevance/faithfulness evaluation. Local grounded answers and citations are accepted with Ollama.
- Object-storage adapter, malware scanning, backup/restore drill, production monitoring hosting, notification routing, and organization SSO for Grafana.
- Every AWS service remains paused by project decision; no AWS deployment is part of this acceptance.

Do not mark the RAG system production-ready until every deferred item has evidence from the target environment.
