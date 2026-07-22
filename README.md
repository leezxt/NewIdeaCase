# NewIdeaCase Platform

**中文｜** 以 Java 21 與 Spring Boot 4.1 建構、可公開審閱的模組化後端平台。  
**English |** A portfolio-ready modular backend built with Java 21 and Spring Boot 4.1.

NewIdeaCase 展示的不只是 CRUD，而是一套從模組邊界、多租戶安全、可靠背景處理、快取、可觀測性，到容器化本機環境與 RAG 整合的完整後端實作。

NewIdeaCase goes beyond CRUD. It demonstrates clear module boundaries, secure multi-tenant APIs, reliable background processing, caching, observability, containerized local infrastructure, and a local RAG integration.

> **目前狀態 / Project status:** 核心平台、安全 API、本機基礎設施、監控堆疊、知識匯入工作器與本機 RAG adapter 已實作，並可透過 Docker Compose 重現。AWS 部署、AWS SDK 整合與 MongoDB Atlas Vector Search 不列為已完成功能。  
> The core platform, secure API, local infrastructure, monitoring stack, ingestion worker, and local RAG adapter are implemented and reproducible with Docker Compose. AWS deployment, AWS SDK integration, and MongoDB Atlas Vector Search are intentionally not presented as completed work.

## 30 秒重點 / 30-second overview

| 領域 / Area | Repository 中的實作證據 / Evidence |
|---|---|
| 後端設計 / Backend design | 商品、使用者、訂單與知識模組；REST API；RFC Problem Details 錯誤契約 / Product, profile, order, and knowledge modules; REST APIs; RFC Problem Details |
| 資料一致性 / Data consistency | 樂觀鎖、訂單商品快照、游標分頁、冪等文件註冊、Outbox-style worker / Optimistic locking, order snapshots, cursor pagination, idempotency, outbox-style worker |
| 安全 / Security | OIDC/JWT、audience 驗證、scope、後端解析租戶與角色、跨租戶 404 / OIDC/JWT, audience validation, scopes, server-derived tenant and roles, non-disclosing 404s |
| 效能 / Performance | Redis read-through cache 與有界游標查詢 / Redis read-through cache and bounded cursor queries |
| 測試 / Testing | Maven verify、Spring Security Test、MongoDB Testcontainers |
| 維運 / Operations | Actuator、Prometheus、Grafana、Alertmanager、Loki、Promtail、correlation ID、驗收腳本 |
| AI 整合 / AI integration | Spring AI/Ollama、租戶與角色過濾、具來源引用的 grounded answers |

## 專案目的 / Why this project exists

這個專案用來呈現後端從單一 API 成長為可維運平台時所需的工程決策：

- 先以模組化單體保留清楚的業務邊界，避免過早導入微服務複雜度。
- 由後端掌控授權與租戶邊界，不信任 client 提交的 ACL 條件。
- 讓重試、部分失敗與背景任務具有可觀測性。
- 提供可重複執行的本機驗證流程，不只展示架構圖。
- 清楚區分已實作功能與後續雲端整合。

This project demonstrates the engineering decisions required when a backend grows from a single API into an operable platform:

- Preserve clear business boundaries with a modular monolith before introducing distributed-system overhead.
- Keep authorization and tenant boundaries under server control.
- Make retries, partial failures, and background work observable.
- Provide repeatable local verification instead of relying only on architecture claims.
- Separate implemented capabilities from planned cloud integrations.

## 系統架構 / Architecture

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

系統採用模組化單體，並以不同 runtime profile 提供預設、安全與 RAG 執行路徑，使本機環境容易審閱，同時保留明確的基礎設施 adapter 邊界。

The system uses a modular monolith with separate runtime profiles for the default, secured, and RAG paths. This keeps local review practical while preserving explicit infrastructure adapter boundaries.

完整的模組邊界、信任邊界與資料流請參閱 [ARCHITECTURE.md](ARCHITECTURE.md)。  
See [ARCHITECTURE.md](ARCHITECTURE.md) for module boundaries, trust boundaries, data flows, and design decisions.

## 已實作能力 / Implemented capabilities

### 商品模組 / Catalog

- 商品建立與 MongoDB 持久化 / Product creation and MongoDB persistence.
- Redis read-through cache 商品查詢。
- 依建立時間排序的不透明游標分頁 / Opaque cursor pagination ordered newest-first.
- 部分更新與樂觀鎖 / Partial updates with optimistic locking.
- 使用 RFC Problem Details 統一回傳驗證、衝突、找不到資源及依賴失敗。

