# SmartQueue — Change Log

All significant changes since the initial PostgreSQL migration.

---

## 2026-09-25 — Gap closures (gaps 1–10)

### Gap 5: PATCH /api/queue/{id}/reassign
- **New endpoint** `PATCH /api/queue/{id}/reassign` with JSON body `{"newCounterId": N}`
- Reassigns a `WAITING` customer to a different `OPEN` counter
- Returns `QueueEntryResponse` with updated `counterId`/`counterName`
- Errors: 404 if entry/counter not found; 409 if not WAITING or counter not OPEN
- **Tests:** 4 new MockMvc tests — 200 (success), 404 (bad id), 409 (not waiting), 409 (closed counter)

### Gap 6: Frontend date-range filter on history
- `<input type="datetime-local" id="history-from">` and `history-to` in history panel
- JavaScript reads values and adds `from`/`to` query params to `GET /api/queue/history`
- Backend accepts `from` and `to` as ISO-8601 `LocalDateTime` (nullable)

### Gap 7: Security (documented limitation)
- No authentication or role-based access control
- All endpoints are public
- See `README.md` → Security note for production hardening guidance

### Gap 8: Python tests for ml_service
- 11 tests in `ml_service/tests/test_api.py` using FastAPI `TestClient`
- Covers: health 200/503, prediction 200, 422 (missing/invalid fields)
- All 11 pass

### Gap 9: Documentation (this file)
- README rewritten to describe actual Spring Boot + PostgreSQL + ML sidecar stack
- UPDATES.md (this file), DATABASE.md, API.md, TESTING.md, ARCHITECTURE.md added
- `docs/MODEL_RESULTS.md` still references legacy RandomForest — see ML.md for current model

### Gap 10: Architecture cleanup
- H2 auto-db (`smartqueue.mv.db`), ADS `Zone.Identifier` streams removed
- `backend/target/` and `.venv/` removed
- Legacy Python/FastAPI/SQLite prototype archived to `legacy/`
- Active files preserved: `ml/`, `ml_service/`, `scripts/train_ml_models.py`, `data/*.csv`

### Gap 1–4 (done in prior sessions)
- PostgreSQL migration (Hibernate `ddl-auto=update`)
- ML sidecar (FastAPI, smartqueue_ml_v1.joblib, rule-based fallback)
- Dashboard and analytics views
- 17 Java tests (12 feature + 4 persistence + 1 context)

---

## Pre-migration (legacy/ archive)

Original prototype used Python 3.10 + FastAPI + SQLite. See `legacy/` directory.
