"""Tests for ml_service FastAPI endpoints.

Covers:
- GET /health       → 200 when model bundle is loaded
- POST /predict     → 200 with valid PredictRequest
- POST /predict     → 422 with missing / invalid fields
"""
from __future__ import annotations

from unittest.mock import MagicMock

import numpy as np
import pytest
from fastapi.testclient import TestClient

# Import the service module — bundle starts as None until startup() runs.
# Tests that need a live bundle patch service.bundle directly.
import ml_service.service as service


@pytest.fixture
def client() -> TestClient:
    """Return a TestClient for the FastAPI app.

    The bundle is not loaded through the startup event; individual tests
    that need it must patch service.bundle.
    """
    return TestClient(service.app)


@pytest.fixture
def mock_bundle() -> dict:
    """Return a minimal mock bundle that satisfies the service's type guard."""
    mock_service_model = MagicMock()
    mock_service_model.predict.return_value = np.array([12.5])

    mock_wait_model = MagicMock()
    mock_wait_model.predict.return_value = np.array([8.3])

    mock_abandon_model = MagicMock()
    # predict_proba returns shape (n_samples, 2) → [prob_class_0, prob_class_1]
    mock_abandon_model.predict_proba.return_value = np.array([[0.85, 0.15]])

    return {
        "model_name": "smartqueue_ml_v1",
        "trained_at": "2026-01-01T00:00:00Z",
        "service_model": mock_service_model,
        "wait_model": mock_wait_model,
        "abandon_model": mock_abandon_model,
    }


class TestHealth:
    """Tests for GET /health."""

    def test_health_returns_200_when_bundle_loaded(self, client: TestClient, monkeypatch, mock_bundle: dict):
        """Health endpoint returns 200 and model info when bundle is set."""
        monkeypatch.setattr(service, "bundle", mock_bundle)

        response = client.get("/health")

        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "healthy"
        assert data["model"] == "smartqueue_ml_v1"
        assert "trained_at" in data

    def test_health_returns_503_when_bundle_not_loaded(self, client: TestClient, monkeypatch):
        """Health endpoint returns 503 when the model bundle is None."""
        monkeypatch.setattr(service, "bundle", None)

        response = client.get("/health")

        assert response.status_code == 503
        assert response.json()["detail"] == "Model bundle not loaded"


class TestPredict:
    """Tests for POST /predict."""

    VALID_PAYLOAD = {
        "basket_items": 8,
        "payment_method": "UPI",
        "assistance_required": 0,
        "open_counters": 3,
        "queue_length": 5,
        "queue_position": 3,
        "counter_load_minutes": 4.2,
        "hour": 14,
        "day_of_week": 3,  # Wednesday (Java 1-based)
        "is_weekend": 0,
        "is_peak": 0,
    }

    def test_predict_returns_200_with_valid_input(self, client: TestClient, monkeypatch, mock_bundle: dict):
        """Prediction endpoint returns 200 and prediction fields for valid input."""
        monkeypatch.setattr(service, "bundle", mock_bundle)

        response = client.post("/predict", json=self.VALID_PAYLOAD)

        assert response.status_code == 200
        data = response.json()
        assert "service_minutes" in data
        assert "wait_minutes" in data
        assert "abandonment_probability" in data
        assert "model_source" in data
        assert data["model_source"] == "smartqueue_ml_v1"

        # Values are floats
        assert isinstance(data["service_minutes"], float)
        assert isinstance(data["wait_minutes"], float)
        assert isinstance(data["abandonment_probability"], float)

    def test_predict_returns_422_when_basket_items_missing(self, client: TestClient, monkeypatch, mock_bundle: dict):
        """422 returned when required field basket_items is missing."""
        monkeypatch.setattr(service, "bundle", mock_bundle)
        payload = {k: v for k, v in self.VALID_PAYLOAD.items() if k != "basket_items"}

        response = client.post("/predict", json=payload)

        assert response.status_code == 422

    def test_predict_returns_422_when_basket_items_out_of_range(self, client: TestClient, monkeypatch, mock_bundle: dict):
        """422 returned when basket_items exceeds max (300)."""
        monkeypatch.setattr(service, "bundle", mock_bundle)
        payload = {**self.VALID_PAYLOAD, "basket_items": 500}

        response = client.post("/predict", json=payload)

        assert response.status_code == 422

    def test_predict_returns_422_when_payment_method_invalid(self, client: TestClient, monkeypatch, mock_bundle: dict):
        """422 returned when payment_method is not UPI|CARD|CASH."""
        monkeypatch.setattr(service, "bundle", mock_bundle)
        payload = {**self.VALID_PAYLOAD, "payment_method": "BITCOIN"}

        response = client.post("/predict", json=payload)

        assert response.status_code == 422

    def test_predict_returns_422_when_open_counters_is_zero(self, client: TestClient, monkeypatch, mock_bundle: dict):
        """422 returned when open_counters is 0 (ge=1 constraint)."""
        monkeypatch.setattr(service, "bundle", mock_bundle)
        payload = {**self.VALID_PAYLOAD, "open_counters": 0}

        response = client.post("/predict", json=payload)

        assert response.status_code == 422

    def test_predict_returns_422_when_queue_position_negative(self, client: TestClient, monkeypatch, mock_bundle: dict):
        """422 returned when queue_position is negative."""
        monkeypatch.setattr(service, "bundle", mock_bundle)
        payload = {**self.VALID_PAYLOAD, "queue_position": -1}

        response = client.post("/predict", json=payload)

        assert response.status_code == 422

    def test_predict_returns_422_when_hour_out_of_range(self, client: TestClient, monkeypatch, mock_bundle: dict):
        """422 returned when hour is outside 0-23."""
        monkeypatch.setattr(service, "bundle", mock_bundle)
        payload = {**self.VALID_PAYLOAD, "hour": 25}

        response = client.post("/predict", json=payload)

        assert response.status_code == 422

    def test_predict_accepts_day_of_week_as_string(self, client: TestClient, monkeypatch, mock_bundle: dict):
        """day_of_week accepts string names like 'FRIDAY'."""
        monkeypatch.setattr(service, "bundle", mock_bundle)
        payload = {**self.VALID_PAYLOAD, "day_of_week": "FRIDAY"}

        response = client.post("/predict", json=payload)

        assert response.status_code == 200
        assert response.json()["service_minutes"] is not None

    def test_predict_returns_503_when_bundle_not_loaded(self, client: TestClient, monkeypatch):
        """503 returned when the model bundle is None."""
        monkeypatch.setattr(service, "bundle", None)

        response = client.post("/predict", json=self.VALID_PAYLOAD)

        assert response.status_code == 503