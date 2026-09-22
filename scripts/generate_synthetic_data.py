"""Generate labelled synthetic checkout history for development only.

This file must never be presented as real observed store data. Its purpose is
to make the ML pipeline reproducible until permission-based observations are
collected from a real checkout location.
"""

from pathlib import Path

import numpy as np
import pandas as pd


PROJECT_ROOT = Path(__file__).resolve().parents[1]
OUTPUT = PROJECT_ROOT / "data" / "synthetic_checkout_history.csv"


def main(rows: int = 2500) -> None:
    rng = np.random.default_rng(42)
    item_count = np.clip(rng.negative_binomial(4, 0.2, rows) + 1, 1, 120)
    payment_method = rng.choice(
        ["upi", "card", "cash"], size=rows, p=[0.57, 0.25, 0.18]
    )
    hour_of_day = rng.integers(9, 22, rows)
    day_of_week = rng.integers(0, 7, rows)

    payment_seconds = np.select(
        [payment_method == "upi", payment_method == "card", payment_method == "cash"],
        [8, 15, 25],
    )
    peak_seconds = np.where(np.isin(hour_of_day, [12, 13, 17, 18, 19, 20]), 14, 0)
    weekend_seconds = np.where(day_of_week >= 5, 6, 0)
    noise = rng.normal(0, 18, rows)
    service_seconds = np.clip(
        32 + item_count * 4.9 + payment_seconds + peak_seconds + weekend_seconds + noise,
        35,
        900,
    ).round().astype(int)

    frame = pd.DataFrame(
        {
            "item_count": item_count,
            "payment_method": payment_method,
            "hour_of_day": hour_of_day,
            "day_of_week": day_of_week,
            "service_seconds": service_seconds,
            "data_source": "synthetic_simulation",
        }
    )
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    frame.to_csv(OUTPUT, index=False)
    print(f"Created {len(frame)} labelled synthetic rows at {OUTPUT}")


if __name__ == "__main__":
    main()

