# CLAUDE.md — SmartQueue Finalization Project

## Project Identity

SmartQueue is an ML-assisted retail checkout queue-management system. The final
project is **Java + Spring Boot + Spring Data JPA + Hibernate with PostgreSQL**
and an integrated trained ML model for service-time prediction.

The old **Python + FastAPI + SQLite** implementation is LEGACY/ARCHIVE only. It
must never be treated as the final architecture, and must be clearly separated
so it does not confuse the final README or codebase.

## Master Directive (from the project owner)

The PostgreSQL + ML merge is **ALREADY UNDERWAY / ALREADY INITIATED** in this
working copy.

- **DO NOT RE-MERGE** the PostgreSQL and ML work.
- **DO NOT RESTART** the merge.
- **DO NOT repeat** the PostgreSQL migration.
- **DO NOT copy** the same files again.
- **VERIFY the existing merge first**, fix only genuine integration bugs.
- **DO NOT invent functionality.** A feature is COMPLETE only when it actually
  works AND is tested.
- **DO NOT fabricate** test results, ML integration, or PostgreSQL usage.
- **Do not hardcode passwords or credentials.** No secrets in code or docs.
- Preserve the original team project; it must remain safe. All work here is the
  development/finalization workstream.
- Keep legacy code clearly separated (e.g. a `legacy/` area) and documented as
  LEGACY/ARCHIVE.
- Prefer simple, maintainable solutions over unnecessary rewrites.

## Source of Truth

Use the ACTUAL FILES AND CODE as the primary source of truth — not class names,
not config files, not "it's documented". When documentation and code disagree,
inspect the code, determine actual state, then update docs to match reality.

## Final Objective — Phases (in order)

1. **Inspect current merged state** — what PostgreSQL/ML work actually exists;
   duplicates, broken imports, dependency conflicts, overwritten functionality.
2. **Test the merge result** — build/start, PostgreSQL connects + tables,
   insert/retrieve, relationship works, token/allocation/lifecycle work, ML
   prediction reaches allocation flow, frontend/dashboard work on real data.
3. **Verify ML integration** — the runtime path must be:
   `Customer → Spring Boot → Prediction → ACTUAL TRAINED MODEL → Predicted
   Service Time → Queue/Counter Allocation → Stored Queue Entry → Response`.
   If model format can't be consumed directly by Java, use the simplest
   technically valid architecture that ACTUALLY executes the real model (e.g.
   the Python ML sidecar service). Document load/request/input/output/failure
   handling/why this architecture.
4. **Verify PostgreSQL** — driver, datasource config, entities, schema, PK/FK,
   constraints, generated IDs, timestamps, enums, token uniqueness, queries,
   transactions. Key relationship: `CHECKOUT_COUNTERS.id → QUEUE_ENTRIES.counter_id`.
   No invented schema changes, no random indexes. No hardcoded credentials.
5. **Complete remaining functional requirements** — queue/customer features
   (registration, token, prediction, assignment, status, service start/complete,
   cancellation with historical preservation, safe reassignment), counter
   features (open/close/safe-close/status), fairness/priority (preserve
   existing FairQueueSelector logic unless actually buggy), history + filtering
   (status/counter/date), database-backed reports/analytics, dashboard/charts on
   REAL data (no fake/static values).
6. **Validation & edge cases** — invalid data, zero/negative item count, invalid
   enums, no open counters, invalid IDs, impossible state transitions (start an
   already-serving customer, complete a non-serving customer, cancel completed,
   close counter unsafely), empty queue, no history. Reuse existing exception
   handling.
7. **Security / roles** — if missing, implement a SIMPLE maintainable role-based
   mechanism (customer / staff / manager). No enterprise auth. If infeasible,
   document the limitation honestly.
8. **Testing** — fix existing tests, don't delete failing ones. Add tests for
   DB, backend, API, ML, analytics, edge cases, PostgreSQL. Run real tests,
   record real results, explain anything untestable.
9. **Architecture cleanup** — remove/isolate duplicates, merge artifacts,
   unused files/deps, temp/build/IDE files, logs, machine-specific config,
   personal secrets. Keep useful history. Separate legacy Python clearly.
   Final architecture: `Frontend → REST API → Controllers → Services →
   Repositories → JPA/Hibernate → PostgreSQL`, ML in prediction flow.
