"""Shared pytest fixtures for the FrustraDAR backend suite.

Design notes (verified against the frozen backend on GitHub):

* The frozen models use PostgreSQL-specific column types (ARRAY, UUID, native ENUM), so the suite
  MUST run against a real PostgreSQL instance. Provide it via the TEST_DATABASE_URL env var
  (see requirements-dev.txt / docs/10 B-D2). SQLite is intentionally not supported and the models
  are never modified to accommodate a different engine.
* Environment variables are populated BEFORE importing the app, because `app.core.config.Settings`
  and `app.db.database.engine` are evaluated at import time.
* External services are stubbed at their real call boundaries so no network egress occurs:
    - FCM  -> app.services.notification_service.NotificationService.send_push
    - Email-> app.utils.email.send_email
* The FastAPI startup event (which calls init_db()/init_firebase()) is intentionally NOT triggered:
  TestClient is used without its context-manager form. The schema is created here via
  Base.metadata.create_all against the same engine the app uses, and dropped after each test for
  isolation.
"""

import os
import uuid

import pytest

# --- Environment must be set before importing anything from `app` ------------------------------
# A throwaway Postgres URL; override with TEST_DATABASE_URL when running the suite.
os.environ.setdefault(
    "TEST_DATABASE_URL",
    "postgresql://frustradar_user:frustradar123@127.0.0.1:5432/frustradar_test",
)
os.environ["DATABASE_URL"] = os.environ["TEST_DATABASE_URL"]
os.environ.setdefault("SECRET_KEY", "test-secret-key-not-for-production")
os.environ.setdefault("DEBUG", "true")
# Non-secret placeholders so Settings validates; external calls are stubbed, never dispatched.
os.environ.setdefault("SENDGRID_API_KEY", "test-sendgrid-key")
os.environ.setdefault("SENDGRID_FROM_EMAIL", "noreply@example.com")
os.environ.setdefault("FIREBASE_CREDENTIALS_PATH", "/tmp/firebase-nonexistent.json")

from fastapi.testclient import TestClient  # noqa: E402

from app.main import app  # noqa: E402
from app.db.database import Base, engine, SessionLocal  # noqa: E402

# Ensure every model is registered on Base.metadata before create_all. Importing app.main already
# pulls in the routers -> services -> models, but import the package explicitly to be safe.
# The alias is required: a bare `import app.models` binds the name `app` (the package) in this
# module, shadowing the FastAPI instance imported above and breaking every TestClient fixture.
import app.models as _models  # noqa: E402,F401

API = "/api/v1"


# --- DB dependency override --------------------------------------------------------------------
def _override_get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


# Override whichever `get_db` object the routes actually depend on. Overriding both is harmless;
# both resolve to the same test database anyway (SessionLocal is bound to the app engine).
for _module_path in ("app.core.dependencies", "app.db.database"):
    try:
        _mod = __import__(_module_path, fromlist=["get_db"])
        app.dependency_overrides[getattr(_mod, "get_db")] = _override_get_db
    except (ImportError, AttributeError):
        pass


@pytest.fixture(autouse=True)
def _schema():
    """Create a clean schema before each test and drop it after, for full isolation."""
    Base.metadata.create_all(bind=engine)
    yield
    Base.metadata.drop_all(bind=engine)


@pytest.fixture(autouse=True)
def _stub_external(monkeypatch):
    """Prevent any real FCM/SendGrid egress; service logic still runs, dispatch is a no-op."""
    # FCM push -> no-op success.
    monkeypatch.setattr(
        "app.services.notification_service.NotificationService.send_push",
        lambda self, *args, **kwargs: True,
        raising=False,
    )
    # SendGrid email -> no-op success.
    monkeypatch.setattr(
        "app.utils.email.send_email",
        lambda *args, **kwargs: True,
        raising=False,
    )


@pytest.fixture
def client():
    # No context manager => startup/shutdown events (init_db/init_firebase) do not fire.
    return TestClient(app)


@pytest.fixture
def db_session():
    """A direct session for asserting persisted state / manipulating rows in tests."""
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


# --- Auth helpers ------------------------------------------------------------------------------
def _unique_email(prefix: str) -> str:
    return f"{prefix}-{uuid.uuid4().hex[:8]}@example.com"


def register(client, role: str, parent_email=None):
    """Register a user and return (auth_headers, user_dict, raw_token)."""
    payload = {
        "email": _unique_email(role),
        "username": f"{role}_{uuid.uuid4().hex[:6]}",
        "password": "secret123",
        "role": role,
    }
    if parent_email is not None:
        payload["parent_email"] = parent_email
    resp = client.post(f"{API}/auth/register", json=payload)
    assert resp.status_code == 201, resp.text
    body = resp.json()
    token = body["access_token"]
    headers = {"Authorization": f"Bearer {token}"}
    return headers, body["user"], token


@pytest.fixture
def student(client):
    headers, user, token = register(client, "student")
    return {"headers": headers, "user": user, "token": token}


@pytest.fixture
def parent(client):
    headers, user, token = register(client, "parent")
    return {"headers": headers, "user": user, "token": token}


@pytest.fixture
def linked_pair(client):
    """A parent and a student linked via POST /auth/link-parent (the verified linking path)."""
    p_headers, p_user, p_token = register(client, "parent")
    s_headers, s_user, s_token = register(client, "student")
    resp = client.post(
        f"{API}/auth/link-parent",
        json={"parent_email": p_user["email"]},
        headers=s_headers,
    )
    assert resp.status_code in (200, 201), resp.text
    return {
        "parent": {"headers": p_headers, "user": p_user, "token": p_token},
        "student": {"headers": s_headers, "user": s_user, "token": s_token},
    }
