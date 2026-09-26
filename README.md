# SmartQueue

ML-assisted retail checkout queue management — assigns customers to the open
counter expected to become free first based on predicted service time.

## Architecture

```
┌─────────────────────────────────────┐
│  Frontend (static HTML/JS served    │
│  by Spring Boot, port 8080)         │
└──────────────┬──────────────────────┘
               │  REST
┌──────────────▼──────────────────────┐
│  Spring Boot backend (Java 21)     │     ┌──────────────────────┐
│  Port 8080                         │────▶│  ML sidecar (FastAPI)│
│  PostgreSQL persistence             │     │  Port 8001           │
│  HTML5 History API routing          │     │  smartqueue_ml_v1    │
└─────────────────────────────────────┘     └──────────────────────┘
```

### Stack

- **Backend:** Spring Boot 4.1.1, Java 21, Maven 3.9
- **Database:** PostgreSQL 16 (`hibernate ddl-auto=update`)
- **ML:** FastAPI sidecar (`:8001`), XGBoost model (`smartqueue_ml_v1.joblib`),
  rule-based fallback when ML is unavailable
- **Frontend:** Vanilla JS, no framework, served as static resources

## Features

- Customer check-in with digital token (`SQ-NNN`)
- ML-predicted service time (XGBoost) with rule-based fallback
- Least-workload counter assignment (min-heap)
- Cashier actions: call next, complete checkout, cancel
- Real-time active queue view
- History with date-range filter and status/counter filters
- Customer reassignment between counters (manager operation)
- Counter open/close management
- Estimated wait time and abandonment-risk scores per customer
- Pagination on history

## API Endpoints

### Queue

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/queue/join` | Join queue — returns token, assigned counter, ML prediction |
| `GET` | `/api/queue/active` | All WAITING/CALLED customers |
| `GET` | `/api/queue/history` | Paginated history, filterable by status/counter/date range |
| `POST` | `/api/queue/{id}/complete` | Mark service complete |
| `POST` | `/api/queue/{id}/cancel` | Cancel/no-show customer |
| `PATCH` | `/api/queue/{id}/reassign` | Reassign waiting customer to a different counter |
| `POST` | `/api/queue/counters/{counterId}/next` | Call next customer to a specific counter |

### Counters

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/counters` | List all counters with status |
| `POST` | `/api/counters` | Create a new counter |
| `GET` | `/api/counters/{id}` | Counter details |

### ML

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/predict` | Returns `predicted_seconds` and `model_version` |
| `GET` | `/health` | Health check |

## Quick start

### Prerequisites

- Java 21 (`JAVA_HOME` set)
- Maven 3.9+
- PostgreSQL 16 (running, database created)
- Python 3.10+ with `uvicorn`, `fastapi`, `joblib`, `xgboost`, `scikit-learn`
  (for ML sidecar)

### 1. Database

```sql
CREATE DATABASE smartqueue;
CREATE USER smartqueue_user WITH PASSWORD 'your_password';
GRANT ALL PRIVILEGES ON DATABASE smartqueue TO smartqueue_user;
```

Set the password environment variable before running:

```bash
export DB_PASSWORD=your_password
```

### 2. Build

```bash
cd backend
mvn package -DskipTests       # or: mvn compile
```

### 3. ML sidecar (optional, rule-based fallback used if unavailable)

```bash
cd ml_service
uvicorn ml_service.service:app --host 0.0.0.0 --port 8001 &
```

### 4. Run

```bash
cd backend
DB_PASSWORD=your_password java -jar target/backend-0.0.1-SNAPSHOT.jar
```

Open <http://localhost:8080>

### 5. Tests

```bash
cd backend
DB_PASSWORD=your_password mvn test
# 17 tests: 12 feature tests + 4 persistence tests + 1 context test
```

## Security note

No authentication or role-based access control is implemented. All endpoints
are publicly accessible. For production deployment, add Spring Security with
JWT or session-based authentication, role-based authorization (STAFF, MANAGER),
and HTTPS. API key authentication on the ML sidecar is also recommended.

## Project structure

```
SmartQueue/
├── backend/          Spring Boot application (Java 21)
│   ├── src/main/java/com/smartqueue/
│   │   ├── controller/   REST controllers
│   │   ├── dto/          Request/response records
│   │   ├── model/        JPA entities
│   │   ├── repository/   Spring Data JPA repositories
│   │   └── service/      Business logic
│   └── src/main/resources/
│       ├── static/       Frontend (HTML/JS/CSS/SPA)
│       └── application.properties
├── ml/models/        Trained XGBoost model bundle
├── ml_service/       FastAPI ML sidecar (:8001)
├── data/             CSV training data
├── scripts/          ML training scripts
├── docs/             Architecture, API, DB, ML, testing docs
│   └── diagrams/     ERD + DFD L0/L1/L2 (Mermaid source)
└── legacy/           Archived Python/SQLite prototype
```

## ML Model

The `smartqueue_ml_v1.joblib` XGBoost bundle provides three predictions:
`predicted_seconds` (service time), `wait_seconds` (queue wait),
`abandonment_probability` (0–1). Features used: `basket_items`,
`payment_method` (UPI/CARD/CASH), `assistance_required`, `open_counters`,
`queue_length`, `queue_position`, `counter_load_minutes`, `hour`,
`day_of_week`, `is_weekend`, `is_peak`.

If the ML sidecar is unreachable or times out, a deterministic rule-based
formula (clamped 45–900s) is used as fallback.