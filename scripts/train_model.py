"""Compare regression models and persist the best validation performer."""

from pathlib import Path

import joblib
import pandas as pd
from sklearn.compose import ColumnTransformer
from sklearn.ensemble import RandomForestRegressor
from sklearn.linear_model import LinearRegression
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder
from sklearn.tree import DecisionTreeRegressor


PROJECT_ROOT = Path(__file__).resolve().parents[1]
DATA_PATH = PROJECT_ROOT / "data" / "synthetic_checkout_history.csv"
MODEL_PATH = PROJECT_ROOT / "ml" / "checkout_model.joblib"
RESULTS_PATH = PROJECT_ROOT / "docs" / "MODEL_RESULTS.md"


def build_pipeline(regressor) -> Pipeline:
    transformer = ColumnTransformer(
        [
            ("payment", OneHotEncoder(handle_unknown="ignore"), ["payment_method"]),
        ],
        remainder="passthrough",
    )
    return Pipeline([("features", transformer), ("regressor", regressor)])


def main() -> None:
    if not DATA_PATH.exists():
        raise SystemExit("Run scripts/generate_synthetic_data.py first")

    data = pd.read_csv(DATA_PATH)
    features = data[["item_count", "payment_method", "hour_of_day", "day_of_week"]]
    target = data["service_seconds"]
    x_train, x_test, y_train, y_test = train_test_split(
        features, target, test_size=0.2, random_state=42
    )

    candidates = {
        "Linear Regression": LinearRegression(),
        "Decision Tree": DecisionTreeRegressor(max_depth=8, random_state=42),
        "Random Forest": RandomForestRegressor(
            n_estimators=120, max_depth=10, min_samples_leaf=3, random_state=42
        ),
    }
    results = []
    trained = {}
    for name, estimator in candidates.items():
        pipeline = build_pipeline(estimator)
        pipeline.fit(x_train, y_train)
        prediction = pipeline.predict(x_test)
        metrics = {
            "name": name,
            "mae": mean_absolute_error(y_test, prediction),
            "rmse": mean_squared_error(y_test, prediction) ** 0.5,
            "r2": r2_score(y_test, prediction),
        }
        results.append(metrics)
        trained[name] = pipeline

    best = min(results, key=lambda result: result["mae"])
    MODEL_PATH.parent.mkdir(parents=True, exist_ok=True)
    joblib.dump(
        {
            "model": trained[best["name"]],
            "model_name": best["name"],
            "validation_mae_seconds": round(best["mae"], 3),
            "training_data": "synthetic_simulation",
        },
        MODEL_PATH,
    )

    rows = "\n".join(
        f"| {item['name']} | {item['mae']:.2f} | {item['rmse']:.2f} | {item['r2']:.3f} |"
        for item in sorted(results, key=lambda result: result["mae"])
    )
    report = f"""# Model comparison

These results use **synthetic simulation data**, not observations from a real store.
They validate the pipeline only; real-world claims require real checkout observations.

| Model | MAE (seconds) | RMSE (seconds) | R² |
|---|---:|---:|---:|
{rows}

Selected model: **{best['name']}**, using lowest validation MAE.
"""
    RESULTS_PATH.write_text(report, encoding="utf-8")
    print(report)
    print(f"Saved model at {MODEL_PATH}")


if __name__ == "__main__":
    main()

