# Testing Guide

## Java tests (backend/)

**Location:** `backend/src/test/java/com/smartqueue/`

**Run all:**
```bash
cd backend
DB_PASSWORD=your_password mvn test
```

**Run one class:**
```bash
mvn test -Dtest=QueueFeatureTests
```

### Suite overview

| Test class | Count | What it covers |
|-----------|-------|----------------|
| `QueueFeatureTests` | 12 | All REST endpoints via MockMvc |
| `QueuePersistenceTests` | 4 | JPA repository CRUD + FK constraints |
| `BackendApplicationTests` | 1 | Spring context loads successfully |

**Total: 17 tests**

---

### QueueFeatureTests (12)

| Test | Endpoint | Expected |
|------|----------|----------|
| `joinQueueCreatesEntry` | POST /api/queue/join | 201, token, counterId |
| `getActiveQueue` | GET /api/queue/active | 200, non-empty list after join |
| `getHistoryEmpty` | GET /api/queue/history | 200, empty page |
| `completeCustomer` | POST /api/queue/{id}/complete | 200, status SERVED |
| `cancelCustomer` | POST /api/queue/{id}/cancel | 200, status CANCELLED |
| `getHistoryWithStatus` | GET /api/queue/history?status=SERVED | 200, filtered page |
| `getHistoryWithDateRange` | GET /api/queue/history?from=&to= | 200, date-filtered |
| `callNextCustomer` | POST /api/queue/counters/{id}/next | 200, CALLED status |
| `reassignWaitingCustomerToOpenCounterSucceeds` | PATCH /api/queue/{id}/reassign | 200, new counterId |
| `reassignNonExistentEntryReturns404` | PATCH /api/queue/{id}/reassign | 404 |
| `reassignNonWaitingCustomerReturns409` | PATCH /api/queue/{id}/reassign | 409 |
| `reassignToClosedCounterReturns409` | PATCH /api/queue/{id}/reassign | 409 |

### QueuePersistenceTests (4)
Repository tests validating PostgreSQL FK constraints:
- `saveAndRetrieveEntry` — QueueEntry persisted and loaded
- `cascadeDeleteCounter` — counter with queue entries can't be deleted (FK)
- `entryHasRequiredCounter` — counter_id is NOT NULL
- `cancelEntryHasTimestamp` — cancelledAt set on cancel

### BackendApplicationTests (1)
`contextLoads` — verifies Spring context initializes with all beans.

---

## Python tests (ml_service/)

**Location:** `ml_service/tests/test_api.py`

**Run:**
```bash
cd ml_service
../.venv/bin/python -m pytest tests/ -v
```

**Requires:** `.venv/` active (`pip install -r ml_service/requirements.txt pytest httpx`)

**Test count: 11**

| Test | Description |
|------|-------------|
| `test_health_returns_200_when_bundle_loaded` | GET /health → 200 with model info |
| `test_health_returns_503_when_bundle_not_loaded` | GET /health → 503 when no model |
| `test_predict_returns_200_with_valid_input` | POST /predict → 200 with predicted_seconds |
| `test_predict_returns_422_when_basket_items_missing` | POST /predict → 422 |
| `test_predict_returns_422_when_basket_items_out_of_range` | POST /predict → 422 |
| `test_predict_returns_422_when_payment_method_invalid` | POST /predict → 422 |
| `test_predict_returns_422_when_open_counters_is_zero` | POST /predict → 422 |
| `test_predict_returns_422_when_queue_position_negative` | POST /predict → 422 |
| `test_predict_returns_422_when_hour_out_of_range` | POST /predict → 422 |
| `test_predict_accepts_day_of_week_as_string` | POST /predict → 200 |
| `test_predict_returns_503_when_bundle_not_loaded` | POST /predict → 503 |

---

## Running against a live stack

Start all services, then use `curl`:

```bash
# Join queue
curl -X POST http://localhost:8080/api/queue/join \
  -H "Content-Type: application/json" \
  -d '{"customerName":"Test","itemCount":5,"paymentMethod":"CASH","priorityType":"REGULAR"}'

# List counters
curl http://localhost:8080/api/counters

# Call next
curl -X POST http://localhost:8080/api/queue/counters/1/next

# Complete
curl -X POST http://localhost:8080/api/queue/1/complete
```

**ML sidecar:** `curl http://localhost:8001/health`
**ML predict:** `curl -X POST http://localhost:8001/predict -H "Content-Type: application/json" -d '{"basket_items":10,"payment_method":"card","open_counters":3,"queue_position":2,"hour_of_day":14,"day_of_week":"saturday"}'`
