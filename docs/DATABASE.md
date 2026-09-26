# Database Guide

**Engine:** PostgreSQL 16
**ORM:** Hibernate (Spring Data JPA, `ddl-auto=update`)
**Connection:** `jdbc:postgresql://localhost:5432/smartqueue`
**Password:** via `DB_PASSWORD` environment variable (never hardcoded)

---

## Entities

### QueueEntry

Represents one customer in the queue. A counter is assigned at join time
(`counter_id` is NOT NULL).

| Column | Type | Nullable | Notes |
|--------|------|----------|-------|
| `id` | BIGSERIAL | no | PK |
| `token` | VARCHAR(20) | no | Unique, format `SQ-NNNN` |
| `customer_name` | VARCHAR(60) | no | |
| `item_count` | INTEGER | no | ≥ 0 |
| `payment_method` | VARCHAR(10) | no | **UPI / CARD / CASH** |
| `priority_type` | VARCHAR(15) | no | **REGULAR / ASSISTANCE** |
| `status` | VARCHAR(15) | no | **WAITING / SERVING / COMPLETED / CANCELLED** |
| `counter_id` | BIGINT | **no** | FK → checkout_counters.id; set at join |
| `predicted_service_seconds` | INTEGER | no | From ML or rule-based |
| `prediction_source` | VARCHAR(50) | no | `smartqueue_ml_v1` or `RULE_BASELINE_V1` |
| `estimated_wait_seconds` | BIGINT | yes | ML-predicted wait time (seconds) |
| `abandonment_probability` | DOUBLE | yes | ML-predicted abandonment (0–1) |
| `arrival_time` | TIMESTAMP | no | UTC when customer joined |
| `assigned_at` | TIMESTAMP | no | UTC when first assigned a counter |
| `service_started_at` | TIMESTAMP | yes | Set when status → SERVING |
| `service_completed_at` | TIMESTAMP | yes | Set when status → COMPLETED |
| `actual_service_seconds` | INTEGER | yes | Computed on completion |
| `actual_wait_seconds` | INTEGER | yes | Computed when service starts |
| `cancelled_at` | TIMESTAMP | yes | Set when status → CANCELLED |

**Indexes:** `token` (unique), `status`, `arrival_time`

---

### CheckoutCounter

One service point (checkout lane).

| Column | Type | Nullable | Notes |
|--------|------|----------|-------|
| `id` | BIGSERIAL | no | PK |
| `name` | VARCHAR(100) | no | Display name |
| `status` | VARCHAR(10) | no | **OPEN / CLOSED** |

**Constraint:** `counter_id` in `queue_entries` is NOT NULL. To delete a counter, first delete all its queue entries.

---

## Relationships

```
checkout_counters (1) ←── (many) queue_entries
```

- A `CheckoutCounter` can have many `QueueEntry` records
- A `QueueEntry` must have exactly one `CheckoutCounter` (counter_id NOT NULL)
- `counter_id` cannot be null; to reassign, update the FK directly

---

## Database setup

```sql
CREATE DATABASE smartqueue;
CREATE USER smartqueue_user WITH PASSWORD 'your_secure_password';
GRANT ALL PRIVILEGES ON DATABASE smartqueue TO smartqueue_user;
```

Run the app with `DB_PASSWORD=your_secure_password`. Hibernate auto-creates/
updates tables on startup (`ddl-auto=update`).

---

## Key constraints

- `queue_entries.counter_id` NOT NULL — counter assignment is mandatory
- Delete order: delete queue entries before deleting a counter
- `queue_entries.token` UNIQUE
- `checkout_counters.status` IN ('OPEN', 'CLOSED')
- `queue_entries.status` IN ('WAITING', 'SERVING', 'COMPLETED', 'CANCELLED')
- `queue_entries.payment_method` IN ('UPI', 'CARD', 'CASH')
- `queue_entries.priority_type` IN ('REGULAR', 'ASSISTANCE')
