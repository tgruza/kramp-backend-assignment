# Product Information Aggregator

A backend service that aggregates product information from multiple upstream services into a single, market-aware response for a B2B e-commerce platform serving dealers and workshops across European markets.

## How to Run

### Prerequisites

- **Java 21+** (required for virtual threads, JEP 444)
- **Maven 3.8+** (wrapper included)

### Build and run

```bash
# Build and run tests
./mvnw clean verify

# Start the service
./mvnw spring-boot:run
```

On Windows:
```bash
mvnw.cmd clean verify
mvnw.cmd spring-boot:run
```

The service starts on `http://localhost:8080`.

### Try it out

```bash
# Basic product lookup (Dutch market)
curl "http://localhost:8080/api/v1/products/PART-001?market=nl-NL"

# With customer context (German market, Gold customer with 15% discount)
curl "http://localhost:8080/api/v1/products/PART-001?market=de-DE&customerId=CUST-GOLD"

# Polish market
curl "http://localhost:8080/api/v1/products/PART-002?market=pl-PL"

# Health check (includes circuit breaker status)
curl "http://localhost:8080/actuator/health"

# Prometheus metrics
curl "http://localhost:8080/actuator/prometheus"
```

### Available test data

| Product IDs | Markets | Customer IDs |
|-------------|---------|--------------|
| `PART-001` (Hydraulic Cylinder) | `nl-NL` (Dutch) | `CUST-GOLD` (15% discount) |
| `PART-002` (Air Filter) | `de-DE` (German) | `CUST-SILVER` (10% discount) |
| `PART-003` (V-Belt) | `pl-PL` (Polish) | `CUST-BRONZE` (5% discount) |

### Run tests

```bash
# All tests (unit + integration)
./mvnw test

# 29 tests across 4 test classes:
#   - ParallelAggregationOrchestratorTest (8 unit tests)
#   - GlobalExceptionHandlerTest          (7 unit tests)
#   - ProductRequestTest                  (3 unit tests)
#   - ProductControllerIntegrationTest    (6 integration tests)
#   - ProductAggregatorApplicationTests   (1 context load test)
```

---

## Key Design Decisions and Trade-offs

### 1. Aggregator Pattern (Backend-for-Frontend)

The service acts as a **single entry point** that collapses 4 upstream API calls into 1 response. This directly addresses the three stated problems:

| Problem | Solution |
|---------|----------|
| Slow page loads (especially mobile in rural areas) | Parallel upstream calls + single response reduces round trips |
| Inconsistent behavior when services are slow/unavailable | Centralized timeout and graceful degradation logic |
| Duplicated logic across client applications | All clients (web, mobile, dealer tools) consume one endpoint |

### 2. Java 21 Virtual Threads over Reactive (WebFlux)

**Chosen:** `CompletableFuture` with virtual thread executor (JEP 444)
**Alternative considered:** Spring WebFlux with `Mono`/`Flux`

| Aspect | Virtual Threads (chosen) | WebFlux |
|--------|-------------------------|---------|
| Code readability | Imperative, easy to debug | Reactive chains, steep learning curve |
| Performance | Excellent for I/O-bound work | Excellent under extreme load |
| Testability | Standard JUnit + Mockito | Requires `StepVerifier`, reactive test utilities |
| Team onboarding | Any Java developer | Requires reactive programming expertise |
| Debugging | Standard stack traces | Fragmented stack traces across reactive operators |

**Trade-off:** Virtual threads require Java 21+, but the code reads like sequential blocking code while achieving concurrency benefits of non-blocking I/O. For an aggregation service that is primarily I/O-bound (waiting on upstream calls), this is a strong fit. The team does not need reactive expertise to maintain the codebase.

### 3. Required vs Optional Service Distinction

Services are categorized by business criticality:

