from __future__ import annotations

import heapq
import sqlite3
from dataclasses import asdict, dataclass


@dataclass(frozen=True)
class Assignment:
    entry_id: int
    counter_id: int
    counter_name: str
    estimated_wait_seconds: int

    def as_dict(self) -> dict[str, int | str]:
        return asdict(self)


def counter_workloads(connection: sqlite3.Connection) -> list[dict[str, int | str]]:
    rows = connection.execute(
        """
        SELECT
            c.id,
            c.name,
            c.status,
            COUNT(q.id) AS active_customers,
            COALESCE(SUM(
                CASE
                    WHEN q.status = 'serving' AND q.service_started_at IS NOT NULL THEN
                        MAX(
                            q.predicted_service_seconds - CAST(
                                (julianday(CURRENT_TIMESTAMP) - julianday(q.service_started_at))
                                * 86400 AS INTEGER
                            ),
                            0
                        )
                    ELSE q.predicted_service_seconds
                END
            ), 0) AS workload_seconds
        FROM counters AS c
        LEFT JOIN queue_entries AS q
            ON q.counter_id = c.id
           AND q.status IN ('waiting', 'serving')
        GROUP BY c.id, c.name, c.status
        ORDER BY c.id
        """
    ).fetchall()
    return [dict(row) for row in rows]


def assign_to_best_counter(
    connection: sqlite3.Connection, entry_id: int
) -> Assignment:
    workloads = counter_workloads(connection)
    heap: list[tuple[int, int, str]] = [
        (int(item["workload_seconds"]), int(item["id"]), str(item["name"]))
        for item in workloads
        if item["status"] == "open"
    ]
    if not heap:
        raise ValueError("No checkout counter is currently open")

    heapq.heapify(heap)
    wait_seconds, counter_id, counter_name = heapq.heappop(heap)
    cursor = connection.execute(
        """
        UPDATE queue_entries
        SET counter_id = ?, status = 'waiting', assigned_at = CURRENT_TIMESTAMP
        WHERE id = ? AND status = 'unassigned'
        """,
        (counter_id, entry_id),
    )
    if cursor.rowcount != 1:
        raise ValueError("Queue entry is missing or has already been assigned")

    return Assignment(entry_id, counter_id, counter_name, wait_seconds)


def start_next_customer(
    connection: sqlite3.Connection, counter_id: int
) -> sqlite3.Row:
    serving = connection.execute(
        """
        SELECT id FROM queue_entries
        WHERE counter_id = ? AND status = 'serving'
        """,
        (counter_id,),
    ).fetchone()
    if serving:
        raise ValueError("Complete the current checkout before calling the next customer")

    next_entry = connection.execute(
        """
        SELECT * FROM queue_entries
        WHERE counter_id = ? AND status = 'waiting'
        ORDER BY
            CASE priority_type WHEN 'assistance' THEN 0 ELSE 1 END,
            assigned_at,
            id
        LIMIT 1
        """,
        (counter_id,),
    ).fetchone()
    if not next_entry:
        raise ValueError("There is no waiting customer for this counter")

    connection.execute(
        """
        UPDATE queue_entries
        SET status = 'serving', service_started_at = CURRENT_TIMESTAMP
        WHERE id = ?
        """,
        (next_entry["id"],),
    )
    return connection.execute(
        "SELECT * FROM queue_entries WHERE id = ?", (next_entry["id"],)
    ).fetchone()


def complete_customer(
    connection: sqlite3.Connection, entry_id: int
) -> sqlite3.Row:
    entry = connection.execute(
        "SELECT * FROM queue_entries WHERE id = ?", (entry_id,)
    ).fetchone()
    if not entry or entry["status"] != "serving":
        raise ValueError("Only a customer currently being served can be completed")

    connection.execute(
        """
        UPDATE queue_entries
        SET
            status = 'completed',
            service_completed_at = CURRENT_TIMESTAMP,
            actual_service_seconds = MAX(
                1,
                CAST((julianday(CURRENT_TIMESTAMP) - julianday(service_started_at)) * 86400 AS INTEGER)
            )
        WHERE id = ?
        """,
        (entry_id,),
    )
    return connection.execute(
        "SELECT * FROM queue_entries WHERE id = ?", (entry_id,)
    ).fetchone()
