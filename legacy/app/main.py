from __future__ import annotations

from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import HTMLResponse
from fastapi.staticfiles import StaticFiles
from fastapi.templating import Jinja2Templates

from app.database import db_session, init_database
from app.schemas import CounterStatusRequest, QueueJoinRequest
from app.services.allocator import (
    assign_to_best_counter,
    complete_customer,
    counter_workloads,
    start_next_customer,
)
from app.services.predictor import model_status, predict_service_time


APP_DIR = Path(__file__).resolve().parent


@asynccontextmanager
async def lifespan(_: FastAPI):
    init_database()
    yield


app = FastAPI(
    title="SmartQueue API",
    version="0.1.0",
    description="ML-assisted checkout queue optimisation prototype",
    lifespan=lifespan,
)
app.mount("/static", StaticFiles(directory=APP_DIR / "static"), name="static")
templates = Jinja2Templates(directory=APP_DIR / "templates")


def _entry_dict(row) -> dict:
    item = dict(row)
    if item.get("predicted_service_seconds") is not None:
        item["predicted_minutes"] = round(item["predicted_service_seconds"] / 60, 1)
    return item


def _active_entries(connection) -> list[dict]:
    rows = connection.execute(
        """
        SELECT q.*, c.name AS counter_name
        FROM queue_entries AS q
        LEFT JOIN counters AS c ON c.id = q.counter_id
        WHERE q.status IN ('waiting', 'serving')
        ORDER BY
            CASE q.status WHEN 'serving' THEN 0 ELSE 1 END,
            q.assigned_at,
            q.id
        """
    ).fetchall()
    return [_entry_dict(row) for row in rows]


def _statistics(connection) -> dict:
    row = connection.execute(
        """
        SELECT
            SUM(CASE WHEN status = 'waiting' THEN 1 ELSE 0 END) AS waiting,
            SUM(CASE WHEN status = 'serving' THEN 1 ELSE 0 END) AS serving,
            SUM(CASE WHEN status = 'completed' THEN 1 ELSE 0 END) AS completed,
            AVG(
                CASE WHEN service_started_at IS NOT NULL
                THEN (julianday(service_started_at) - julianday(arrival_time)) * 1440
                END
            ) AS average_wait_minutes,
            AVG(
                CASE WHEN status = 'completed'
                THEN actual_service_seconds
                END
            ) AS average_service_seconds
        FROM queue_entries
        """
    ).fetchone()
    completed_last_hour = connection.execute(
        """
        SELECT COUNT(*) AS total
        FROM queue_entries
        WHERE status = 'completed'
          AND service_completed_at >= datetime('now', '-1 hour')
        """
    ).fetchone()["total"]
    return {
        "waiting": int(row["waiting"] or 0),
        "serving": int(row["serving"] or 0),
        "completed": int(row["completed"] or 0),
        "average_wait_minutes": round(float(row["average_wait_minutes"] or 0), 1),
        "average_service_seconds": round(float(row["average_service_seconds"] or 0), 1),
        "throughput_last_hour": int(completed_last_hour),
    }


@app.get("/", response_class=HTMLResponse)
def dashboard(request: Request):
    return templates.TemplateResponse(
        request=request,
        name="index.html",
        context={"title": "SmartQueue Control Centre"},
    )


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "healthy"}


@app.get("/api/state")
def state() -> dict:
    with db_session() as connection:
        return {
            "statistics": _statistics(connection),
            "counters": counter_workloads(connection),
            "queue": _active_entries(connection),
            "model": model_status(),
        }