| Service | Category | On Failure | Rationale |
|---------|----------|------------|-----------|
| **Catalog** | **Required** | Entire request fails (HTTP 502) | Cannot display a product without its basic info |
| Pricing | Optional | Product returned, pricing `null`, warning added | Customer can still view the product and contact sales |
| Availability | Optional | Product returned, stock `null`, warning added | Customer can still see the product and request a quote |
| Customer | Optional | Standard non-personalized response | Product is still displayable without customer context |

Implementation: all calls execute in parallel via fan-out. At the join point, only catalog failure propagates as an error. Optional service failures produce `null` data with a warning in the response metadata.

### 4. Resilience Strategy

- **Per-service timeouts:** Each upstream service has an individually configurable timeout (default 500ms via `application.yml`). A slow pricing service cannot block the entire response.
- **Circuit breaker (Resilience4j):** When failures exceed 50% in a sliding window of 10 calls, the circuit opens and fast-fails. This protects against cascading failures and gives upstream services time to recover.
- **Graceful degradation:** Optional service failures are caught and the response is assembled with available data, plus metadata indicating which sources were unavailable.

**Why Resilience4j over Hystrix:** Hystrix is in maintenance mode (Netflix archived it in 2018). Resilience4j is the modern, lightweight replacement with native Spring Boot 3 support, functional API, and better modularity.

### 5. Mock Realism

Each mock upstream service simulates realistic distributed system behavior:

- **Latency jitter:** Random delay between 50% and 200% of the stated typical latency
- **Probabilistic failures:** Random failures based on the stated reliability percentages (e.g., 98% for Availability means ~2% of calls fail)
- **Market-aware responses:** Localized product names and descriptions per market (Dutch, German, Polish)
- **Structured data:** Realistic pricing with currency conversion (EUR, PLN), customer discount tiers, per-market warehouse locations and delivery estimates

This demonstrates understanding that real upstream services exhibit exactly this kind of variance: they are not uniformly fast or reliable.

### 6. SOLID Principles

| Principle | Application |
|-----------|-------------|
| **Single Responsibility** | `ProductController` handles HTTP concerns only. `ProductAggregatorService` manages circuit breaker. `ParallelAggregationOrchestrator` handles fan-out/fan-in orchestration. `GlobalExceptionHandler` centralizes error responses. Each mock service simulates one upstream. |
| **Open/Closed** | Adding a new data source (e.g., Related Products) requires creating a new interface + implementation and adding one `callOptional` invocation. Existing service contracts, error handling, and response assembly logic remain untouched. |
| **Liskov Substitution** | Mock services implement the same upstream interfaces that production HTTP clients would. The orchestrator depends only on these interfaces. Swapping mocks for real implementations requires zero code changes in the orchestrator. |
| **Interface Segregation** | Each upstream service has its own focused interface with a single method. The orchestrator interface exposes only `orchestrate(ProductRequest)`. No client is forced to depend on methods it does not use. |
| **Dependency Inversion** | The orchestrator depends on abstract service interfaces (`CatalogService`, `PricingService`, etc.), not on concrete mock implementations. The `AggregationOrchestrator` interface decouples the service layer from the orchestration strategy. |

### 7. Observability and Production Readiness

- **Request correlation ID:** Every request gets a unique `X-Correlation-Id` header (or propagates one if provided). This ID appears in all log entries via MDC and in error response bodies, enabling end-to-end request tracing.
- **MDC propagation to virtual threads:** The correlation ID is explicitly propagated from the request thread to virtual threads executing upstream calls, ensuring log coherence across parallel operations.
- **Spring Actuator:** Health endpoint with circuit breaker status, Prometheus metrics endpoint for monitoring.
- **Response metadata:** Every successful response includes timing, per-service status (`SUCCESS`/`FAILED`/`TIMEOUT`/`SKIPPED`), and warnings. This is invaluable for debugging in production without checking logs.
- **Structured logging:** Consistent log levels (DEBUG for per-service timings, INFO for request lifecycle, WARN for degraded operation, ERROR for failures).

### 8. API Design

