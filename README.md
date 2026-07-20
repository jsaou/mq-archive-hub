# mq-archive-hub

Archives IBM MQ messages into PostgreSQL and exposes them through a versioned REST API.

## Stack

- Java 21, Spring Boot 4
- PostgreSQL + Flyway
- IBM MQ (JMS)
- Docker Compose (Postgres + MQ)
- Micrometer + Prometheus

## Prerequisites

- JDK 21+
- Maven (or `./mvnw`)
- Docker

## Getting started

```bash
cp .env.example .env
docker compose up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

App: `http://localhost:8080`  
MQ console: `https://localhost:9443`

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
| `API_BASE_PATH` | `/api/v1` | API version prefix |
| `API_MAX_PAGE_SIZE` | `100` | Maximum page size |
| `API_DEFAULT_PAGE_SIZE` | `20` | Default page size |

## Tests

```bash
# unit tests (H2, no Docker)
./mvnw test

# unit + integration tests (Testcontainers — requires Docker)
./mvnw verify
```

- `*Test` / `*Tests` → Surefire, H2 in-memory
- `*IT` → Failsafe, real PostgreSQL via Testcontainers

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
| `status` | enum | `RECEIVED`, `PROCESSED`, `ERROR`, `DLQ` |
| `messageId` | string | Exact match on JMSMessageID |
| `correlationId` | string | Exact match on JMSCorrelationID |
| `page` | int | Page number, 0-based (default: 0) |
| `size` | int | Page size (default: 20, max: 100) |
| `sort` | string | Sort field and direction, e.g. `receivedAt,desc` |

**Example:**
```
GET /api/v1/messages?queueName=DEV.QUEUE.1&status=RECEIVED&size=10&sort=receivedAt,desc
```

**Response (200):**
```json
{
  "content": [
    {
      "id": 1,
      "messageId": "ID:414d51...",
      "correlationId": null,
      "payload": "...",
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

### Get message by ID

```
GET /api/v1/messages/{id}
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
