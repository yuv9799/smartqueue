[LEGACY — pre-merge architecture, not maintained. Current stack: Java/Spring Boot + PostgreSQL + ML sidecar]

# PostgreSQL Database Migration — SmartQueue

## Overview

This document describes the migration of the SmartQueue Java backend's
persistence layer from **H2 file-based database** to **PostgreSQL server**.

This is an **isolated development workstream**. It is not the team's current
main working database unless the main project has been changed accordingly.

---

## 1. Original Database

| Property | Value |
|----------|-------|
| Engine | H2 (file-based) |
| JDBC URL | `jdbc:h2:file:./data/smartqueue` |
| Location | `backend/data/smartqueue.mv.db` |
| Username | `sa` |
| Password | _(empty)_ |
| Console | H2 Console at `/h2-console` |
| DDL mode | `update` (Hibernate auto-creates tables) |

---

## 2. New Database

| Property | Value |
|----------|-------|
| Engine | PostgreSQL 16 (server-based) |
| JDBC URL | `jdbc:postgresql://localhost:5432/smartqueue` |
| Host | configurable via `DB_HOST` env var (default: `localhost`) |
| Port | configurable via `DB_PORT` env var (default: `5432`) |
| Database name | configurable via `DB_NAME` env var (default: `smartqueue`) |
| Username | configurable via `DB_USER` env var (default: `smartqueue_user`) |
| Password | configurable via `DB_PASSWORD` env var (no default — must be set) |
| Schema | `public` (default) |
| DDL mode | `update` (Hibernate auto-creates tables) |
| Dialect | `org.hibernate.dialect.PostgreSQLDialect` |

---

## 3. Why This Isolated Copy

The original team project (`main` branch) uses H2 for the portable Friday demo.
PostgreSQL is the target for the final submission (multi-user, real deployment).

This copy was created specifically to:
- Implement and validate the PostgreSQL migration without affecting the main project
- Produce a tested, working persistence layer contribution
- Allow the team to review the changes before merging them into the main project

**This file documents what was done in this isolated copy only.**

---

## 4. Configuration Changes

### Before (`application.properties`)

```properties
spring.datasource.url=jdbc:h2:file:./data/smartqueue
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.h2.console.enabled=true
spring.h2.console.path=/h2-console
```

### After (`application.properties`)

```properties
spring.datasource.url=jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:smartqueue}
spring.datasource.driver-class-name=org.postgresql.Driver
spring.datasource.username=${DB_USER:smartqueue_user}
spring.datasource.password=${DB_PASSWORD:}
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
spring.h2.console.enabled=false
```

Credentials are provided via environment variables (never hardcoded):
- `DB_HOST` — PostgreSQL server host (default: `localhost`)
- `DB_PORT` — PostgreSQL server port (default: `5432`)
- `DB_NAME` — database name (default: `smartqueue`)
- `DB_USER` — database user (default: `smartqueue_user`)
- `DB_PASSWORD` — database password (no default; **must be set**)

### `pom.xml` Changes

| Dependency | Change |
|-----------|--------|
| `spring-boot-h2console` | **Removed** (no H2 console with PostgreSQL) |
| `h2` | **Removed** (not needed; PostgreSQL driver remains) |
| `postgresql` | **Kept** (was already in pom.xml, now the only database driver) |

---

## 5. Entity / Table Mapping

### `checkout_counters` (Java entity → PostgreSQL table)

| Java field | PostgreSQL column | Type |
|-----------|-------------------|------|
| `id` (auto-generated) | `id` | `BIGSERIAL` / `BIGINT` |
| `name` | `name` | `VARCHAR(50)` |
| `status` | `status` | `VARCHAR(10)` |

```sql
CREATE TABLE checkout_counters (
    id     BIGSERIAL PRIMARY KEY,
    name   VARCHAR(50) NOT NULL UNIQUE,
    status VARCHAR(10) NOT NULL
);
```

### `queue_entries` (Java entity → PostgreSQL table)

| Java field | PostgreSQL column | Type |
|-----------|-------------------|------|
| `id` (auto-generated) | `id` | `BIGSERIAL` / `BIGINT` |
| `token` | `token` | `VARCHAR(20)` UNIQUE |
| `customerName` | `customer_name` | `VARCHAR(60)` |
| `itemCount` | `item_count` | `INT` |
| `paymentMethod` | `payment_method` | `VARCHAR(10)` |
| `priorityType` | `priority_type` | `VARCHAR(15)` |
| `status` | `status` | `VARCHAR(15)` |
| `predictedServiceSeconds` | `predicted_service_seconds` | `INT` |
| `predictionSource` | `prediction_source` | `VARCHAR(50)` |
| `counter` (entity reference) | `counter_id` | `BIGINT` FK → `checkout_counters.id` |
| `arrivalTime` | `arrival_time` | `TIMESTAMP` |
| `assignedAt` | `assigned_at` | `TIMESTAMP` |
| `serviceStartedAt` | `service_started_at` | `TIMESTAMP` |
| `serviceCompletedAt` | `service_completed_at` | `TIMESTAMP` |
| `actualServiceSeconds` | `actual_service_seconds` | `INT` |