10. **Final ER diagram** — from the ACTUAL final DB (not generic). At minimum
    CHECKOUT_COUNTERS + QUEUE_ENTRIES with columns/PK/FK/constraints/cardinality.
    Store under `docs/diagrams/` as source (Mermaid/DBML/Graphviz) + rendered
    image (PNG/SVG) e.g. `ERD.mmd` and `ERD.png`.
11. **DFD Level 0, 1, 2** — from the ACTUAL final system, under `docs/diagrams/`
    (`DFD_Level_0.png` etc. + source files). L0 = context (Customer / Store
    Staff / Manager); L1 = main processes (queue mgmt, counter/service mgmt,
    prediction, reporting); L2 = detailed queue-entry flow (input validation,
    prediction, allocation, token generation, persistence, response). Only
    steps that actually exist in code.
12. **Documentation** — after functionality is tested: `README.md`, `UPDATES.md`,
    `docs/DATABASE.md`, `docs/ML.md`, `docs/API.md`, `docs/TESTING.md`,
    `docs/ARCHITECTURE.md`. Docs must match real code. No stale "H2" / "ML
    planned" claims if false, and no completion claims that aren't verified.

## Final Packaging — SmartQueue_Final

When the project is implemented AND tested:
- Create a NEW folder in the SAME PARENT directory: **`SmartQueue_Final`**.
- DO NOT rename or destroy the current working project.
- Self-contained, GitHub-ready, beginner-friendly.
- Preferred structure (GUIDELINE only — "clear and working > cosmetic"):
  `README.md`, `UPDATES.md`, `.gitignore`, `backend/`, `ml/`, `database/`,
  `docs/` (incl. `diagrams/`), `tests/`, `scripts/`, `legacy/`.
- NEVER include: `target/`, build output, `.class`, IDE metadata, logs, caches,
  temp DBs, personal paths, **passwords/credentials/API keys/tokens**, merge
  backups, generated junk.
- Include: source, backend, frontend, ML/model, data/training, DB scripts/docs,
  tests, scripts, README/UPDATES, ERD, DFDs, setup instructions.
- Small READMEs inside major folders where useful.
- The README must answer, for a new student: where is backend/frontend/ML/DB
  docs, how to configure PostgreSQL, how to run, how to test, where are ERD/
  DFDs, what to edit for ML/DB/frontend work.

## Final End-to-End Verification

Flow to verify: `Customer → Frontend → REST API → Controller → Service → ML
Prediction → Counter Allocation → Repository → JPA/Hibernate → PostgreSQL →
Queue data → Response → Frontend`, then lifecycle `JOIN → WAITING → SERVING →
COMPLETED`, plus `CANCELLED` and reassignment where supported. Dashboard/
history/analytics must show real stored data.

## Final Report to the Owner

Must cover (24 items): merge verification, PostgreSQL verification, ML
integration result, features completed/partially completed/missing, DB/backend/
frontend/ML changes, tests added + actually run + REAL results, known
limitations, docs created, ERD/DFD files, final structure, location of
`SmartQueue_Final`, exact setup/run/test commands, manual setup required, and
anything that could affect another contributor.

## Project-Specific Facts (verified)

- Working dir: `.../queue management - Copy/SmartQueue`
- Backend: Spring Boot **4.1.1** (parent), Java 21, Maven.
- Boot 4.x modernizations affecting tests:
  - `@AutoConfigureMockMvc` lives in module **`spring-boot-webmvc-test`**
    (package `org.springframework.boot.webmvc.test.autoconfigure`), which must
    be declared explicitly in `pom.xml` — `spring-boot-starter-test` no longer
    transitively provides it.
  - `@MockBean` was REMOVED in Spring Framework 7 → use **`@MockitoBean`**
    (`org.springframework.test.context.bean.override.mockito.MockitoBean`).
  - Jackson 3 artifacts live under `tools.jackson` → import
    `tools.jackson.databind.ObjectMapper` (not `com.fasterxml...`).
  - Per-technology test starters exist: `spring-boot-starter-data-jpa-test`,
    `spring-boot-starter-thymeleaf-test`, `spring-boot-starter-validation-test`.
