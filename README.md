# mq-archive-hub

Archives IBM MQ messages into PostgreSQL and exposes them through a REST API.

## Stack

- Java 21, Spring Boot 4
- PostgreSQL + Flyway
- IBM MQ (JMS)
- Docker Compose (Postgres + MQ)

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

## Tests

```bash
# unit tests (H2)
./mvnw test

# unit + integration tests (Testcontainers)
./mvnw verify
```

- `*Test` / `*Tests` → Surefire, H2
- `*IT` → Failsafe, Docker containers

## Endpoints

- `GET /actuator/health`
- `GET /actuator/info`
- `GET /actuator/metrics`