```sql
CREATE TABLE queue_entries (
    id                       BIGSERIAL PRIMARY KEY,
    token                    VARCHAR(20) UNIQUE,
    customer_name            VARCHAR(60) NOT NULL,
    item_count               INT NOT NULL,
    payment_method           VARCHAR(10) NOT NULL,
    priority_type            VARCHAR(15) NOT NULL,
    status                   VARCHAR(15) NOT NULL,
    predicted_service_seconds INT NOT NULL,
    prediction_source        VARCHAR(50) NOT NULL,
    counter_id               BIGINT NOT NULL REFERENCES checkout_counters(id),
    arrival_time             TIMESTAMP NOT NULL,
    assigned_at              TIMESTAMP NOT NULL,
    service_started_at       TIMESTAMP,
    service_completed_at     TIMESTAMP,
    actual_service_seconds   INT
);
```

Hibernate creates these tables automatically at startup (via `ddl-auto=update`).
The exact SQL Hibernate generates is visible in the application logs when
`spring.jpa.show-sql=true` is set.

---

## 6. Relationships

```
checkout_counters (1) ──── (many) queue_entries
     id (PK)  ←───────────── counter_id (FK, NOT NULL)
```

This is a **one-to-many** relationship implemented as:
- `@ManyToOne(fetch = FetchType.LAZY, optional = false)` on `QueueEntry.counter`
- `@JoinColumn(name = "counter_id", nullable = false)`

**Constraint:** `queue_entries.counter_id` references `checkout_counters.id`,
ensuring every queue entry is assigned to an existing counter.

---

## 7. How Persistence Works

### Startup
1. Spring Boot starts, connects to PostgreSQL
2. Hibernate sees empty database, creates tables via `ddl-auto=update`
3. `DataInitializer` (`CommandLineRunner`) creates 3 counters:
   - "Counter 1" (OPEN)
   - "Counter 2" (OPEN)
   - "Counter 3" (OPEN)

### Join Queue (POST /api/queue/join)
1. `QueueService.joinQueue()` receives `JoinQueueRequest`
2. `CheckoutTimePredictor` computes predicted service time (in-memory formula)
3. `CounterAllocator` queries counters from PostgreSQL, selects least-loaded
4. New `QueueEntry` object created in memory
5. **`saveAndFlush()` → INSERT to PostgreSQL**, Hibernate returns entity with `id`
6. Token generated as `"SQ-%04d".formatted(id)` — uses the real DB-generated ID
7. **`save()` → UPDATE** stores the token in PostgreSQL
8. `QueueAssignmentResponse` returned with token, counter, ETA

### Service Lifecycle
- **WAITING** → customer is queued for a specific counter
- **SERVING** → cashier calls next (`startNextCustomer()`)
- **COMPLETED** → cashier finishes checkout (`completeCustomer()`)

### Dashboard (GET /api/dashboard/stats)
- Queries `queue_entries` for counts by status
- Queries `checkout_counters` for counter status
- Computes averages from all persisted entries

---

## 8. How the Database Was Tested

### Verification steps performed

1. **Maven build** — `mvn clean package -DskipTests` completed successfully,
   producing `backend-0.0.1-SNAPSHOT.jar`

2. **PostgreSQL installation** — Zonky embedded PostgreSQL 16.9 binaries
   downloaded from Maven Central and run as a normal user (no root required)

3. **Schema creation** — Hibernate `ddl-auto=update` created both tables
   on first startup (verified from Hibernate SQL logs)

4. **Foreign key verified** — `queue_entries.counter_id → checkout_counters.id`
   confirmed via JDBC `DatabaseMetaData.getImportedKeys()`

5. **Primary keys confirmed** — `id` on both tables verified via
   `DatabaseMetaData.getPrimaryKeys()`

6. **HTTP API tested:**

```
POST /api/queue/join  → Alice Smith (token SQ-0001, Counter 1) ✓
POST /api/queue/join  → Bob Jones   (token SQ-0002, Counter 2) ✓
GET  /api/queue/active → 2 entries with counter names          ✓
GET  /api/dashboard/stats → 2 waiting, avg 101s prediction    ✓
```

7. **Direct SQL query verified** — Records confirmed in PostgreSQL:
   ```sql
   SELECT * FROM queue_entries;       -- 2 rows, real IDs 1 and 2
   SELECT * FROM checkout_counters;   -- 3 rows
   ```

---

