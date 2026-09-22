# SmartQueue

SmartQueue is an ML-assisted retail checkout queue-management prototype. It
predicts checkout service time and uses a min-heap to assign customers to the
open counter with the smallest predicted workload.

## Current progress

- Customer check-in and unique digital token
- Checkout-time prediction with ML-model support and a fallback formula
- Min-heap-based least-workload counter assignment
- Cashier actions: call next and complete checkout
- Safe counter opening/closing rules
- Live statistics and responsive management dashboard
- Reproducible synthetic data and three-model comparison
- SRS, architecture, hypothesis, evaluation plan, and roadmap

The included generated dataset is explicitly synthetic. It must not be
presented as observed store data.

## Technology

- Python 3.10+
- FastAPI and Uvicorn
- SQLite for the portable MVP
- HTML, CSS, and JavaScript
- pandas and scikit-learn for model training
- Python `heapq` for priority-queue allocation

## Quick start

### Windows PowerShell

```powershell
python -m venv .venv
.venv\Scripts\Activate.ps1
pip install -r requirements.txt
python scripts\generate_synthetic_data.py
python scripts\train_model.py
uvicorn app.main:app --reload
```

### WSL, Linux, or macOS

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python scripts/generate_synthetic_data.py
python scripts/train_model.py
uvicorn app.main:app --reload
```

Open:

- Dashboard: <http://127.0.0.1:8000>
- Interactive API documentation: <http://127.0.0.1:8000/docs>

The database and three default counters are created automatically on first run.

## Demonstration sequence

1. Open the dashboard and select **Load demo queue**.
2. Explain that predicted checkout durations form each counter's workload.
3. Add a new customer and show the selected least-loaded counter.
4. Select **Call next** on a counter.
5. Complete the active checkout and show updated statistics.
6. Open `/docs` to demonstrate and test the API contract.

## Algorithm

For each open counter, SmartQueue sums the predicted durations of assigned
customers. It builds a min-heap containing:

```text
(predicted workload, counter id, counter name)
```

The minimum element is the counter expected to become free first. Building the
heap costs `O(C)` and selecting a counter costs `O(log C)`, where `C` is the
number of open counters. Assignment within a counter remains ordered; an
assistance flag is available for a documented accessibility policy.

## Test

```bash
python -m unittest discover -s tests -v
```

## Important documents

- [`docs/SRS.md`](docs/SRS.md) — scope, requirements, architecture, and data model
- [`docs/PROJECT_PLAN.md`](docs/PROJECT_PLAN.md) — hypothesis, workstreams, ethics, and roadmap
- [`docs/FRIDAY_DEMO.md`](docs/FRIDAY_DEMO.md) — demonstration order, script, and likely questions
- `docs/MODEL_RESULTS.md` — generated after model training
- `data/real_observation_template.csv` — anonymous real-data collection structure

## Next development milestone

Collect permission-based anonymised checkout observations, implement the
baseline-versus-SmartQueue simulation, test the hypothesis, and then migrate
the persistence layer to PostgreSQL for the final submission.