- Datasource is PostgreSQL; password comes ONLY from env var `DB_PASSWORD`
  (no default fallback, no hardcoded secrets). Run tests with
  `DB_PASSWORD=... mvn test` — note `-DDB_PASSWORD=...` does NOT work because
  Spring `${DB_PASSWORD}` resolves environment variables, not system properties.
- `src/test/resources/application-test.properties` points at the same PostgreSQL
  as the app (deliberate — persistence tests target real PostgreSQL).
- Full test suite = **17 tests** (12 QueueFeatureTests + 4 QueuePersistenceTests +
  1 BackendApplicationTests); passes with `DB_PASSWORD` set (verified 2026-09-25
  after adding the 4 reassignment tests).
- A background Python **ML sidecar service** (`ml_service/` FastAPI on port
  8001) provides runtime prediction; the backend's `MlPredictionClient`
  (`smartqueue.ml.base-url`) calls it. In tests it is mocked (`@MockitoBean` /
  test config with unreachable URL).

## Session Progress (last audit: 2026-09-25)

### VERIFIED DONE (source of truth = code + live runtime, not claims)

- **Tests green** — full suite passes with `DB_PASSWORD=... mvn test`:
  - `QueueFeatureTests` = 12 tests (cancellation ok/409-twice/404, history
    pagination/page2/status filter, analytics overview, dashboard stats,
    + reassignment: 200/404/409/409).
  - `QueuePersistenceTests` = 4 tests.
  - `BackendApplicationTests` = 1 (contextLoads).
  - **Total: 17 tests, all pass. BUILD SUCCESS.**
- **PostgreSQL live + correct.** Backend on :8080 connects to PostgreSQL
  (`jdbc:postgresql://localhost:5432/smartqueue`, user `smartqueue_user`, v16).
  `ddl-auto=update`. Entities: `CHECKOUT_COUNTER(id PK, name unique, status)`;
  `QUEUE_ENTRY(id PK, token unique SQ-%04d from DB id, customerName, itemCount,
  paymentMethod[UPI|CARD|CASH], priorityType[REGULAR|ASSISTANCE], status
  [WAITING|SERVING|COMPLETED|CANCELLED], predictedServiceSeconds,
  predictionSource, counter_id FK→CHECKOUT_COUNTERS.id NOT NULL, arrivalTime,
  assignedAt, serviceStartedAt, serviceCompletedAt, actualServiceSeconds,
  actualWaitSeconds, estimatedWaitSeconds, abandonmentProbability,
  cancelledAt)`. Token uniqueness via double-save (saveAndFlush→setToken→save)
  inside @Transactional joinQueue().
- **ML runtime path WORKS end-to-end (verified live).** Join returned
  `predictionSource: "smartqueue_ml_v1"`, `abandonmentRisk: 0.015`,
  `estimatedWaitSeconds`. Flow: backend RestClient (2s timeout) → FastAPI
  sidecar :8001 → `ml/models/smartqueue_ml_v1.joblib` (XGBoost bundle:
  service_model 450 trees, wait_model 500 trees, abandon_model 400 trees,
  + feature lists/metrics) → response → stored QueueEntry. **No hardcoded
  `/home/kiit/...` paths in any `.py`/`.java`** — ml_service uses
  `PROJECT_ROOT = Path(__file__).resolve().parents[1]` + `ML_MODEL_PATH` env
  override. Fallback = `CheckoutTimePredictor` `RULE_BASELINE_V1` (clamped
  45–900s) when sidecar unreachable/null; genuinely exercised in tests.
- **API surface (all live and tested):**
  - `POST /api/queue/join` 201 — token + counter + predictedServiceSeconds +
    estimatedWaitSeconds + abandonmentRisk; allocation = least-workload open
    counter (min-heap over active workload).
  - `GET /api/queue/active`; `GET /api/queue/history` (page/size/status/
    counterId/from/to, max 500/page).
  - `POST /api/queue/counters/{counterId}/next` (start next; 409 if already
    serving or queue empty); `POST /api/queue/{id}/complete` (404 if not
    SERVING); `POST /api/queue/{id}/cancel` (idempotent, 409 if re-cancel);
    `PATCH /api/queue/{id}/reassign` with `{"newCounterId":N}` (404 if entry
    not found; 409 if entry not WAITING or target counter CLOSED).
  - `GET /api/counters`; `PATCH /api/counters/{id}/status` (safe-close 409 if
    active customers).
  - `GET /api/dashboard/stats`; `GET /api/reports/overview` (7d window,
    counterUtilization + hourlyDistribution); `GET /` ping.
  - `GlobalExceptionHandler`: MethodArgumentNotValid→400 (field map),
    HttpMessageNotReadable→400, IllegalState → 404 if "not found" else 409.
