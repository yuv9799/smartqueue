# SmartQueue Owner Report

## Package Inventory

| # | Item | Location | Notes |
|---|------|----------|-------|
| 1 | Spring Boot backend (Java 21) | `backend/src/main/java/com/smartqueue/` | 8 source packages |
| 2 | Frontend (static HTML/JS/CSS) | `backend/src/main/resources/static/` | index.html, app.js, style.css |
| 3 | Build artifact (jar) | `backend/target/backend-0.0.1-SNAPSHOT.jar` | 54 MB, built with `mvn package -DskipTests` |
| 4 | Maven pom.xml | `backend/pom.xml` | Spring Boot 4.1.1, Java 21, Boot test modernizations |
| 5 | Application config | `backend/src/main/resources/application.properties` | PostgreSQL datasource, no hardcoded password |
| 6 | Application test config | `backend/src/test/resources/application-test.properties` | Same DB as application.properties |
| 7 | ML sidecar (FastAPI) | `ml_service/service.py` | Runs on port 8001 |
| 8 | ML model bundle | `ml/models/smartqueue_ml_v1.joblib` | XGBoost, 3 sub-models |
| 9 | ML training script | `scripts/train_ml_models.py` | Reproduces the .joblib bundle |
| 10 | ML training data | `data/synthetic_checkout_history.csv` | 5000-row synthetic dataset |
| 11 | ML real-data template | `data/real_observation_template.csv` | Anonymous collection structure |
| 12 | Java test suite | `backend/src/test/java/com/smartqueue/` | 17 tests (QueueFeatureTests ×12, QueuePersistenceTests ×4, BackendApplicationTests ×1) |
| 13 | Python test suite | `ml_service/tests/test_api.py` | 11 tests via FastAPI TestClient |
| 14 | Python venv | `.venv/` | ~800 MB; contains fastapi, joblib, xgboost, pytest |
| 15 | Archived legacy prototype | `legacy/` | Python/FastAPI/SQLite version (for reference only) |
| 16 | REST API documentation | `docs/API.md` | All endpoints, request/response schemas |
| 17 | Database documentation | `docs/DATABASE.md` | Entities, relationships, constraints, setup |
| 18 | ML documentation | `docs/ML.md` | Model info, sidecar API, features, fallback |
| 19 | Testing guide | `docs/TESTING.md` | How to run all tests |
| 20 | Architecture overview | `docs/ARCHITECTURE.md` | System diagram, layers, decisions |
| 21 | Change log | `docs/UPDATES.md` | Gaps 1–10 closure notes |
| 22 | Root README | `README.md` | Architecture, stack, quick start |
| 23 | Project notes | `CLAUDE.md` | Gap tracking, verified fixes, session learnings |
| 24 | .gitignore | `.gitignore` | Standard exclusions: .git, target, .venv, *.pyc, Zone.Identifier |

---

## How to build and run

### 1. Database (PostgreSQL 16)

```sql
CREATE DATABASE smartqueue_db;
CREATE USER smartqueue_user WITH PASSWORD 'your_password';
GRANT ALL PRIVILEGES ON DATABASE smartqueue_db TO smartqueue_user;
```

### 2. Backend (Java 21)

```bash
cd backend
mvn package -DskipTests
DB_PASSWORD=your_password \
  java -jar target/backend-0.0.1-SNAPSHOT.jar
# Backend: http://localhost:8080
```

### 3. ML sidecar (optional — rule-based fallback used if down)

```bash
cd ml_service
../.venv/bin/uvicorn service:app --host 0.0.0.0 --port 8001
```

### 4. Run tests

```bash
# Java (17 tests)
cd backend
DB_PASSWORD=your_password mvn test

# Python (11 tests)
cd ml_service
../.venv/bin/python -m pytest tests/ -v
```

---

## Key runtime facts

- **DB_PASSWORD** environment variable — required, no default. Set before starting backend.
- **ML sidecar** — runs independently, called by backend on each queue join.
  If unreachable, `CheckoutTimePredictor` falls back to rule-based formula (45–900s, clamped).
- **counter_id NOT NULL** — cannot null out a counter. To delete a counter,
  first delete all queue entries pointing to it.
- **Token format** — `SQ-{id:04}`, assigned after DB save (guaranteed unique).
- **Build path** — `backend/` directory, not repo root. Use `cd backend && mvn ...`.
- **Maven path** — `~/tools/apache-maven-3.9.16/bin/mvn` (not on PATH).
- **Java path** — `~/tools/jdk-21.0.12.1+1/bin/java` (not on PATH).

---

## Known limitations

1. No authentication or authorization — all endpoints public.
2. ML sidecar runs on port 8001 (not behind reverse proxy).
3. `counter_id` is NOT NULL — no way to "unassign" a customer from a counter via API.
4. History pagination: no total count returned in response body (Spring Page object).
5. No rate limiting or request validation beyond basic `@Valid` annotations.
6. Queue entries with `status = WAITING` cannot be re-allocated by the allocation
   service (only via the explicit reassign endpoint).
7. No audit log — status changes are not tracked separately.
8. No transaction retry on ML sidecar timeout — if the prediction call fails,
   the transaction is rolled back and the customer must re-join.

---

## Maintenance

| Concern | How to handle |
|---------|---------------|
| Retrain ML model | `python scripts/train_ml_models.py` → `ml/models/smartqueue_ml_v1.joblib` |
| Add new endpoint | Add DTO record + service method + controller + tests |
| Change entity field | Hibernate `ddl-auto=update` auto-migrates; add migration note for prod |
| Fix ML sidecar down | Backend auto-falls back to rule-based; no restart needed |
| Rebuild jar | `cd backend && mvn package -DskipTests` |
| Re-run all tests | `cd backend && DB_PASSWORD=... mvn test` |
