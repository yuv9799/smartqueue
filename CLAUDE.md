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
- Full test suite = 13 tests (8 QueueFeatureTests + 4 QueuePersistenceTests +
  1 BackendApplicationTests); passes with `DB_PASSWORD` set.
- A background Python **ML sidecar service** (`ml_service/` FastAPI on port
  8001) provides runtime prediction; the backend's `MlPredictionClient`
  (`smartqueue.ml.base-url`) calls it. In tests it is mocked (`@MockitoBean` /
  test config with unreachable URL).
