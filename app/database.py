from __future__ import annotations

import os
import sqlite3
from contextlib import contextmanager
from pathlib import Path
from typing import Iterator


PROJECT_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_DB_PATH = PROJECT_ROOT / "smartqueue.db"


def database_path() -> Path:
    return Path(os.getenv("SMARTQUEUE_DB_PATH", str(DEFAULT_DB_PATH)))


def connect() -> sqlite3.Connection:
    connection = sqlite3.connect(database_path(), timeout=10)
    connection.row_factory = sqlite3.Row
    connection.execute("PRAGMA foreign_keys = ON")
    return connection


@contextmanager
def db_session() -> Iterator[sqlite3.Connection]:
    connection = connect()
    try:
        yield connection
        connection.commit()
    except Exception:
        connection.rollback()
        raise
    finally:
        connection.close()


SCHEMA = """
CREATE TABLE IF NOT EXISTS counters (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE,
    status TEXT NOT NULL DEFAULT 'open'
        CHECK (status IN ('open', 'closed')),
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS queue_entries (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    token TEXT UNIQUE,
    customer_name TEXT NOT NULL,
    item_count INTEGER NOT NULL CHECK (item_count BETWEEN 1 AND 300),
    payment_method TEXT NOT NULL
        CHECK (payment_method IN ('upi', 'card', 'cash')),
    priority_type TEXT NOT NULL DEFAULT 'regular'
        CHECK (priority_type IN ('regular', 'assistance')),
    status TEXT NOT NULL DEFAULT 'unassigned'
        CHECK (status IN ('unassigned', 'waiting', 'serving', 'completed', 'cancelled')),
    predicted_service_seconds INTEGER NOT NULL,
    prediction_source TEXT NOT NULL,
    counter_id INTEGER,
    arrival_time TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_at TEXT,
    service_started_at TEXT,
    service_completed_at TEXT,
    actual_service_seconds INTEGER,
    FOREIGN KEY (counter_id) REFERENCES counters(id)
);

CREATE INDEX IF NOT EXISTS idx_queue_status ON queue_entries(status);
CREATE INDEX IF NOT EXISTS idx_queue_counter_status
    ON queue_entries(counter_id, status);
"""


def init_database() -> None:
    database_path().parent.mkdir(parents=True, exist_ok=True)
    with db_session() as connection:
        connection.executescript(SCHEMA)
        counter_count = connection.execute(
            "SELECT COUNT(*) AS total FROM counters"
        ).fetchone()["total"]
        if counter_count == 0:
            connection.executemany(
                "INSERT INTO counters(name, status) VALUES (?, 'open')",
                [("Counter 1",), ("Counter 2",), ("Counter 3",)],
            )

