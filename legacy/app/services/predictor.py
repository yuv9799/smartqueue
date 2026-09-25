from __future__ import annotations

import os
from datetime import datetime
from functools import lru_cache
from pathlib import Path
from typing import Any


PROJECT_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_MODEL_PATH = PROJECT_ROOT / "ml" / "checkout_model.joblib"


@lru_cache(maxsize=1)
def _load_model_bundle() -> dict[str, Any] | None:
    model_path = Path(os.getenv("SMARTQUEUE_MODEL_PATH", str(DEFAULT_MODEL_PATH)))
    if not model_path.exists():
        return None

    try:
        import joblib

        bundle = joblib.load(model_path)
        return bundle if isinstance(bundle, dict) else {"model": bundle}
    except Exception:
        # The application remains usable even when an optional model is damaged
        # or its Python dependency is not installed.
        return None


def _heuristic_prediction(
    item_count: int, payment_method: str, hour_of_day: int
) -> int:
    payment_overhead = {"upi": 8, "card": 15, "cash": 24}[payment_method]
    peak_overhead = 12 if hour_of_day in {12, 13, 17, 18, 19, 20} else 0
    predicted = 32 + (item_count * 4.8) + payment_overhead + peak_overhead
    return round(max(45, min(predicted, 900)))


def predict_service_time(
    item_count: int,
    payment_method: str,
    moment: datetime | None = None,
) -> tuple[int, str]:
    moment = moment or datetime.now()
    bundle = _load_model_bundle()

    if bundle and bundle.get("model") is not None:
        try:
            import pandas as pd

            features = pd.DataFrame(
                [
                    {
                        "item_count": item_count,
                        "payment_method": payment_method,
                        "hour_of_day": moment.hour,
                        "day_of_week": moment.weekday(),
                    }
                ]
            )
            seconds = float(bundle["model"].predict(features)[0])
            model_name = str(bundle.get("model_name", "trained model"))
            return round(max(45, min(seconds, 900))), model_name
        except Exception:
            pass

    return (
        _heuristic_prediction(item_count, payment_method, moment.hour),
        "explainable fallback formula",
    )


def model_status() -> dict[str, str | bool]:
    bundle = _load_model_bundle()
    if not bundle:
        return {
            "trained_model_loaded": False,
            "name": "Explainable fallback formula",
        }
    return {
        "trained_model_loaded": True,
        "name": str(bundle.get("model_name", "Trained regression model")),
    }

