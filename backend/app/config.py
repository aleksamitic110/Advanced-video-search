from functools import lru_cache
import os
from pathlib import Path


class Settings:
    def __init__(self) -> None:
        env = _read_env_file(Path(".env"))
        self.youtube_api_key = _get("YOUTUBE_API_KEY", env, "")
        self.data_dir = Path(_get("DATA_DIR", env, "./data"))
        self.frame_interval_seconds = int(_get("FRAME_INTERVAL_SECONDS", env, "8"))
        self.enable_ytdlp = _get("ENABLE_YTDLP", env, "false").lower() == "true"
        self.backend_cors_origins = _get(
            "BACKEND_CORS_ORIGINS",
            env,
            "chrome-extension://*,http://localhost:8000,http://127.0.0.1:8000",
        )

    @property
    def database_path(self) -> Path:
        return self.data_dir / "yt_search.sqlite3"

    @property
    def frames_dir(self) -> Path:
        return self.data_dir / "frames"

    @property
    def videos_dir(self) -> Path:
        return self.data_dir / "videos"

    @property
    def uploads_dir(self) -> Path:
        return self.data_dir / "uploads"

    @property
    def cors_origins(self) -> list[str]:
        return [origin.strip() for origin in self.backend_cors_origins.split(",") if origin.strip()]

    def ensure_dirs(self) -> None:
        self.data_dir.mkdir(parents=True, exist_ok=True)
        self.frames_dir.mkdir(parents=True, exist_ok=True)
        self.videos_dir.mkdir(parents=True, exist_ok=True)
        self.uploads_dir.mkdir(parents=True, exist_ok=True)


@lru_cache
def get_settings() -> Settings:
    settings = Settings()
    settings.ensure_dirs()
    return settings


def _get(key: str, env: dict[str, str], default: str) -> str:
    return os.environ.get(key, env.get(key, default))


def _read_env_file(path: Path) -> dict[str, str]:
    if not path.exists():
        return {}
    values: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            continue
        key, value = stripped.split("=", 1)
        values[key.strip()] = value.strip().strip('"').strip("'")
    return values