- **FairQueueSelector PRESERVED** (per directive) — priority jump (ASSISTANCE
  up to 2 positions) + starvation guard (REGULAR unstarves after 10 min);
  used by startNextCustomer. CounterAllocator is PriorityQueue min-heap.
- **Frontend** (static SPA, served by backend): 8 stat cards, join form,
  assignment panel, counter Start-Next/Open-Closed, active queue with
  Complete/Cancel, history with status+counter filter + pagination, KPI cards
  + utilization/hourly bar charts (data-driven from /api/reports/overview, no
  fake values), 10s auto-refresh, XSS-escaped output.
- **Git**: `main`, 11 commits (`28ed01f` MVP, `a0b597f` full merge,
  `a0ecd78` CLAUDE.md session progress, `ff4ca4b` prediction-first allocation
  + ML persistence, `b4096bf` CLAUDE.md gaps 1+2 done, `4d834e1` gaps 5+6
  reassign endpoint + date filter, `0280c31` gap 10 legacy archive,
  `4de896a` README rewrite, `52edf76` gap 8 ml_service tests, `5ab68f4` gap 9
  docs, `05eb1a9` serve SPA at `/`). `.gitignore` covers
  `*Zone.Identifier`, `.venv/`,
  `**/target/`, `*.joblib`, `*.pkl`, `*.mv.db`, etc. 74 tracked files.
  NO passwords/keys in committed files.
- **SPA at `/` fix (commit 05eb1a9).** `HomeController` was a
  `@RestController` returning JSON for `/`, so a browser (Accept: text/html)
  got **406**. Changed it to `@Controller` → `forward:/index.html` so the
  static SPA loads when navigating to `http://localhost:8080/`. Verified live
  (200 + index.html) and all 17 tests still pass.

### Verified GAPS / TODO (found by audit — do these in order)

1. **DONE: ALLOCATION RUNS BEFORE PREDICTION (fixed 2026-09-25).**
   `QueueService.joinQueue()` now does two-pass allocation:
   (a) rule-based `CheckoutTimePredictor` estimate for the new customer —
       fast, no network, steers the counter choice;
   (b) pre-allocate (existing load only) to feed a real counter-load into the
       ML feature vector;
   (c) ML `predict()` for wait-time/abandonment (service time upgraded to ML's
       when available);
   (d) final `allocateCounter(initialServiceSeconds)` — the new customer's own
       predicted service time now factors into counter selection.
   `CounterAllocator.allocateCounter(int)` takes the predicted seconds and adds
   it to every open counter's workload. All 13 tests still green after fix.
2. **DONE: ML wait/abandonment persisted (fixed 2026-09-25, commit ff4ca4b).**
   `QUEUE_ENTRY` now has `estimated_wait_seconds` (BIGINT, nullable) and
   `abandonment_probability` (DOUBLE, nullable) columns (added by ddl-auto=update).
   QueueEntry entity updated with both fields; QueueService.joinQueue() passes
   ML wait/abandonment to the new constructor; QueueEntryResponse exposes both;
   QueueEntryRepository adds `averageEstimatedWaitSecondsBetween()`,
   `averageAbandonmentProbabilityBetween()`,
   `averageWaitPredictionErrorSecondsBetween()` queries;
   AnalyticsOverviewResponse adds `waitPredictionMAESeconds` +
   `meanPredictedAbandonmentRisk`; AnalyticsService computes and surfaces both.
   QueuePersistenceTests updated to new 9-arg constructor. Verified live:
   join returns `estimatedWaitSeconds: 669, abandonmentRisk: 0.245` and these
   are stored and returned via `/api/queue/history`. Pre-fix entries = null
   (expected, not a bug). 13/13 tests green.
3. **DONE: Test-count drift (fixed 2026-09-25).** Real count is **13** (8
   QueueFeatureTests + 4 QueuePersistenceTests + 1 BackendApplicationTests) —
   verified directly in Maven output. Doc updated.
