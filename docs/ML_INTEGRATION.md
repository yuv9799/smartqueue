[LEGACY — pre-merge architecture, not maintained. Current stack: Java/Spring Boot + PostgreSQL + ML sidecar]

# ML Integration — SmartQueue Copy

> **Scope:** This document describes the ML integration done in the **isolated copy**
> (`queue management - Copy/SmartQueue`). It is intended as a reference for the
> team's main project, not as a replacement for it.

## Overview

A Python FastAPI sidecar (`ml-service/`) serves three XGBoost models trained on
synthetic data. The Spring Boot backend calls the sidecar at join-queue time and
falls back to the built-in rule-based predictor if the sidecar is unavailable.

**Prediction pipeline** (backend → ML sidecar → PostgreSQL):

```
Client POST /api/queue/join
  └─► QueueService.joinQueue()
        ├─► CounterAllocator.allocateCounter()
        │     └─► computes open counter count, min workload
        ├─► QueueEntryRepository.countByStatus(WAITING/SERVING)
        ├─► MlPredictionClient.predict(MlFeatures)
        │     ├─► ML sidecar UP  → returns MlPrediction (service/wait/abandonment)
        │     └─► ML sidecar DOWN → returns null  (graceful fallback)
        └─► CheckoutTimePredictor.predict() [used only on fallback]
  └─► QueueEntry saved to PostgreSQL
       └─► counter_id FK to checkout_counters.id
```

## Trained Models

| Model | Task | Test metric |
|---|---|---|
| Service time | Regression | MAE = 0.61 min, R² = 0.44 |
| Waiting time | Regression | MAE = 1.01 min, R² = 0.962 |
| Abandonment | Binary classification | Accuracy = 0.78, F² F1 = 0.56 |

The **demand / queue-length** model was excluded (test R² = -0.08, worse than a
constant-mean baseline).

**Bundle:** `ml/models/smartqueue_ml_v1.joblib`

## ML Features

The backend assembles these features for each join-queue request:

| Feature | Source | Type |
|---|---|---|
| `basket_items` | JoinQueueRequest.itemCount | int |
| `payment_method` | JoinQueueRequest.paymentMethod.name() | UPI/CARD/CASH |
| `assistance_required` | JoinQueueRequest.priorityType == ASSISTANCE | 0/1 |
| `open_counters` | CounterAllocator.openCounterCount() | int |
| `queue_length` | QueueEntryRepository.countByStatus(WAITING + SERVING) | int |
| `queue_position` | queue_length + 1 (or 1 if empty) | int |
| `counter_load_minutes` | CounterAllocator.estimatedWaitSeconds / 60 | float |
| `hour` | LocalDateTime.now().getHour() | 0–23 |
| `day_of_week` | LocalDateTime.now().getDayOfWeek().getValue() | 1=Mon..7=Sun (Java ISO) |
| `is_weekend` | SATURDAY or SUNDAY | 0/1 |
| `is_peak` | 12–13 or 17–20 | 0/1 |

> The ML sidecar normalises `day_of_week` internally: it accepts both Java's
> 1=Mon..7=Sun and Python's 0=Mon..6=Sun (subtract 1) and string names like
> `"FRIDAY"`.

## API

### ML Sidecar (`ml-service/service.py`)

```http
GET  /health  → 200 {status, model, trained_at}
POST /predict → 200 {service_minutes, wait_minutes, abandonment_probability, model_source}
```

**POST /predict body** (`application/json`):

```json
{
  "basket_items": 5,
  "payment_method": "UPI",
  "assistance_required": 0,
  "open_counters": 2,
  "queue_length": 8,
  "queue_position": 3,
  "counter_load_minutes": 15.0,
  "hour": 14,
  "day_of_week": 6,
  "is_weekend": 1,
  "is_peak": 1
}
```

All fields required. `day_of_week` accepts:
- Integer 1–7 (Java `DayOfWeek.getValue()` format)
- Integer 0–6 (Python day-index)
- String weekday name e.g. `"FRIDAY"` (case-insensitive)

