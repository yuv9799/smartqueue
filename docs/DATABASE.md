# Database Guide

**Engine:** PostgreSQL 16
**ORM:** Hibernate (Spring Data JPA, `ddl-auto=update`)
**Connection:** `jdbc:postgresql://localhost:5432/smartqueue_db`
**Password:** via `DB_PASSWORD` environment variable (never hardcoded)

---

## Entities

### QueueEntry

Represents one customer in the queue.

| Column | Type | Nullable | Notes |
|--------|------|----------|-------|
| `id` | BIGSERIAL | no | PK |
| `token` | VARCHAR(20) | no | Unique, format `SQ-NNN` |
| `customer_name` | VARCHAR(255) | no | |
| `item_count` | INTEGER | no | ≥ 0 |
| `payment_method` | VARCHAR(20) | no | CARD / CASH / DIGITAL |
| `priority_type` | VARCHAR(20) | no | REGULAR / EXPRESS / VIP |
| `status` | VARCHAR(20) | no | WAITING / CALLED / SERVING / SERVED / CANCELLED / NO_SHOW |
| `counter_id` | BIGINT | **no** | FK → checkout_counters.id; must be set at join |
| `predicted_service_seconds` | INTEGER | yes | From ML or rule-based |
| `prediction_source` | VARCHAR(50) | yes | `smartqueue_ml_v1` or `RULE_BASED` |
| `estimated_wait_seconds` | INTEGER | yes | Estimated queue wait time |
| `abandonment_probability` | DECIMAL(5,4) | yes | 0–1 scale |
| `arrival_time` | TIMESTAMP | no | |
| `service_started_at` | TIMESTAMP | yes | Set when CALLED |
| `service_completed_at` | TIMESTAMP | yes | Set when SERVED |
| `actual_service_seconds` | INTEGER | yes | Computed on completion |
| `cancelled_at` | TIMESTAMP | yes | Set when CANCELLED / NO_SHOW |

**Indexes:** `token` (unique), `status`, `arrival_time`

---

### CheckoutCounter

One service point.

| Column | Type | Nullable | Notes |
|--------|------|----------|-------|
| `id` | BIGSERIAL | no | PK |
| `name` | VARCHAR(100) | no | Display name |
| `status` | VARCHAR(20) | no | OPEN / CLOSED |

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
CREATE DATABASE smartqueue_db;
CREATE USER smartqueue_user WITH PASSWORD 'your_secure_password';
GRANT ALL PRIVILEGES ON DATABASE smartqueue_db TO smartqueue_user;

-- Hibernate auto-creates tables (ddl-auto=update)
-- No manual migrations needed for development
```

---

## Key constraints

- `queue_entries.counter_id` NOT NULL — counter assignment is mandatory
- Delete order: delete queue entries before deleting a counter
- `queue_entries.token` UNIQUE
- `checkout_counters.status` CHECK IN ('OPEN', 'CLOSED')
- `queue_entries.status` CHECK IN ('WAITING','CALLED','SERVING','SERVED','CANCELLED','NO_SHOW')
