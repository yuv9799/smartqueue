# ML Predicted Service Time

## Architecture: sidecar

The trained model runs as a separate FastAPI service on port 8001. Spring Boot
calls it on queue join:

```
QueueEntry needs service time
        │
        ▼
MlPredictionClient.predict(features)   (RestClient, 2s timeout)
        │
        ▼
FastAPI POST /predict (port 8001)
        │
        ▼
Loads ml/models/smartqueue_ml_v1.joblib (XGBoost bundle, loaded once at startup)
        │
        ▼
Predicted service time → wait time → abandonment risk
```

If the sidecar is unreachable or times out, `CheckoutTimePredictor` falls back
to a deterministic rule formula (clamped 45–900 seconds).

## Model

- **File:** `ml/models/smartqueue_ml_v1.joblib`
- **Type:** XGBoost bundle
  - `service_model` — predicts checkout service time (trained on synthetic data)
  - `wait_model` — predicts queue wait time
  - `abandon_model` — predicts abandonment probability
  - plus feature lists, training metrics
- **Features used:**
  - `item_count` (basket items)
  - `payment_method` (encoded)
  - `open_counters`
  - `queue_position`
  - `hour_of_day`
  - `day_of_week`
- **Performance:** measured with MAE (mean absolute error) on held-out data.

## Model files and training

| File | Role |
|------|------|
| `scripts/train_ml_models.py` | Trains all three models from synthetic data |
| `ml/models/smartqueue_ml_v1.joblib` | Trained bundle loaded by sidecar |
| `data/synthetic_checkout_history.csv` | Training dataset |
| `data/real_observation_template.csv` | Anonymous real-data template |

`.joblib` files are gitignored; reproduce by running the training script.
`legacy/ml/checkout_model.joblib` is the archived RandomForest (old prototype).

## API

### POST /predict

```json
// Request
{
  "basket_items": 15,
  "payment_method": "card",
  "open_counters": 3,
  "queue_position": 4,
  "hour_of_day": 14,
  "day_of_week": "friday"
}
```

```json
// Response 200
{
  "predicted_seconds": 227,
  "wait_seconds": 642,
  "abandonment_probability": 0.18,
  "model_version": "smartqueue_ml_v1"
}
```

**Errors:** 422 invalid fields (e.g. basket_items > 300), 503 bundle not loaded.

### GET /health

```json
{ "status": "healthy", "model": "smartqueue_ml_v1" }
```

## Testing

Run `ml_service` tests + 17 Spring Boot tests; see `TESTING.md`.
