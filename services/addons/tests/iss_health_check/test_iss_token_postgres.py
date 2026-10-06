"""Run with ISS_TEST_DATABASE_URL pointing to a local PostgreSQL instance.

The test user needs CREATE DATABASE permission; each test gets a temporary database.
"""

from concurrent.futures import ThreadPoolExecutor
import os
from pathlib import Path
import time
import uuid
from unittest.mock import Mock

from cryptography.fernet import Fernet
import pytest
from sqlalchemy import create_engine, text
from sqlalchemy.exc import DBAPIError

from addons.images.iss_health_check import iss_token


@pytest.fixture
def database(monkeypatch):
    url = os.environ.get("ISS_TEST_DATABASE_URL")
    if not url:
        pytest.skip("Set ISS_TEST_DATABASE_URL to run PostgreSQL integration tests")
    admin = create_engine(url, isolation_level="AUTOCOMMIT")
    name = "iss_test_" + uuid.uuid4().hex
    with admin.connect() as connection:
        connection.exec_driver_sql(f'CREATE DATABASE "{name}"')
    engine = create_engine(admin.url.set(database=name))
    try:
        with engine.begin() as connection:
            connection.exec_driver_sql(
                "CREATE TABLE public.iss_keys (iss_key_id serial PRIMARY KEY, "
                "common_name varchar(128) NOT NULL, token varchar(128) NOT NULL)"
            )
            connection.exec_driver_sql(
                "INSERT INTO public.iss_keys (common_name, token) "
                "VALUES ('older', 'older-token'), ('old', 'old-token')"
            )
            migration = (
                Path(__file__).resolve().parents[4]
                / "resources/db/migration/V6__iss_keys_encrypted_token.sql"
            )
            for statement in migration.read_text().split(";"):
                if statement.strip():
                    connection.exec_driver_sql(statement)
        monkeypatch.setattr(
            iss_token.pgquery,
            "init_connection_engine",
            lambda: create_engine(engine.url),
        )
        monkeypatch.setattr(
            iss_token.iss_health_check_environment,
            "ISS_TOKEN_ENCRYPTION_KEYS",
            [Fernet.generate_key().decode()],
        )
        yield engine
    finally:
        engine.dispose()
        with admin.connect() as connection:
            connection.exec_driver_sql(f'DROP DATABASE "{name}" WITH (FORCE)')
        admin.dispose()


def test_migration_and_refresh_keep_one_encrypted_token(database, monkeypatch):
    with database.connect() as connection:
        assert connection.execute(
            text("SELECT common_name, token FROM iss_keys")
        ).all() == [("old", "old-token")]
    post = Mock(return_value=Mock(ok=True, json=lambda: {"Item": "new-token"}))
    monkeypatch.setattr(iss_token.requests, "post", post)

    def revoke(*args, **kwargs):
        with database.connect() as connection:
            rows = connection.execute(text("SELECT token FROM iss_keys")).all()
            assert len(rows) == 1
            assert rows[0][0] != "new-token"
            assert iss_token.decrypt_token(rows[0][0]) == "new-token"
        return Mock(ok=True)

    monkeypatch.setattr(iss_token.requests, "delete", revoke)
    with iss_token.token_for_check() as token:
        assert token == "new-token"
    assert post.call_args.kwargs["headers"] == {"x-api-key": "old-token"}


