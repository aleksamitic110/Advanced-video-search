import json
import sqlite3
from contextlib import contextmanager
from pathlib import Path
from typing import Any, Iterator

from .config import get_settings


def get_connection() -> sqlite3.Connection:
    settings = get_settings()
    settings.ensure_dirs()
    connection = sqlite3.connect(settings.database_path)
    connection.row_factory = sqlite3.Row
    connection.execute("PRAGMA foreign_keys = ON")
    return connection


@contextmanager
def connect() -> Iterator[sqlite3.Connection]:
    connection = get_connection()
    try:
        yield connection
        connection.commit()
    finally:
        connection.close()


def init_db() -> None:
    schema_path = Path(__file__).with_name("schema.sql")
    with connect() as connection:
        connection.executescript(schema_path.read_text(encoding="utf-8"))


def row_to_dict(row: sqlite3.Row | None) -> dict[str, Any] | None:
    if row is None:
        return None
    return {key: row[key] for key in row.keys()}


def rows_to_dicts(rows: list[sqlite3.Row]) -> list[dict[str, Any]]:
    return [row_to_dict(row) or {} for row in rows]


def json_dumps(value: Any) -> str:
    return json.dumps(value, ensure_ascii=True, separators=(",", ":"))


def json_loads(value: str | None, default: Any = None) -> Any:
    if not value:
        return default
    return json.loads(value)