@app.post("/api/queue/join", status_code=201)
def join_queue(payload: QueueJoinRequest) -> dict:
    predicted_seconds, source = predict_service_time(
        payload.item_count, payload.payment_method
    )
    try:
        with db_session() as connection:
            open_counters = connection.execute(
                "SELECT COUNT(*) AS total FROM counters WHERE status = 'open'"
            ).fetchone()["total"]
            if not open_counters:
                raise ValueError("No checkout counter is currently open")

            cursor = connection.execute(
                """
                INSERT INTO queue_entries(
                    customer_name,
                    item_count,
                    payment_method,
                    priority_type,
                    predicted_service_seconds,
                    prediction_source
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                (
                    payload.customer_name,
                    payload.item_count,
                    payload.payment_method,
                    payload.priority_type,
                    predicted_seconds,
                    source,
                ),
            )
            entry_id = int(cursor.lastrowid)
            token = f"SQ-{entry_id:04d}"
            connection.execute(
                "UPDATE queue_entries SET token = ? WHERE id = ?",
                (token, entry_id),
            )
            assignment = assign_to_best_counter(connection, entry_id)
            entry = connection.execute(
                """
                SELECT q.*, c.name AS counter_name
                FROM queue_entries q
                JOIN counters c ON c.id = q.counter_id
                WHERE q.id = ?
                """,
                (entry_id,),
            ).fetchone()
            result = _entry_dict(entry)
            result["estimated_wait_seconds"] = assignment.estimated_wait_seconds
            return result
    except ValueError as exc:
        raise HTTPException(status_code=409, detail=str(exc)) from exc


@app.post("/api/counters/{counter_id}/next")
def call_next(counter_id: int) -> dict:
    try:
        with db_session() as connection:
            counter = connection.execute(
                "SELECT * FROM counters WHERE id = ? AND status = 'open'",
                (counter_id,),
            ).fetchone()
            if not counter:
                raise ValueError("This counter does not exist or is closed")
            return _entry_dict(start_next_customer(connection, counter_id))
    except ValueError as exc:
        raise HTTPException(status_code=409, detail=str(exc)) from exc


@app.post("/api/queue/{entry_id}/complete")
def finish_checkout(entry_id: int) -> dict:
    try:
        with db_session() as connection:
            return _entry_dict(complete_customer(connection, entry_id))
    except ValueError as exc:
        raise HTTPException(status_code=409, detail=str(exc)) from exc


@app.patch("/api/counters/{counter_id}")
def change_counter_status(counter_id: int, payload: CounterStatusRequest) -> dict:
    with db_session() as connection:
        counter = connection.execute(
            "SELECT * FROM counters WHERE id = ?", (counter_id,)
        ).fetchone()
        if not counter:
            raise HTTPException(status_code=404, detail="Counter not found")

        active_count = connection.execute(
            """
            SELECT COUNT(*) AS total FROM queue_entries
            WHERE counter_id = ? AND status IN ('waiting', 'serving')
            """,
            (counter_id,),
        ).fetchone()["total"]
        if payload.status == "closed" and active_count:
            raise HTTPException(
                status_code=409,
                detail="Complete this counter's assigned customers before closing it",
            )

        connection.execute(
            "UPDATE counters SET status = ? WHERE id = ?",
            (payload.status, counter_id),
        )
        return {"id": counter_id, "name": counter["name"], "status": payload.status}


DEMO_CUSTOMERS = [
    ("Demo Customer 1", 8, "upi", "regular"),
    ("Demo Customer 2", 32, "card", "regular"),
    ("Demo Customer 3", 15, "cash", "regular"),
    ("Demo Customer 4", 48, "upi", "regular"),
    ("Demo Customer 5", 6, "card", "assistance"),
    ("Demo Customer 6", 24, "upi", "regular"),
]


@app.post("/api/demo/load", status_code=201)
def load_demo() -> dict[str, int | str]:
    with db_session() as connection:
        existing = connection.execute(
            """
            SELECT COUNT(*) AS total FROM queue_entries
            WHERE customer_name LIKE 'Demo Customer %'
              AND status IN ('waiting', 'serving')
            """
        ).fetchone()["total"]
        if existing:
            raise HTTPException(status_code=409, detail="Demo queue is already loaded")

        loaded = 0
        for name, items, payment, priority in DEMO_CUSTOMERS:
            seconds, source = predict_service_time(items, payment)
            cursor = connection.execute(
                """
                INSERT INTO queue_entries(
                    customer_name, item_count, payment_method, priority_type,
                    predicted_service_seconds, prediction_source
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                (name, items, payment, priority, seconds, source),
            )
            entry_id = int(cursor.lastrowid)
            connection.execute(
                "UPDATE queue_entries SET token = ? WHERE id = ?",
                (f"SQ-{entry_id:04d}", entry_id),
            )
            assign_to_best_counter(connection, entry_id)
            loaded += 1
        return {"message": "Demo queue loaded", "customers_loaded": loaded}


@app.post("/api/demo/reset")
def reset_demo() -> dict[str, str]:
    with db_session() as connection:
        connection.execute("DELETE FROM queue_entries")
        connection.execute("DELETE FROM sqlite_sequence WHERE name = 'queue_entries'")
    return {"message": "Prototype queue data reset"}