### 使用者與訂單 / Users and orders

- 依 JWT subject 讀取與更新個人資料 / Read and update the profile associated with the JWT subject.
- 訂單使用不可變商品快照並計算總額 / Orders use immutable product snapshots and calculated totals.
- 控制訂單狀態轉換 / Controlled order-state transitions.
- 使用樂觀鎖避免較新的資料被覆寫 / Optimistic locking prevents lost updates.

### 知識匯入與 RAG / Knowledge ingestion and RAG

- 冪等註冊純文字知識文件 / Idempotent plain-text document registration.
- 租戶與 ACL metadata 僅由後端指派 / Tenant and ACL metadata are assigned only by the backend.
- 透過可重試的 Outbox-style worker 正規化與切分文件。
- 僅檢索租戶與角色允許的 chunks / Retrieval is filtered by tenant and role.
- 使用 Spring AI 與 Ollama 產生附來源引用的 grounded answers。
- RAG adapter 未啟用時回傳明確的 service-unavailable contract。

### 安全 / Security

- OIDC/JWT issuer 與 audience 驗證。
- 路由層級讀寫 scope / Route-level read and write scopes.
- 從可信任 token claims 解析租戶與角色。
- 跨租戶或未授權文件回傳 404，避免洩漏資源是否存在。
- 本機安全 runtime 使用預先配置的 Keycloak realm。

### 可觀測性 / Observability

- 透過 X-Correlation-Id 傳遞 correlation ID。
- HTTP 流量、知識匯入結果與 queue 狀態的 Prometheus metrics。
- Grafana 顯示 availability、request rate、5xx ratio、p95 latency、JVM 資源與 queue 深度。
- 應用程式離線、5xx 升高、queue backlog 與終止失敗的告警規則。
- Promtail 與 Loki 集中式日誌 / Centralized logs through Promtail and Loki.

## 本機驗證 / Local verification

### 前置需求 / Prerequisites

- Docker Desktop
- PowerShell
- Java 21（僅在容器外執行 Maven 時需要 / only for running Maven outside containers）

### 1. 設定並啟動 / Configure and start

~~~powershell
Copy-Item .env.example .env
docker compose up --build
~~~

範例環境檔提供本機開發預設值；secret 與產生的資料已由 [.gitignore](.gitignore) 排除。  
The example environment file contains local-development defaults. Secrets and generated data are excluded by [.gitignore](.gitignore).

### 2. 檢查服務 / Check the services

| 服務 / Service | Local URL |
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

### 3. 執行測試與驗收 / Run tests and acceptance checks

~~~powershell
.\mvnw.cmd -Prag clean verify

.\scripts\acceptance.ps1
.\scripts\monitoring-acceptance.ps1
.\scripts\platform-services-acceptance.ps1
.\scripts\rag-acceptance.ps1
~~~

Compose 啟動後，驗收腳本會檢查本機平台。前置條件與預期結果請參閱 [docs/ACCEPTANCE.md](docs/ACCEPTANCE.md)。  
After Compose starts, the acceptance scripts exercise the local platform. See [docs/ACCEPTANCE.md](docs/ACCEPTANCE.md) for prerequisites and expected results.

## API 操作範例 / Example API flow

建立商品 / Create a product:

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

使用游標取得下一頁 / Follow cursor pagination:

~~~powershell
$firstPage = Invoke-RestMethod -Uri 'http://localhost:8080/api/v1/products?limit=2'
$secondPage = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/products?limit=2&cursor=$($firstPage.nextCursor)"
~~~

以樂觀鎖更新 / Update with optimistic locking:

~~~powershell
$update = @{
  name = 'Updated product'
  amount = 1399.00
  currency = 'TWD'
  version = $product.version
} | ConvertTo-Json

Invoke-RestMethod -Method Patch -Uri "http://localhost:8080/api/v1/products/$($product.id)" -ContentType application/json -Body $update
~~~

格式錯誤的 cursor 回傳 400；使用過期 version 更新則回傳 409。  
A malformed cursor returns 400; an update using an outdated version returns 409.

## 安全模型 / Security model

啟用 resource server / Enable the resource server:

