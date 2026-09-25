"""Train the SmartQueue ML models from the Complete ML Pipeline notebook.

Reproduces the notebook's training procedure (same synthetic-data generator,
same feature engineering, same hyperparameters, same RANDOM_STATE=42) and
saves the three useful models into one joblib bundle:

    ml/models/smartqueue_ml_v1.joblib

The demand-forecast model from the notebook is intentionally NOT saved:
on the notebook's own test split it scored R2 = -0.08 (worse than
predicting the mean), so it has no value until real data arrives.

Run from the SmartQueue project root:
    .venv/bin/python scripts/train_ml_models.py
"""
from __future__ import annotations

import sys
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from sklearn.compose import ColumnTransformer
from sklearn.ensemble import (
    GradientBoostingRegressor,
    RandomForestClassifier,
)
from sklearn.impute import SimpleImputer
from sklearn.metrics import (
    accuracy_score,
    f1_score,
    mean_absolute_error,
    r2_score,
)
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder

RANDOM_STATE = 42
PROJECT_ROOT = Path(__file__).resolve().parents[1]
MODEL_DIR = PROJECT_ROOT / "ml" / "models"
OUTPUT_PATH = MODEL_DIR / "smartqueue_ml_v1.joblib"

try:
    from xgboost import XGBRegressor, XGBClassifier
    XGB_AVAILABLE = True
except ImportError:
    XGB_AVAILABLE = False


def make_synthetic_smartqueue(n=12000, seed=42):
    """Identical to the notebook's make_synthetic_smartqueue()."""
    rng = np.random.default_rng(seed)
    start = pd.Timestamp("2026-01-01 08:00:00")
    timestamps = start + pd.to_timedelta(
        rng.integers(0, 180 * 24 * 60, n), unit="m"
    )
    timestamps = pd.Series(timestamps)

    hour = timestamps.dt.hour + timestamps.dt.minute / 60
    dow = timestamps.dt.dayofweek
    basket = np.clip(rng.poisson(12, n) + 1, 1, 80)
    payment = rng.choice(["UPI", "CARD", "CASH"], n, p=[0.52, 0.28, 0.20])
    assistance = rng.binomial(1, np.clip(0.06 + basket / 300, 0.04, 0.35), n)
    open_counters = rng.integers(2, 5, n)
    queue_length = np.clip(
        rng.poisson(5 + 7 * ((hour >= 17) & (hour <= 20)) + 3 * (dow >= 5), n),
        0, 45,
    )
    position = np.clip(rng.integers(1, 12, n), 1, queue_length + 1)

    counter_load = np.clip(
        rng.gamma(shape=2.0, scale=3.0, size=n)
        + 0.25 * queue_length
        + 0.8 * (open_counters == 2), 0, 30
    )

    payment_effect = np.where(
        payment == "CASH", 0.9, np.where(payment == "CARD", 0.45, 0.2)
    )
    peak_effect = 0.8 * ((hour >= 17) & (hour <= 20))
    service_time = (
        1.0
        + 0.075 * basket
        + 1.7 * assistance
        + payment_effect
        + peak_effect
        + rng.normal(0, 0.75, n)
    )
    service_time = np.clip(service_time, 0.8, 18)

    avg_ahead_service = 3.8 + 0.12 * basket + 0.7 * assistance
    waiting_time = (
        (position - 1) * avg_ahead_service / open_counters
        + 0.65 * counter_load
        + rng.normal(0, 1.2, n)
    )
    waiting_time = np.clip(waiting_time, 0, 45)

    logit = (
        -4.2 + 0.22 * waiting_time + 0.045 * queue_length
        + 0.7 * assistance - 0.015 * basket
    )
    p_abandon = 1 / (1 + np.exp(-logit))
    abandoned = rng.binomial(1, np.clip(p_abandon, 0.01, 0.95), n)

    return pd.DataFrame({
        "timestamp": timestamps,
        "basket_items": basket,
        "payment_method": payment,
        "assistance_required": assistance,
        "open_counters": open_counters,
        "queue_length": queue_length,
        "queue_position": position,
        "counter_load_minutes": counter_load,
        "service_time_minutes": service_time,
        "waiting_time_minutes": waiting_time,
        "abandoned": abandoned,
    })


