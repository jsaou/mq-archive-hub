# mq-archive-hub

Archives IBM MQ messages into PostgreSQL and exposes them through a versioned REST API and an Angular consultation UI.

## Structure

```
mq-archive-hub/
├── backend/          # Spring Boot 4 (Java 21) + Dockerfile
├── frontend/         # Angular 22 + Material (list UI)
├── docker-compose.yml
├── .env.example
└── README.md
```

## Stack

- Java 21, Spring Boot 4
- PostgreSQL + Flyway
- IBM MQ (JMS)
- Docker Compose (Postgres + MQ + backend)
- Micrometer + Prometheus
- Angular 22, Angular Material, Signal Forms, Vitest

## Prerequisites

- JDK 21+ (only if running the API outside Docker)
- Maven (or `backend/mvnw`) — only if running the API outside Docker
- Node.js 20+ and npm
- Docker

## Getting started

### Full stack (infra + API in Docker)

```bash
cp .env.example .env
docker compose up -d --build
```

- API: `http://localhost:8080`
- MQ console: `https://localhost:9443`

Infra only (Postgres + MQ), then run the API locally:

```bash
docker compose up -d postgres mq
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### Angular UI

With the API already running:

```bash
cd frontend
npm install
npm start
```

UI: `http://localhost:4200`  
Dev proxy: `/api` → `http://localhost:8080` (`frontend/proxy.conf.json`)

**UI status**

| Route | Status |
|-------|--------|
| `/messages` | List with filters, pagination, status chips |
| `/messages/:id` | Detail with metadata + payload |

Default list sort from the UI: `receivedAt,desc` (aligned with the API default).

## Configuration

All settings are driven by environment variables. Copy `.env.example` to `.env` and adjust as needed.

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/mqarchive` | JDBC URL |
| `DB_USERNAME` | `mqarchive` | DB user |
| `DB_PASSWORD` | `mqarchive` | DB password |
| `MQ_QUEUE_MANAGER` | `QM1` | Queue manager name |
| `MQ_CHANNEL` | `DEV.APP.SVRCONN` | MQ channel |
| `MQ_CONN_NAME` | `localhost(1414)` | MQ connection string |
| `MQ_USER` | `app` | MQ user |
| `MQ_PASSWORD` | `passw0rd` | MQ password |
| `MQ_QUEUE_NAME` | `DEV.QUEUE.1` | Source queue |
| `MQ_DLQ_NAME` | `DEV.QUEUE.2` | Dead-letter queue |
| `MQ_CONCURRENCY` | `3-10` | Listener thread range |
| `API_PORT` | `8080` | Host port for the API container |
| `API_BASE_PATH` | `/api/v1` | API version prefix |
| `API_MAX_PAGE_SIZE` | `100` | Maximum page size |
| `API_DEFAULT_PAGE_SIZE` | `20` | Default page size |

## Tests

### Backend

```bash
cd backend

# unit tests (H2, no Docker)
./mvnw test

# unit + integration tests (Testcontainers — requires Docker)
./mvnw verify
```

- `*Test` / `*Tests` → Surefire, H2 in-memory
- `*IT` → Failsafe, real PostgreSQL via Testcontainers

| Class | Runner | Scope |
|-------|--------|--------|
| `MqMessageRepositoryTest` | Surefire (`@DataJpaTest`, H2) | Persistence, Specs filters, pagination, sort |
| `MessageQueryControllerIT` | Failsafe (Postgres) | REST GET list/get, 200/404, pagination |
| `MqIngestServiceIT` | Failsafe (Postgres) | Ingest success, duplicates, invalid messages not persisted |
| `MqMessageListenerIT` | Failsafe (Postgres) | Listener → DB; poison messages parked on DLQ |
| `ArchiveFlowIT` | Failsafe (Postgres) | E2E: simulated JMS → listener → DB → REST |

IBM MQ broker container IT is left for a later phase (image `icr.io/ibm-messaging/mq`).

### Frontend

```bash
cd frontend
npm test -- --watch=false
```

Vitest + jsdom (`@angular/build:unit-test`):

| Spec | Scope |
|------|--------|
| `app.spec.ts` | App shell |
| `message-api.spec.ts` | List/detail request builders |
| `message-list-page.spec.ts` | List page render, empty/error HTTP states |
| `message-detail-page.spec.ts` | Detail page load + 404 |

## REST API

Base path: `http://localhost:8080/api/v1` (configurable via `API_BASE_PATH`)

### List messages

```
GET /api/v1/messages
```

Query parameters (all optional):

| Parameter | Type | Description |
|-----------|------|-------------|
| `queueName` | string | Filter by source queue name |
| `status` | enum | `RECEIVED` (archived OK), `ERROR` (invalid but archived), `DLQ` (poison — archived + parked on MQ DLQ) |
| `messageId` | string | Exact match on JMSMessageID |
| `correlationId` | string | Exact match on JMSCorrelationID |
| `page` | int | Page number, 0-based (default: 0) |
| `size` | int | Page size (default: 20, max: 100) |
| `sort` | string | Sort field and direction, e.g. `id,asc` or `receivedAt,desc` |

Allowed sort fields: `id`, `messageId`, `correlationId`, `contentType`, `status`, `receivedAt`. Default (API): `receivedAt,desc` when `sort` is omitted.

**Example:**
```
GET /api/v1/messages?queueName=DEV.QUEUE.1&status=RECEIVED&size=10&sort=id,asc
```

**Response (200) — summary (no payload):**
```json
{
  "content": [
    {
      "id": 1,
      "messageId": "ID:414d51...",
      "correlationId": null,
      "contentType": "text/plain",
      "status": "RECEIVED",
      "receivedAt": "2026-07-20T12:00:00Z"
    }
  ],
  "page": {
    "size": 10,
    "number": 0,
    "totalElements": 42,
    "totalPages": 5
  }
}
```

List responses intentionally omit `payload` (and the DB query uses a summary projection that does not select the payload column). Use get-by-id for the full body.

### Get message by ID

```
GET /api/v1/messages/{id}
```

**Response (200) — detail (includes payload):**
```json
{
  "id": 1,
  "messageId": "ID:414d51...",
  "correlationId": null,
  "payload": "...",
  "contentType": "text/plain",
  "status": "RECEIVED",
  "receivedAt": "2026-07-20T12:00:00Z"
}
```

**Responses:**
- `200 OK` — message found
- `404 Not Found` — `{"detail": "Message not found: {id}"}`
- `400 Bad Request` — invalid ID type or invalid sort field

### Error format

All errors follow [RFC 7807 Problem Details](https://www.rfc-editor.org/rfc/rfc7807):

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Invalid sort property: notAField"
}
```

## Actuator

| Endpoint | Description |
|----------|-------------|
| `GET /actuator/health` | Application health |
| `GET /actuator/info` | Application info |
| `GET /actuator/metrics` | Micrometer metrics |
| `GET /actuator/prometheus` | Prometheus scrape endpoint |

### Custom metrics

| Metric | Description |
|--------|-------------|
| `mq.ingest.success` | Messages successfully archived |
| `mq.ingest.duplicate` | Duplicate messages skipped |
| `mq.ingest.failure` | Ingest failures (transient) |
| `mq.ingest.dlq` | Messages parked on DLQ |