### Backend API changes

`POST /api/queue/join` response now includes two additional fields:

```json
{
  "id": 3,
  "token": "SQ-0003",
  "predictionSource": "smartqueue_ml_v1",   ← model name, or "RULE_BASELINE_V1" fallback
  "abandonmentRisk": 0.386,                  ← ML abandonment probability (null on fallback)
  "estimatedWaitSeconds": 173,               ← ML wait-time prediction (0 on rule fallback)
  ...
}
```

## Files

```
SmartQueue/
├── ml_service/                  # Python ML sidecar package (was ml-service, renamed ml_service)
│   ├── service.py                # FastAPI app
│   ├── requirements.txt
│   └── __init__.py
├── ml/
│   └── models/
│       └── smartqueue_ml_v1.joblib   # Trained model bundle
├── scripts/
│   └── train_ml_models.py           # Training script (reproduces friend notebook)
├── backend/src/main/java/com/smartqueue/
│   └── service/
│       └── MlPredictionClient.java  # REST client with 2-s timeout, graceful fallback
└── backend/src/main/resources/
    └── application.properties
        └── smartqueue.ml.base-url=${ML_SERVICE_URL:http://localhost:8001}
```

## Starting the ML sidecar

```bash
# Requires Python 3.10+ with virtual environment
cd SmartQueue
source .venv/bin/activate
python -m uvicorn ml_service.service:app --port 8001

# Or via script (if ml_service_start.sh is added):
# bash ml_service_start.sh
```

**Dependencies** (installed in `.venv`):
`fastapi`, `uvicorn`, `pydantic`, `joblib`, `pandas`, `numpy`, `scikit-learn`, `xgboost`

## Environment variables

| Variable | Default | Notes |
|---|---|---|
| `ML_MODEL_PATH` | `ml/models/smartqueue_ml_v1.joblib` | Path to the model bundle |
| `ML_SERVICE_URL` | `http://localhost:8001` | Backend uses this to reach the sidecar |

## Graceful Degradation

If the ML sidecar is unreachable (network error, crash, not started), the backend
falls back silently to `CheckoutTimePredictor` and logs:

```
ML service call failed — using rule-based fallback: Connect to localhost:8001 [...]
```

The join-queue response in this case:
- `predictionSource`: `"RULE_BASELINE_V1"`
- `abandonmentRisk`: `null` (no ML score available)
- `estimatedWaitSeconds`: `0` (rule-based predictor does not estimate wait time)

## Retraining the Models

Edit `scripts/train_ml_models.py` and run:

```bash
cd SmartQueue
source .venv/bin/activate
python scripts/train_ml_models.py
# Output: ml/models/smartqueue_ml_v1.joblib
```

Restart the ML sidecar to pick up the new bundle.

## Integration Notes for Main Project

1. Copy `ml_service/`, `ml/models/smartqueue_ml_v1.joblib`, and
   `scripts/train_ml_models.py` into the main project's directory structure.
2. Copy `backend/src/main/java/com/smartqueue/service/MlPredictionClient.java`
   into the main project's service layer.
3. Update `backend/src/main/resources/application.properties` to add
   `smartqueue.ml.base-url=${ML_SERVICE_URL:http://localhost:8001}`.
4. Inject `MlPredictionClient` into `QueueService` and modify `joinQueue()` as
   described in the Overview pipeline above.
5. Extend `QueueAssignmentResponse` with `Double abandonmentRisk` and
   `long estimatedWaitSeconds` fields.
6. Add the `ml-service` startup step to the deployment/launch script.

## Verified Behaviour

| Scenario | `predictionSource` | `abandonmentRisk` | `estimatedWaitSeconds` |
|---|---|---|---|
| ML sidecar running, 1st join | `smartqueue_ml_v1` | `0.023` (example) | ML wait time in seconds |
| ML sidecar stopped (fallback) | `RULE_BASELINE_V1` | `null` | `0` |
| ML service error (503, etc.) | `RULE_BASELINE_V1` | `null` | `0` |