- **Versioned endpoint** (`/api/v1/products/{id}`) for backward-compatible evolution
- **Market as required query parameter** (not path segment), because the same product exists across markets
- **Customer ID as optional parameter**, following the acceptance criteria
- **`@JsonInclude(NON_NULL)`** so missing optional data does not clutter the JSON response
- **Validation** with clear error messages for missing or malformed parameters (e.g., market format `xx-XX`)

### 9. Test Strategy

| Layer | Class | Tests | What it verifies |
|-------|-------|-------|------------------|
| Unit | `ParallelAggregationOrchestratorTest` | 8 | Fan-out/fan-in logic, each failure scenario, metadata assembly |
| Unit | `GlobalExceptionHandlerTest` | 7 | All exception types map to correct HTTP status, error body structure, correlation ID |
| Unit | `ProductRequestTest` | 3 | `hasCustomerId()` with null, blank, and valid values |
| Integration | `ProductControllerIntegrationTest` | 6 | Full Spring context: happy paths (3 markets), 502 for unknown product, 400 for missing/invalid params |
| Smoke | `ProductAggregatorApplicationTests` | 1 | Spring context loads successfully |

The unit tests use Mockito for service dependencies and run in milliseconds. The integration tests boot the full Spring context with mock upstream services and verify the HTTP contract end-to-end.

---

## What I Would Do Differently With More Time

1. **Caching** (Caffeine or Redis) for catalog data, which changes infrequently. This would reduce latency and upstream load significantly.
2. **Retry with exponential backoff** on the required catalog call before failing, using Resilience4j's `@Retry`.
3. **OpenAPI/Swagger** documentation via `springdoc-openapi` for automatic API docs at `/swagger-ui.html`.
4. **gRPC implementation** as a bonus endpoint for internal service-to-service communication, using `grpc-spring-boot-starter`. The REST endpoint would remain for external/frontend clients.
5. **Contract tests** (Pact) to validate upstream service contracts, so mock behavior stays aligned with real services.
6. **Rate limiting** per client to protect upstream services from being overwhelmed.
7. **Docker Compose** setup with containerized service and simulated external endpoints.
8. **Load testing** with Gatling to validate virtual thread performance under high concurrency.
9. **Structured JSON logging** (Logback + logstash-encoder) for production log aggregation (ELK/Loki).

---

## Design Question: Option A (Related Products Service)

> The Assortment team wants to add a "Related Products" service (200ms latency, 90% reliability). How would your design accommodate this? Should it be required or optional?

### How the current design accommodates this

The current architecture makes adding a new data source straightforward:

1. **Define interface:** Create `RelatedProductsService` with a `getRelatedProducts(String productId, String market)` method.
2. **Implement mock:** Create `MockRelatedProductsService` with 200ms typical latency and 90% reliability simulation.
3. **Add response field:** Add `List<RelatedProduct> relatedProducts` to `AggregatedProductResponse`.
4. **Wire it in:** Add one `callOptional("RelatedProductsService", ...)` invocation in `ParallelAggregationOrchestrator`.
5. **Configure timeout:** Add `aggregator.timeout.related-products-ms: 300` to `application.yml`.

This touches 4-5 files, all additive changes. No existing service contracts, error handling, or response assembly logic needs modification. The Open/Closed principle in action.

### Required or optional?

**Optional, without question.** At 90% reliability, making it required would mean approximately 1 in 10 product page views fails entirely. For a B2B e-commerce platform where dealers need to look up parts quickly, this failure rate is unacceptable.

Related products are supplementary content. The core purchase flow (view product -> check price -> check availability -> buy) does not depend on recommendations. When the service is down, the product page simply omits the "You might also like" section.

### Additional considerations for this service

Given the higher latency (200ms) and lower reliability (90%) compared to other upstream services:

- **Aggressive timeout** (~300ms) to prevent it from delaying the overall response
- **Dedicated circuit breaker** that trips quickly (e.g., 3 failures in a window of 5)
- **Local cache** (Caffeine, 5-minute TTL) of recent results, so we can serve slightly stale recommendations when the service is flaky. Related products change infrequently enough that stale data is preferable to no data.