~~~text
OIDC_ISSUER_URI
OIDC_AUDIENCE
OIDC_TENANT_CLAIM=tenant_id
OIDC_ROLES_CLAIM=roles
APP_SECURITY_ENABLED=true
~~~

| 路由 / Route | 權限 / Required authority |
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

8080 的預設 API 供本機快速探索；8081 的安全 API 驗證本機 Keycloak 簽發的 token。正式環境必須提供自己的 identity provider 與 secret management。

The default API on port 8080 supports convenient local exploration. The secure API on port 8081 validates tokens issued by the local Keycloak realm. Production must provide its own identity provider and secret management.

## 值得審閱的設計決策 / Design decisions worth reviewing

- **模組化單體優先 / Modular monolith first:** 在需要之前不引入分散式系統成本。
- **後端掌控授權上下文 / Server-owned authorization context:** client 無法指定租戶或 ACL filter。
- **樂觀並行控制 / Optimistic concurrency:** 衝突寫入明確失敗，不會靜默覆寫新資料。
- **游標分頁 / Cursor pagination:** 避免集合變動時產生 offset drift。
- **Outbox-style ingestion:** 文件註冊與非同步切分之間具有可恢復、可觀測的邊界。
- **誠實的 adapter 邊界 / Honest adapter boundaries:** 本機 Ollama retrieval 已實作；Atlas 與 AWS 維持為明確的後續整合點。

## 目前限制 / Current limits

- 本機 RAG 每次請求會從有限數量、已授權的 MongoDB chunks 重建記憶體 SimpleVectorStore；此流程用於本機驗證，不代表正式環境規模。
- Atlas Vector Search、持久化 production embeddings 與 golden-dataset evaluation 仍為後續工作。
- AWS SDK 整合與正式 AWS 部署不在目前 topology 內。
- Compose monitoring endpoints 僅供開發；正式環境應將 metrics services 保持在 private network，並使用組織驗證保護 Grafana。

- Local RAG rebuilds an in-memory SimpleVectorStore from a bounded set of authorized MongoDB chunks per request. This is a local verification path, not the production-scale design.
- Atlas Vector Search, persistent production embeddings, and golden-dataset evaluation remain future work.
- AWS SDK integration and live AWS deployment are not part of the active topology.
- Compose monitoring endpoints are for development only. Production should keep metrics services private and protect Grafana with organization authentication.

雲端邊界規劃請參閱 [docs/AWS-ADAPTER-PLAN.md](docs/AWS-ADAPTER-PLAN.md)。  
See [docs/AWS-ADAPTER-PLAN.md](docs/AWS-ADAPTER-PLAN.md) for the planned cloud boundary.

## Repository 導覽 / Repository guide

| 路徑 / Path | 用途 / Purpose |
|---|---|
| src/main | 應用程式模組與設定 / Application modules and configuration |
| src/test | 單元與整合測試 / Unit and integration verification |
| scripts | 可重複執行的本機驗收 / Repeatable local acceptance checks |
| monitoring | Prometheus、Grafana、Loki 與告警設定 |
| keycloak | 本機 identity provider 設定 |
| docs/ACCEPTANCE.md | 驗證方式與預期結果 / Verification guide and expected results |
| ARCHITECTURE.md | 詳細架構與信任邊界 / Architecture and trust boundaries |
| docs/AWS-ADAPTER-PLAN.md | 尚未實作的雲端整合規劃 / Planned cloud integration |

## 技術棧 / Tech stack

Java 21 · Spring Boot 4.1 · Spring Security · Spring Data MongoDB · Spring Data Redis · Spring AI · Testcontainers · Docker Compose · Keycloak · Caddy · Prometheus · Grafana · Loki · Alertmanager · Ollama

## 建議審閱順序 / Suggested review order

1. 從 [ARCHITECTURE.md](ARCHITECTURE.md) 了解架構 / Start with the architecture.
2. 審閱商品 API 的快取、游標分頁與樂觀鎖 / Review caching, cursor pagination, and optimistic locking.
3. 審閱安全設定與租戶、角色解析 / Review security and tenant/role context.
4. 審閱知識註冊與 ingestion worker / Review knowledge registration and the ingestion worker.
5. 執行上方驗證流程並參閱 [docs/ACCEPTANCE.md](docs/ACCEPTANCE.md) / Run the verification path and inspect the acceptance guide.