def test_overlapping_checks_wait_until_token_use_finishes(database, monkeypatch):
    post = Mock(
        side_effect=[
            Mock(ok=True, json=lambda: {"Item": "first-token"}),
            Mock(ok=True, json=lambda: {"Item": "second-token"}),
        ]
    )
    revoke = Mock(return_value=Mock(ok=True))
    monkeypatch.setattr(iss_token.requests, "post", post)
    monkeypatch.setattr(iss_token.requests, "delete", revoke)

    def second_check():
        with iss_token.token_for_check() as token:
            return token

    with ThreadPoolExecutor(max_workers=1) as executor:
        with iss_token.token_for_check() as first:
            future = executor.submit(second_check)
            deadline = time.monotonic() + 5
            while time.monotonic() < deadline:
                with database.connect() as connection:
                    waiting = connection.execute(
                        text(
                            "SELECT count(*) FROM pg_locks WHERE locktype = 'advisory' "
                            "AND classid = 73021 AND objid = 1 AND NOT granted "
                            "AND database = (SELECT oid FROM pg_database WHERE datname = current_database())"
                        )
                    ).scalar_one()
                if waiting:
                    break
                time.sleep(0.02)
            assert waiting == 1
            assert first == "first-token"
            assert post.call_count == 1
            assert not future.done()
        assert future.result(timeout=5) == "second-token"
    assert post.call_args.kwargs["headers"] == {"x-api-key": "first-token"}
    with database.connect() as connection:
        tokens = connection.execute(text("SELECT token FROM iss_keys")).scalars().all()
        assert len(tokens) == 1
        assert iss_token.decrypt_token(tokens[0]) == "second-token"


def test_failure_releases_lock_and_preserves_previous_token(database, monkeypatch):
    monkeypatch.setattr(
        iss_token.requests, "post", Mock(side_effect=iss_token.requests.Timeout())
    )
    with pytest.raises(iss_token.requests.Timeout):
        with iss_token.token_for_check():
            pytest.fail("Token creation should fail")
    with database.connect() as connection:
        assert (
            connection.execute(text("SELECT token FROM iss_keys")).scalar_one()
            == "old-token"
        )
        assert connection.execute(
            text("SELECT pg_try_advisory_lock(73021, 1)")
        ).scalar_one()
        connection.execute(text("SELECT pg_advisory_unlock(73021, 1)"))


def test_bootstrap_and_device_failure_preserve_committed_token(database, monkeypatch):
    with database.begin() as connection:
        connection.execute(text("DELETE FROM iss_keys"))
    monkeypatch.setattr(
        iss_token.requests,
        "post",
        Mock(return_value=Mock(ok=True, json=lambda: {"Item": "bootstrap-token"})),
    )
    revoke = Mock()
    monkeypatch.setattr(iss_token.requests, "delete", revoke)
    with pytest.raises(RuntimeError, match="device request"):
        with iss_token.token_for_check():
            raise RuntimeError("device request failed")
    revoke.assert_not_called()
    with database.connect() as connection:
        assert (
            iss_token.decrypt_token(
                connection.execute(text("SELECT token FROM iss_keys")).scalar_one()
            )
            == "bootstrap-token"
        )
        assert connection.execute(
            text("SELECT pg_try_advisory_lock(73021, 1)")
        ).scalar_one()
        connection.execute(text("SELECT pg_advisory_unlock(73021, 1)"))


def test_database_rejects_refresh_without_losing_old_token(database, monkeypatch):
    with database.begin() as connection:
        connection.exec_driver_sql(
            "ALTER TABLE iss_keys ADD CONSTRAINT reject_new_token CHECK (token = 'old-token')"
        )
    monkeypatch.setattr(
        iss_token.requests,
        "post",
        Mock(return_value=Mock(ok=True, json=lambda: {"Item": "new-token"})),
    )
    revoke = Mock()
    monkeypatch.setattr(iss_token.requests, "delete", revoke)
    with pytest.raises(DBAPIError, match="reject_new_token"):
        with iss_token.token_for_check():
            pytest.fail("Storage should fail")
    revoke.assert_not_called()
    with database.connect() as connection:
        assert (
            connection.execute(text("SELECT token FROM iss_keys")).scalar_one()
            == "old-token"
        )
        assert connection.execute(
            text("SELECT pg_try_advisory_lock(73021, 1)")
        ).scalar_one()
        connection.execute(text("SELECT pg_advisory_unlock(73021, 1)"))