## 9. How to Run the PostgreSQL Version

### Prerequisites

- Java 21+ (or use the Temurin JDK 21 in `~/tools/jdk-21.0.12.1+1`)
- Maven 3.9+ (or use `~/tools/apache-maven-3.9.16`)
- PostgreSQL 16+ running on `localhost:5432`

### 1. Install PostgreSQL (no root required — use Zonky binaries)

```bash
# Download embedded PostgreSQL 16 binaries
curl -sL "https://repo.maven.apache.org/maven2/io/zonky/test/postgres/embedded-postgres-binaries-linux-amd64/16.9.0/embedded-postgres-binaries-linux-amd64-16.9.0.jar" -o pg-bin.jar

# Extract server binaries
java -jar pg-bin.jar extract
mv postgres-linux-x86_64 /home/YOUR_USER/tools/pg

# Initialize and start
/home/YOUR_USER/tools/pg/bin/initdb -D /home/YOUR_USER/tools/pgdata -U postgres
/home/YOUR_USER/tools/pg/bin/pg_ctl -D /home/YOUR_USER/tools/pgdata -l pg.log -o "-p 5432" start
```

Or install via apt on Ubuntu:
```bash
sudo apt install postgresql postgresql-contrib
sudo systemctl start postgresql
```

### 2. Create database and user

```bash
# Connect as postgres superuser (local socket, no password)
sudo -u postgres psql

# Inside psql:
CREATE USER smartqueue_user WITH PASSWORD 'your_password_here';
CREATE DATABASE smartqueue OWNER smartqueue_user;
GRANT ALL PRIVILEGES ON DATABASE smartqueue TO smartqueue_user;
\q
```

### 3. Build the application

```bash
cd SmartQueue/backend
mvn clean package -DskipTests
```

### 4. Run with environment variables

```bash
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=smartqueue
export DB_USER=smartqueue_user
export DB_PASSWORD=your_password_here
java -jar target/backend-0.0.1-SNAPSHOT.jar
```

Or inline:
```bash
DB_PASSWORD=your_password_here java -jar target/backend-0.0.1-SNAPSHOT.jar
```

The application starts on **http://localhost:8080**.

---

## 10. SQL Verification Commands

After the app has run at least once, connect to the database and run:

```sql
-- Show all tables
SELECT table_name FROM information_schema.tables
WHERE table_schema = 'public';

-- Show table structure (columns)
\d checkout_counters
\d queue_entries

-- Show primary keys
SELECT table_name, column_name
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu
  ON tc.constraint_name = kcu.constraint_name
WHERE constraint_type = 'PRIMARY KEY'
  AND tc.table_schema = 'public';

-- Show foreign keys
SELECT
  tc.table_name,
  kcu.column_name,
  ccu.table_name AS foreign_table_name,
  ccu.column_name AS foreign_column_name
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu
  ON tc.constraint_name = kcu.constraint_name
JOIN information_schema.constraint_column_usage ccu
  ON ccu.constraint_name = tc.constraint_name
WHERE constraint_type = 'FOREIGN KEY'
  AND tc.table_schema = 'public';

-- Show persisted queue entries
SELECT id, token, customer_name, status, counter_id
FROM queue_entries;

-- Show counters
SELECT id, name, status FROM checkout_counters;

-- Verify FK relationship works
SELECT qe.id, qe.customer_name, qe.counter_id, cc.name AS counter_name
FROM queue_entries qe
JOIN checkout_counters cc ON qe.counter_id = cc.id;
```

---

## 11. Integration into the Team's Main Project

When the team is ready to adopt this PostgreSQL migration in the main project:

1. Copy the changed files from this isolated copy:
   - `backend/src/main/resources/application.properties`
   - `backend/pom.xml`

2. Ensure PostgreSQL is installed (Docker, system package, or managed service)

3. Set `DB_PASSWORD` and `DB_USER` as environment variables or in a secrets manager

4. On first deployment to a new PostgreSQL database, Hibernate will auto-create
   the tables via `ddl-auto=update`

5. For production, consider:
   - Switching `spring.jpa.hibernate.ddl-auto` to `validate` (Hibernate only
     checks schema, does not modify it)
   - Using Flyway or Liquibase for managed migrations
   - Creating indexes on `queue_entries(status)` and `queue_entries(counter_id, status)`

---

## Summary of Changes

| File | Change |
|------|--------|
| `backend/pom.xml` | Removed `spring-boot-h2console`, removed `h2` dependency |
| `backend/src/main/resources/application.properties` | Replaced H2 datasource with PostgreSQL; added Hibernate dialect |
| `docs/DATABASE_POSTGRESQL.md` | Created (this document) |

**No entity, service, controller, or repository code was changed.**
The JPA/Hibernate abstraction layer made the switch transparent to all Java code.
