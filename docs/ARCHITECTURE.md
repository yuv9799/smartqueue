# Architecture Overview

## System context

```
                        ┌────────────────────────────────────────┐
                        │          SmartQueue system             │
                        │                                        │
  ┌──────────┐          │   ┌────────────┐      ┌────────────┐  │
  │  Customer │ ──────▶  │   │  Frontend  │      │  Backend   │  │
  │  (browser)│         │   │ (static SPA│─────▶│ (Spring    │  │
  └──────────┘          │   │  JS/HTML)  │      │  Boot 4)   │  │
                        │   └────────────┘      └─────┬──────┘  │
  ┌──────────┐          │                             │ JPA      │
  │  Store   │ ──────▶  │                             ▼          │
  │  Staff / │          │                     ┌────────────┐     │
  │  Manager │         │                     │ PostgreSQL │    │
  └──────────┘          │                     │ (queue +   │     │
                        │                     │  counters) │     │
                        │                     └────────────┘     │
                        │                             ▲           │
                        │                             │ REST      │
                        │                     ┌────────────┐     │
                        │                     │ ML sidecar │    │
                        │                     │ (FastAPI,  │     │
                        │                     │  port 8001) │    │
                        │                     └────────────┘     │
                        └────────────────────────────────────────┘
```

## Layers

1. **Frontend** — static HTML/JS/CSS served by Spring Boot (`/`). No framework.
   - Dashboard, counter panel, active queue, history, analytics
   - Auto-refreshes every 10s; XSS-safe output escaping

2. **REST API** — `QueueController`, `CounterController` expose endpoints under `/api/`.

3. **Services** — `QueueService`, `CounterService`, `AnalyticsService`, `DashboardService`.
   - Business rules: allocation (min-heap over active workload), fair definition
   - Boundary/edge cases (invalid status transition → 409, missing → 404)

4. **Repositories** — Spring Data JPA (`QueueEntryRepository`, `CheckoutCounterRepository`).

5. **Persistence** — PostgreSQL via Hibernate (`ddl-auto=update`). Entities in
   `backend/src/main/java/com/smartqueue/model/`.

6. **ML prediction** — `MlPredictionClient` (RestClient, 2s timeout) calls the
   FastAPI sidecar `POST /predict`. If sidecar unreachable, `CheckoutTimePredictor`
   uses a rule-based fallback.

## Request lifecycle (join)

```
POST /api/queue/join
        │
        ▼
QueueController.joinQueue()
        │
        ▼
QueueService.joinQueue()
        ├── sanity checks (counter status, fields)
        ├── rule-based estimate → allocation (two-pass: pre-allocate, then allocate)
        ├── ML predict → wait/abandonment/service-time (overrides rule-based)
        ├── final counter assignment (min-heap, includes ML service time)
        ├── saveAndFlush → assign token (SQ-%04d) → save
        ▼
QueueAssignmentResponse → Frontend
```

## Key decisions

- **ML as a sidecar** — Java interacts with the real trained XGBoost model via
  an HTTP API rather than loading `.joblib` in-process. Simpler and keeps model
  serving independent of Java classpath.
- **Rule-based fallback** — if the sidecar is down, the queue still functions
  with a deterministic formula (base + items × per-item; clamped 45–900s).
- **Token from DB id** — `SQ-{id:04}` guarantees uniqueness (issued after save).
- **No secrets in code** — `DB_PASSWORD` comes from the environment.

## Security note

No authentication/authorization is implemented (customer + staff share one
interface). For a production rollout, add Spring Security with role-based access.
See README.md Security note.
