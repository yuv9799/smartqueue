# SmartQueue REST API

Base URL: `http://localhost:8080/api`

---

## Queue

### POST /queue/join
Join the queue. Returns assignment response with token, predicted time, and estimated wait.

**Request body:**
```json
{
  "customerName": "Jane Doe",
  "itemCount": 12,
  "paymentMethod": "CARD",
  "priorityType": "REGULAR"
}
```

| Field | Type | Values |
|-------|------|--------|
| `customerName` | string | required |
| `itemCount` | integer | ≥ 0 |
| `paymentMethod` | string | `CARD`, `CASH`, `DIGITAL` |
| `priorityType` | string | `REGULAR`, `EXPRESS`, `VIP` |

**Response 201:**
```json
{
  "id": 42,
  "token": "SQ-0042",
  "customerName": "Jane Doe",
  "itemCount": 12,
  "paymentMethod": "CARD",
  "priorityType": "REGULAR",
  "status": "WAITING",
  "predictedServiceSeconds": 187,
  "predictionSource": "smartqueue_ml_v1",
  "counterId": 1,
  "counterName": "Counter 1",
  "estimatedWaitSeconds": 432,
  "abandonmentRisk": 0.38
}
```

---

### GET /queue/active
Returns all customers in `WAITING` or `CALLED` state (not yet completed/cancelled).

**Response 200:** Array of `QueueEntryResponse` objects.

---

### GET /queue/history
Paginated service history. Filterable by status, counter, and date range.

**Query params:**
| Param | Type | Description |
|-------|------|-------------|
| `status` | string | `WAITING`, `CALLED`, `SERVING`, `SERVED`, `CANCELLED`, `NO_SHOW` |
| `counterId` | integer | Filter by counter |
| `from` | ISO-8601 | Start of date/time range (≥) |
| `to` | ISO-8601 | End of date/time range (≤) |
| `page` | integer | Page number (default 0) |
| `size` | integer | Page size (default 50) |

**Response 200:** Spring `Page<QueueEntryResponse>`.

---

### POST /queue/{entryId}/complete
Mark a customer's service as complete.

**Path:** `entryId` — queue entry ID

**Response 200:** Updated `QueueEntryResponse` with `status: SERVED` and `serviceCompletedAt` timestamp.

---

### POST /queue/{entryId}/cancel
Cancel a customer's queue entry (customer chose not to wait).

**Path:** `entryId` — queue entry ID

**Response 200:** Updated `QueueEntryResponse` with `status: CANCELLED`.

---

### PATCH /queue/{entryId}/reassign
Move a waiting customer to a different open counter. Manager operation.

**Path:** `entryId` — queue entry ID

**Request body:**
```json
{ "newCounterId": 3 }
```

**Responses:**
- `200` — customer successfully reassigned (counterId/counterName updated)
- `404` — entry or counter not found
- `409` — entry is not in `WAITING` status, or target counter is not `OPEN`

---

### POST /queue/counters/{counterId}/next
Call the next waiting customer to a specific counter.

**Path:** `counterId` — target counter ID

**Response 200:** `QueueEntryResponse` for the called customer (status → `CALLED`).
**404** — counter not found.
**409** — no waiting customers in queue.

---

## Counters

### GET /counters
List all checkout counters.

**Response 200:**
```json
[
  { "id": 1, "name": "Counter 1", "status": "OPEN" },
  { "id": 2, "name": "Counter 2", "status": "CLOSED" }
]
```

---

### POST /counters
Create a new counter.

**Request body:**
```json
{ "name": "Express Lane", "status": "OPEN" }
```

**Response 201:** Created counter object.

---

### GET /counters/{id}
Get counter details.

---

## ML Sidecar (`localhost:8001`)

### POST /predict
**Request:**
```json
{
  "basket_items": 15,
  "payment_method": "card",
  "open_counters": 3,
  "queue_position": 5,
  "hour_of_day": 14,
  "day_of_week": "friday"
}
```

**Response 200:**
```json
{
  "predicted_seconds": 227,
  "model_version": "smartqueue_ml_v1"
}
```

**503** when model bundle not loaded (rule-based fallback used by callers).

### GET /health
Returns `{"status": "healthy", "model": "smartqueue_ml_v1"}`.
Returns `{"status": "error", "model": null}` when bundle not loaded.
