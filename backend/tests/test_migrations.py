"""Alembic migration head verification (FD-26).

This is a static check against the migration scripts: the current head must be `b2c3d4e5f6a7`
(the "add behavioral fields" revision). It does not run migrations against a database, so it does
not interfere with the create_all/drop_all schema fixture used by the rest of the suite. Run from
the `backend/` directory so `alembic.ini` is discoverable.
"""

import os

import pytest

pytestmark = pytest.mark.migrations

EXPECTED_HEAD = "b2c3d4e5f6a7"


@pytest.mark.skipif(
    not os.path.exists("alembic.ini"),
    reason="alembic.ini not found; run this test from the backend/ directory",
)
def test_alembic_head_is_frozen_revision():
    from alembic.config import Config
    from alembic.script import ScriptDirectory

    script = ScriptDirectory.from_config(Config("alembic.ini"))
    heads = script.get_heads()
    assert heads == [EXPECTED_HEAD], f"expected single head {EXPECTED_HEAD!r}, got {heads!r}"