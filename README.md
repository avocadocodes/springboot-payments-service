# payments-service

## Problem Statement

Payment APIs fail in interesting ways: the same request is submitted twice during a network retry,
the downstream gateway times out under load, two concurrent refunds race on the same record, and
every one of these produces money-movement bugs that are expensive to reconcile.
This service is a case study in building a payment backend that is correct by construction:
idempotency keys prevent double-charges at the API surface, a Resilience4j circuit breaker
absorbs gateway brownouts without cascading, and optimistic locking on the Payment entity makes
concurrent state transitions safe without row-level locks.

## Architecture

```
                         ┌─────────────────────────────────────────┐
  HTTP Client            │            payments-service              │
  ─────────────          │                                          │
                         │  ┌────────────┐    ┌──────────────────┐ │
  POST /payments  ──────►│  │ Controller │───►│  PaymentService  │ │
  GET  /payments/{id}    │  └────────────┘    └────────┬─────────┘ │
  POST /payments/{id}    │                             │            │
       /refund           │          ┌──────────────────┴──────┐    │
                         │          │                          │    │
                         │  ┌───────▼──────┐   ┌─────────────▼──┐ │
                         │  │ Idempotency  │   │ PaymentGateway │ │
                         │  │  Service     │   │    Client      │ │
                         │  └───────┬──────┘   └───────┬────────┘ │
                         │          │                   │           │
                         └──────────┼───────────────────┼───────────┘
                                    │                   │
                         ┌──────────▼──┐    ┌───────────▼──────────┐
                         │    Redis    │    │  Simulated Gateway   │
                         │ (key→id TTL)│    │  + Retry / CB        │
                         └─────────────┘    └──────────────────────┘
                                    │
                         ┌──────────▼──────────┐
                         │     PostgreSQL       │
                         │   payments table     │
                         │  (Flyway V1__init)   │
                         └─────────────────────┘
```

## Key Design Decisions

### Idempotency Keys
A client-supplied `Idempotency-Key` header is stored in Redis as `idempotency:<key> → paymentId`
with a 24-hour TTL. Before charging the gateway, the service checks Redis; if a mapping exists,
it fetches and returns the original payment without re-processing. This makes `POST /payments`
safe to retry after any network failure without risk of double-charging.

### Circuit Breaker + Retry
`PaymentGatewayClient` is annotated with `@Retry` (3 attempts, 200 ms back-off) and
`@CircuitBreaker` (opens at 50 % failure rate over a 10-call window, half-opens after 10 s).
Both use the same `paymentGateway` configuration in `application.yml`. A shared fallback method
returns a failed `GatewayResponse`, which the service persists as a `FAILED` payment rather than
throwing — keeping the system in a known state even during gateway outages.

### Optimistic Locking
`Payment` carries a `@Version Long version` column. JPA increments this on every UPDATE and
raises `OptimisticLockingFailureException` if two concurrent transactions both read the same
version. This prevents a refund from racing with a status update without taking a DB-level lock
on every read path.

### Layered Architecture
```
controller   — request/response mapping, validation, HTTP status
service      — business rules (idempotency check, state transitions, gateway calls)
gateway      — external integration, resilience annotations
repository   — Spring Data JPA, no custom SQL
domain       — JPA entities, enums
dto          — request/response POJOs; no domain leakage through the HTTP boundary
exception    — typed exceptions + global RFC 7807 handler
config       — Spring beans (Redis template, OpenAPI spec)
```

## How to Run

**Prerequisites:** Docker and Docker Compose.

```bash
git clone <repo>
cd payments-service

# Build the image and start all services
docker compose up --build
```

The app starts on port 8080. PostgreSQL and Redis are started first and health-checked
before the app container launches.

Swagger UI: http://localhost:8080/swagger-ui.html
Prometheus metrics: http://localhost:8080/actuator/prometheus
Health: http://localhost:8080/actuator/health

## API Examples

### Create a payment

```bash
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: order-7842-attempt-1" \
  -d '{
    "amount": "49.99",
    "currency": "USD",
    "customerId": "cust-1001",
    "method": "CARD"
  }'
```

Response `201 Created`:
```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "amount": 49.99,
  "currency": "USD",
  "customerId": "cust-1001",
  "method": "CARD",
  "status": "SUCCEEDED",
  "gatewayReference": "GW-A1B2C3D4",
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:30:00Z"
}
```

Repeating the same `Idempotency-Key` returns `200 OK` with the identical body — gateway is not called again.

### Fetch a payment

```bash
curl http://localhost:8080/api/v1/payments/3fa85f64-5717-4562-b3fc-2c963f66afa6
```

### Refund a payment

```bash
curl -X POST http://localhost:8080/api/v1/payments/3fa85f64-5717-4562-b3fc-2c963f66afa6/refund
```

Returns `422 Unprocessable Entity` if the payment is not in `SUCCEEDED` state.

### Validation error shape (RFC 7807)

```json
{
  "type": "https://payments.nikita.com/errors/validation-failed",
  "title": "Validation Failed",
  "status": 400,
  "detail": "One or more fields failed validation",
  "instance": "/api/v1/payments",
  "timestamp": "2024-01-15T10:30:00Z",
  "errors": [
    { "field": "currency", "message": "currency must be uppercase ISO 4217" }
  ]
}
```

## Running Tests Locally

```bash
mvn test
```

Tests use H2 (in-memory, PostgreSQL-compat mode) and Mockito. No Docker required.
Redis is excluded via `spring.autoconfigure.exclude` in `application-test.yml`; the
`IdempotencyService` is replaced with an in-memory stub in `TestRedisConfig`.

## Metrics / SLOs

| Metric | Target |
|---|---|
| `POST /payments` p95 latency | < 150 ms (excluding gateway simulation jitter) |
| `GET /payments/{id}` p95 latency | < 20 ms |
| Idempotent duplicate charge rate | 0 % |
| Circuit breaker open state false-positive rate | < 1 % at steady state |
| Payment in unknown state after gateway timeout | 0 % (fallback always writes FAILED) |

Key Prometheus metrics exposed:

- `resilience4j_circuitbreaker_state{name="paymentGateway"}` — circuit state
- `resilience4j_retry_calls_total{name="paymentGateway"}` — retry counts by outcome
- `http_server_requests_seconds` — per-endpoint latency histograms
- `hikaricp_connections_active` — DB pool utilization