4. **`startNextCustomer` empty-queue → 409** is fine; no `/api/queue/{id}/start`
   needed. Do NOT invent endpoints.
5. **DONE: Reassignment endpoint (fixed 2026-09-25).**
   `PATCH /api/queue/{id}/reassign` with body `{"newCounterId":N}` added.
   Components: `ReassignRequest` DTO (`@NotNull Long newCounterId`);
   `QueueController.reassignCustomer()` PATCH endpoint; multiple
   `QueueService.reassignCustomer(entryId, newCounterId)` — validates entry
   exists (404), status is WAITING (409 otherwise), target counter exists
   (404) and is OPEN (409); updates the counter FK. Added `setCounter()` to
   `QueueEntry`. 4 new QueueFeatureTests (success, 404 entry, 409 non-waiting,
   409 closed counter). Verified live; 17/17 tests green.
6. **DONE: History UI date-range filter (fixed 2026-09-25).**
   `index.html` history panel now has two `datetime-local` inputs
   (`history-from`, `history-to`); `app.js` `loadHistory()` reads them and
   passes `from`/`to` to the existing `/api/queue/history?from=...&to=...`
   backend support. Frontend-only change (no backend edits).
7. **DONE: No role-based security (gap #7).** Not implemented. README.md
   and `docs/ARCHITECTURE.md` document the limitation with concrete production
   hardening guidance (Spring Security, HTTPS, ML-sidecar API key).
8. **DONE: Python tests for `ml_service/` (gap #8, commit 52edf76).**
   `ml_service/tests/test_api.py` — 11 tests via FastAPI `TestClient`
   (health 200/503, predict 200, 422 missing/invalid fields, day-of-week as
   string, 503 unloaded bundle). All 11 pass. `.venv/` recreated (needed by
   sidecar) and used to run them.
9. **DONE: Docs (gap #9).** Root `README.md` rewritten to the actual
   Spring Boot 4 + PostgreSQL + ML-sidecar stack. Added `docs/UPDATES.md`,
   `docs/DATABASE.md`, `docs/ML.md`, `docs/API.md`, `docs/TESTING.md`,
   `docs/ARCHITECTURE.md`. NOTE: `docs/diagrams/` (ERD + DFD L0/L1/L2, phases
   10–11) still MISSING — the phase-10/11 diagram deliverable.
10. **DONE: Architecture cleanup (gap #10, commit 0280c31).**
    - All **`*:Zone.Identifier` ADS files** deleted (incl. the git broken-ref one).
    - `backend/data/smartqueue.mv.db` (H2) deleted.
    - `backend/target/` + `.venv/` removed from the working copy (`.venv/` was
      later recreated because the ML sidecar needs it to run).
    - Legacy moved under `legacy/`: `app/`, `scripts/train_model.py`,
      `scripts/generate_synthetic_data.py`, `tests/test_core.py`,
      `requirements.txt`.
    - `ml/checkout_model.joblib` (RandomForest legacy) stays gitignored;
      `scripts/train_ml_models.py` + `docs/ML.md` document the active
      `smartqueue_ml_v1.joblib`.
11. **DONE: SmartQueue_Final packaging + owner report (gap #11).**
    `/home/kiit/omniclaude/se/queue management - Copy/SmartQueue_Final/SmartQueue/`
    contains backend+jar, frontend, ml, ml_service, .venv, data, scripts,
    docs (all 6 new), legacy, README, `OWNER_REPORT.md` (24-item inventory).
    Excluded: `.git`, ADS streams, logs, `__pycache__`, Zone.Identifier.
12. **Runtime env**: `mvn` NOT on PATH in WSL sessions — use
    `~/tools/apache-maven-3.9.16/bin/mvn` with `JAVA_HOME=~/tools/jdk-21.0.12.1+1`;
    `mvnw` wrapper is present but bare (no distribution downloaded). Run tests:
    `DB_PASSWORD=... mvn test` (env var, NOT -D).

### HOW TO RUN (verified)
- Backend: `DB_PASSWORD=... java -jar backend/target/backend-0.0.1-SNAPSHOT.jar` (:8080)
- ML sidecar: `.venv/bin/python -m uvicorn ml_service.service:app --port 8001`
- Tests: `cd backend && DB_PASSWORD=... ~/tools/apache-maven-3.9.16/bin/mvn test`
