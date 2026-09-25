import sqlite3
import unittest
from datetime import datetime

from app.services.allocator import assign_to_best_counter, counter_workloads
from app.services.predictor import predict_service_time


class QueueCoreTests(unittest.TestCase):
    def setUp(self):
        self.connection = sqlite3.connect(":memory:")
        self.connection.row_factory = sqlite3.Row
        self.connection.executescript(
            """
            CREATE TABLE counters (
                id INTEGER PRIMARY KEY,
                name TEXT NOT NULL,
                status TEXT NOT NULL
            );
            CREATE TABLE queue_entries (
                id INTEGER PRIMARY KEY,
                counter_id INTEGER,
                status TEXT NOT NULL,
                predicted_service_seconds INTEGER NOT NULL,
                assigned_at TEXT,
                service_started_at TEXT
            );
            INSERT INTO counters VALUES (1, 'Counter 1', 'open');
            INSERT INTO counters VALUES (2, 'Counter 2', 'open');
            INSERT INTO counters VALUES (3, 'Counter 3', 'closed');
            INSERT INTO queue_entries VALUES (1, 1, 'waiting', 250, CURRENT_TIMESTAMP, NULL);
            INSERT INTO queue_entries VALUES (2, NULL, 'unassigned', 100, NULL, NULL);
            """
        )

    def tearDown(self):
        self.connection.close()

    def test_allocator_chooses_open_counter_with_lowest_workload(self):
        assignment = assign_to_best_counter(self.connection, 2)
        self.assertEqual(assignment.counter_id, 2)
        self.assertEqual(assignment.estimated_wait_seconds, 0)

    def test_counter_workload_excludes_closed_counter_from_no_calculation(self):
        workloads = counter_workloads(self.connection)
        by_id = {row["id"]: row for row in workloads}
        self.assertEqual(by_id[1]["workload_seconds"], 250)
        self.assertEqual(by_id[2]["workload_seconds"], 0)

    def test_fallback_prediction_increases_with_basket_size(self):
        moment = datetime(2026, 9, 22, 10, 0)
        small, _ = predict_service_time(5, "upi", moment)
        large, _ = predict_service_time(40, "upi", moment)
        self.assertGreater(large, small)


if __name__ == "__main__":
    unittest.main()
