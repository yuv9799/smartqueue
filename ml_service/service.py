"""SmartQueue ML Sidecar — serves predictions from the trained models.

Loaded bundle: ml/models/smartqueue_ml_v1.joblib
Models: service-time regressor, waiting-time regressor, abandonment classifier

POST /predict
    Features available at join-queue time:
        basket_items, payment_method, assistance_required,
        open_counters, queue_length, queue_position,
        counter_load_minutes,
        hour, day_of_week, is_weekend, is_peak

    Returns: {service_minutes, wait_minutes, abandonment_probability}
"""
from __future__ import annotations

import os
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field, field_validator

PROJECT_ROOT = Path(__file__).resolve().parents[1]
MODEL_PATH = os.environ.get(
    "ML_MODEL_PATH",
    str(PROJECT_ROOT / "ml" / "models" / "smartqueue_ml_v1.joblib"),
)

bundle: dict | None = None


def load_bundle() -> dict:
    path = Path(MODEL_PATH)
    if not path.exists():
        raise RuntimeError(f"Model file not found: {path}")
    loaded = joblib.load(path)
    if not isinstance(loaded, dict) or "service_model" not in loaded:
        raise RuntimeError(
            f"Model file at {path} is not a valid SmartQueue ML bundle. "
            "Run scripts/train_ml_models.py first."
        )
    return loaded


# ---------------------------------------------------------------------------
# Pydantic request / response models
# ---------------------------------------------------------------------------

class PredictRequest(BaseModel):
    basket_items: int = Field(..., ge=1, le=300)
    payment_method: str = Field(..., pattern="^(UPI|CARD|CASH)$")
    assistance_required: int = Field(..., ge=0, le=1)
    open_counters: int = Field(..., ge=1)
    queue_length: int = Field(..., ge=0)
    queue_position: int = Field(..., ge=1)
    counter_load_minutes: float = Field(..., ge=0)
    hour: int = Field(..., ge=0, le=23)
    # Accepts 1=Mon..7=Sun (Java's DayOfWeek.getValue()) and
    # converts to Python's 0=Mon..6=Sun internally.
    # Also accepts string names like "FRIDAY" for manual testing.
    day_of_week: int | str = Field(...)

    @field_validator("day_of_week", mode="before")
    @classmethod
    def normalize_day_of_week(cls, v) -> int:
        if isinstance(v, str):
            names = ["MONDAY", "TUESDAY", "WEDNESDAY",
                     "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"]
            try:
                return names.index(v.strip().upper())
            except ValueError as exc:
                raise ValueError(
                    f"day_of_week must be an int 0-6/1-7 or a weekday name, got '{v}'"
                ) from exc
        val = int(v)
        if 1 <= val <= 7:
            # Java's DayOfWeek.getValue(): 1=Mon..7=Sun -> Python 0=Mon..6=Sun
            return val - 1
        return val  # already Python 0=Mon..6=Sun
    is_weekend: int = Field(..., ge=0, le=1)
    is_peak: int = Field(..., ge=0, le=1)

    model_config = {
        "json_schema_extra": {
            "example": {
                "basket_items": 8,
                "payment_method": "UPI",
                "assistance_required": 0,
                "open_counters": 3,
                "queue_length": 5,
                "queue_position": 3,
                "counter_load_minutes": 4.2,
                "hour": 14,
                "day_of_week": 3,
                "is_weekend": 0,
                "is_peak": 0,
            }
        }
    }


class PredictResponse(BaseModel):
    service_minutes: float
    wait_minutes: float
    abandonment_probability: float
    model_source: str


# ---------------------------------------------------------------------------
# FastAPI app
# ---------------------------------------------------------------------------

app = FastAPI(
    title="SmartQueue ML Service",
    description="Serves checkout-time, waiting-time, and abandonment predictions "
                "from the trained XGBoost models.",
    version="1.0.0",
)


@app.on_event("startup")
def startup():
    global bundle
    try:
        bundle = load_bundle()
        print(
            f"[SmartQueue ML] Loaded bundle '{bundle['model_name']}' "
            f"trained at {bundle['trained_at']}"
        )
    except Exception as exc:
        print(f"[SmartQueue ML] WARNING: Could not load model bundle: {exc}")
        print("[SmartQueue ML] ML predictions will return HTTP 503")
        bundle = None


@app.get("/health")
def health():
    if bundle is None:
        raise HTTPException(status_code=503, detail="Model bundle not loaded")
    return {
        "status": "healthy",
        "model": bundle["model_name"],
        "trained_at": bundle["trained_at"],
    }


@app.post("/predict", response_model=PredictResponse)
def predict(req: PredictRequest):
    if bundle is None:
        raise HTTPException(
            status_code=503,
            detail="ML model not loaded. Set ML_MODEL_PATH or run "
                  "scripts/train_ml_models.py.",
        )

    row = pd.DataFrame([{
        "basket_items": req.basket_items,
        "payment_method": req.payment_method,
        "assistance_required": req.assistance_required,
        "open_counters": req.open_counters,
        "queue_length": req.queue_length,
        "queue_position": req.queue_position,
        "counter_load_minutes": req.counter_load_minutes,
        "hour": req.hour,
        "day_of_week": req.day_of_week,
        "is_weekend": req.is_weekend,
        "is_peak": req.is_peak,
    }])

    # 1. Service-time
    service_pred = float(
        bundle["service_model"].predict(row)[0]
    )

    # 2. Waiting-time  (needs waiting_time_minutes as a feature for abandon model)
    wait_pred = float(
        bundle["wait_model"].predict(row)[0]
    )

    # 3. Abandonment (needs waiting_time_minutes feature)
    abandon_row = row.copy()
    abandon_row["waiting_time_minutes"] = max(0, wait_pred)
    abandon_prob = float(
        bundle["abandon_model"].predict_proba(abandon_row)[0, 1]
    )

    return PredictResponse(
        service_minutes=round(max(0, service_pred), 3),
        wait_minutes=round(max(0, wait_pred), 3),
        abandonment_probability=round(float(abandon_prob), 3),
        model_source=bundle["model_name"],
    )