def build_preprocessor(features: list[str]) -> ColumnTransformer:
    cat_cols = ["payment_method"]
    num_cols = [c for c in features if c not in cat_cols]
    return ColumnTransformer([
        ("num", SimpleImputer(strategy="median"), num_cols),
        ("cat", Pipeline([
            ("imputer", SimpleImputer(strategy="most_frequent")),
            ("onehot", OneHotEncoder(handle_unknown="ignore")),
        ]), cat_cols),
    ])


def train_models(df: pd.DataFrame) -> dict:
    for col in ("timestamp",):
        df[col] = pd.to_datetime(df[col], errors="coerce")
    df["hour"] = df["timestamp"].dt.hour
    df["day_of_week"] = df["timestamp"].dt.dayofweek
    df["is_weekend"] = (df["day_of_week"] >= 5).astype(int)
    df["is_peak"] = df["hour"].between(17, 20).astype(int)
    df = df.replace([np.inf, -np.inf], np.nan).dropna()

    common_cat = ["payment_method"]

    # ---- 1. Service-time model ------------------------------------------
    service_features = [
        "basket_items", "payment_method", "assistance_required",
        "open_counters", "queue_length", "counter_load_minutes",
        "hour", "day_of_week", "is_weekend", "is_peak",
    ]
    X = df[service_features]
    y = df["service_time_minutes"]
    X_tr, X_te, y_tr, y_te = train_test_split(
        X, y, test_size=0.20, random_state=RANDOM_STATE
    )
    num_cols = [c for c in service_features if c not in common_cat]
    preprocess = ColumnTransformer([
        ("num", SimpleImputer(strategy="median"), num_cols),
        ("cat", Pipeline([
            ("imputer", SimpleImputer(strategy="most_frequent")),
            ("onehot", OneHotEncoder(handle_unknown="ignore")),
        ]), common_cat),
    ])
    if XGB_AVAILABLE:
        core = XGBRegressor(
            n_estimators=450, max_depth=5, learning_rate=0.045,
            subsample=0.85, colsample_bytree=0.85,
            objective="reg:squarederror", random_state=RANDOM_STATE, n_jobs=-1,
        )
    else:
        core = GradientBoostingRegressor(
            n_estimators=350, max_depth=4, learning_rate=0.04,
            random_state=RANDOM_STATE,
        )
    service_model = Pipeline([("preprocess", preprocess), ("model", core)])
    service_model.fit(X_tr, y_tr)
    pred = service_model.predict(X_te)
    service_metrics = {
        "mae_minutes": round(float(mean_absolute_error(y_te, pred)), 3),
        "r2": round(float(r2_score(y_te, pred)), 3),
    }
    print(f"service-time  MAE={service_metrics['mae_minutes']}  R2={service_metrics['r2']}")

    # ---- 2. Waiting-time model ------------------------------------------
    wait_features = [
        "basket_items", "payment_method", "assistance_required",
        "open_counters", "queue_length", "queue_position",
        "counter_load_minutes", "hour", "day_of_week",
        "is_weekend", "is_peak",
    ]
    X = df[wait_features]
    y = df["waiting_time_minutes"]
    X_tr, X_te, y_tr, y_te = train_test_split(
        X, y, test_size=0.20, random_state=RANDOM_STATE
    )
    num_cols = [c for c in wait_features if c not in common_cat]
    wait_preprocess = ColumnTransformer([
        ("num", SimpleImputer(strategy="median"), num_cols),
        ("cat", Pipeline([
            ("imputer", SimpleImputer(strategy="most_frequent")),
            ("onehot", OneHotEncoder(handle_unknown="ignore")),
        ]), common_cat),
    ])
    if XGB_AVAILABLE:
        core = XGBRegressor(
            n_estimators=500, max_depth=5, learning_rate=0.04,
            subsample=0.85, colsample_bytree=0.85,
            objective="reg:squarederror", random_state=RANDOM_STATE, n_jobs=-1,
        )
    else:
        core = GradientBoostingRegressor(
            n_estimators=400, max_depth=4, learning_rate=0.035,
            random_state=RANDOM_STATE,
        )
    wait_model = Pipeline([("preprocess", wait_preprocess), ("model", core)])
    wait_model.fit(X_tr, y_tr)
    wait_pred = wait_model.predict(X_te)
    wait_metrics = {
        "mae_minutes": round(float(mean_absolute_error(y_te, wait_pred)), 3),
        "r2": round(float(r2_score(y_te, wait_pred)), 3),
    }
    print(f"waiting-time  MAE={wait_metrics['mae_minutes']}  R2={wait_metrics['r2']}")

    # ---- 3. Abandonment model -------------------------------------------
    abandon_features = [
        "basket_items", "payment_method", "assistance_required",
        "open_counters", "queue_length", "queue_position",
        "counter_load_minutes", "waiting_time_minutes",
        "hour", "day_of_week", "is_weekend", "is_peak",
    ]
    X = df[abandon_features]
    y = df["abandoned"].astype(int)
    X_tr, X_te, y_tr, y_te = train_test_split(
        X, y, test_size=0.20, random_state=RANDOM_STATE, stratify=y
    )
    num_cols = [c for c in abandon_features if c not in common_cat]
    abandon_preprocess = ColumnTransformer([
        ("num", SimpleImputer(strategy="median"), num_cols),
        ("cat", Pipeline([
            ("imputer", SimpleImputer(strategy="most_frequent")),
            ("onehot", OneHotEncoder(handle_unknown="ignore")),
        ]), common_cat),
    ])
    if XGB_AVAILABLE:
        core = XGBClassifier(
            n_estimators=400, max_depth=5, learning_rate=0.045,
            subsample=0.85, colsample_bytree=0.85,
            objective="binary:logistic", eval_metric="logloss",
            random_state=RANDOM_STATE, n_jobs=-1,
        )
    else:
        core = RandomForestClassifier(
            n_estimators=350, max_depth=10, min_samples_leaf=3,
            class_weight="balanced", random_state=RANDOM_STATE, n_jobs=-1,
        )
    abandon_model = Pipeline([("preprocess", abandon_preprocess), ("model", core)])
    abandon_model.fit(X_tr, y_tr)
    ab_pred = abandon_model.predict(X_te)
    abandon_metrics = {
        "accuracy": round(float(accuracy_score(y_te, ab_pred)), 3),
        "f1": round(float(f1_score(y_te, ab_pred, zero_division=0)), 3),
    }
    print(f"abandonment   accuracy={abandon_metrics['accuracy']}  F1={abandon_metrics['f1']}")

    bundle = {
        "model_name": "smartqueue_ml_v1",
        "trained_at": pd.Timestamp.utcnow().isoformat(),
        "service_model": service_model,
        "wait_model": wait_model,
        "abandon_model": abandon_model,
        "service_features": service_features,
        "wait_features": wait_features,
        "abandon_features": abandon_features,
        "metrics": {
            "service_time": service_metrics,
            "waiting_time": wait_metrics,
            "abandonment": abandon_metrics,
        },
        "notes": (
            "Trained on synthetic data generated by "
            "scripts/train_ml_models.py (same generator as "
            "SmartQueue_Complete_ML_Pipeline.ipynb). Replace with real "
            "store data when available. Demand model excluded (test R2<0)."
        ),
    }
    return bundle


def main() -> None:
    df = make_synthetic_smartqueue()
    print(f"Generated {len(df)} synthetic rows (seed=42, reproducible)")

    bundle = train_models(df)

    MODEL_DIR.mkdir(parents=True, exist_ok=True)
    joblib.dump(bundle, OUTPUT_PATH)
    size_kb = OUTPUT_PATH.stat().st_size // 1024
    print(f"\nSaved bundle -> {OUTPUT_PATH} ({size_kb} KB)")


if __name__ == "__main__":
    main()
