import shutil
import subprocess
import sys
import os
from pathlib import Path

from ..config import Settings, get_settings
from ..database import connect
from ..indexing.vector_index import VectorIndex
from ..utils import parse_youtube_video_id, youtube_watch_url
from .youtube_client import YouTubeClient, YouTubeMetadata


class IngestionPipeline:
    def __init__(self, settings: Settings | None = None) -> None:
        self.settings = settings or get_settings()
        self.youtube = YouTubeClient(self.settings.youtube_api_key)
        self.vector_index = VectorIndex()

    def import_urls(self, urls: list[str]) -> list[dict]:
        results = []
        for url in urls:
            video_id = parse_youtube_video_id(url)
            if not video_id:
                results.append({"video_id": "", "url": url, "status": "failed", "error": "Invalid YouTube URL"})
                continue
            try:
                self.import_video(video_id, youtube_watch_url(video_id))
                results.append({"video_id": video_id, "url": youtube_watch_url(video_id), "status": "indexed", "error": ""})
            except Exception as exc:
                self._mark_failed(video_id, youtube_watch_url(video_id), str(exc))
                results.append({"video_id": video_id, "url": youtube_watch_url(video_id), "status": "failed", "error": str(exc)})
        return results

    def import_video(self, video_id: str, url: str) -> None:
        self._upsert_video(video_id, url, "fetching_metadata")
        metadata = self.youtube.fetch_metadata(video_id)
        self._store_metadata(url, metadata)
        self._replace_text_docs(video_id)
        self._insert_text_doc(video_id, "metadata", "title", None, metadata.title, metadata.title)
        self._insert_text_doc(video_id, "description", "description", None, metadata.title, metadata.description)

        comments = self.youtube.fetch_comments(video_id)
        for index, comment in enumerate(comments):
            self._insert_text_doc(video_id, "comment", str(index), None, metadata.title, comment)

        transcript_chunks = self.youtube.fetch_transcript_chunks(video_id)
        for index, chunk in enumerate(transcript_chunks):
            self._insert_text_doc(
                video_id,
                "transcript",
                str(index),
                chunk.get("timestamp_seconds"),
                metadata.title,
                chunk.get("content", ""),
            )

        frame_count = 0
        if self.settings.enable_ytdlp:
            self._update_status(video_id, "extracting_frames")
            frame_count = self._download_and_extract_frames(video_id, url)

        with connect() as connection:
            connection.execute(
                """
                UPDATE videos
                SET status = 'indexed',
                    error = '',
                    comment_count = ?,
                    transcript_count = ?,
                    frame_count = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE video_id = ?
                """,
                (len(comments), len(transcript_chunks), frame_count, video_id),
            )

    def _upsert_video(self, video_id: str, url: str, status: str) -> None:
        with connect() as connection:
            connection.execute(
                """
                INSERT INTO videos(video_id, url, status)
                VALUES (?, ?, ?)
                ON CONFLICT(video_id) DO UPDATE SET
                    url = excluded.url,
                    status = excluded.status,
                    updated_at = CURRENT_TIMESTAMP
                """,
                (video_id, url, status),
            )

    def _store_metadata(self, url: str, metadata: YouTubeMetadata) -> None:
        with connect() as connection:
            connection.execute(
                """
                UPDATE videos
                SET title = ?,
                    description = ?,
                    thumbnail_url = ?,
                    channel_title = ?,
                    duration = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE video_id = ?
                """,
                (
                    metadata.title,
                    metadata.description,
                    metadata.thumbnail_url,
                    metadata.channel_title,
                    metadata.duration,
                    metadata.video_id,
                ),
            )

    def _replace_text_docs(self, video_id: str) -> None:
        with connect() as connection:
            connection.execute("DELETE FROM text_docs WHERE video_id = ?", (video_id,))
            connection.execute("DELETE FROM frames WHERE video_id = ?", (video_id,))

    def _insert_text_doc(
        self,
        video_id: str,
        source_type: str,
        source_id: str,
        timestamp_seconds: int | None,
        title: str,
        content: str,
    ) -> None:
        if not content.strip():
            return
        with connect() as connection:
            connection.execute(
                """
                INSERT INTO text_docs(video_id, source_type, source_id, timestamp_seconds, title, content)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                (video_id, source_type, source_id, timestamp_seconds, title, content),
            )

    def _download_and_extract_frames(self, video_id: str, url: str) -> int:
        ffmpeg_path = self._find_ffmpeg()
        if not ffmpeg_path:
            raise RuntimeError(
                "ENABLE_YTDLP=true, but ffmpeg is not available. Install ffmpeg on PATH "
                "or put ffmpeg.exe in backend/tools/ffmpeg/bin."
            )
        yt_dlp_command = self._yt_dlp_command()

        video_path = self._find_downloaded_video(video_id)
        if not video_path:
            output_template = self.settings.videos_dir / f"{video_id}.%(ext)s"
            self._run_command(
                [
                    *yt_dlp_command,
                    "--no-playlist",
                    "--ffmpeg-location",
                    str(Path(ffmpeg_path).parent),
                    "-f",
                    "bv*[height<=480][ext=mp4]+ba[ext=m4a]/b[height<=480][ext=mp4]/bv*[height<=480]+ba/b[height<=480]/best[height<=480]/best",
                    "--merge-output-format",
                    "mp4",
                    "-o",
                    str(output_template),
                    url,
                ],
                "yt-dlp download failed",
            )
            video_path = self._find_downloaded_video(video_id)
            if not video_path:
                raise RuntimeError("yt-dlp finished, but no downloaded video file was found.")

        frame_dir = self.settings.frames_dir / video_id
        frame_dir.mkdir(parents=True, exist_ok=True)
        for old_frame in frame_dir.glob("*.jpg"):
            old_frame.unlink()

        pattern = frame_dir / "frame_%05d.jpg"
        self._run_command(
            [
                ffmpeg_path,
                "-y",
                "-i",
                str(video_path),
                "-vf",
                f"fps=1/{self.settings.frame_interval_seconds}",
                "-q:v",
                "3",
                str(pattern),
            ],
            "ffmpeg frame extraction failed",
        )

        frame_count = 0
        for index, frame_path in enumerate(sorted(frame_dir.glob("*.jpg"))):
            timestamp = index * self.settings.frame_interval_seconds
            self.vector_index.add_frame(video_id, timestamp, frame_path)
            frame_count += 1
        return frame_count

    def _yt_dlp_command(self) -> list[str]:
        project_local = Path(__file__).resolve().parents[2] / "tools" / "yt-dlp.exe"
        if project_local.exists():
            return [str(project_local)]
        path_executable = shutil.which("yt-dlp")
        if path_executable:
            return [path_executable]
        return [sys.executable, "-m", "yt_dlp"]

    def _find_downloaded_video(self, video_id: str) -> Path | None:
        candidates = sorted(self.settings.videos_dir.glob(f"{video_id}.*"))
        complete = [candidate for candidate in candidates if candidate.is_file() and ".part" not in candidate.name]
        mp4_files = [candidate for candidate in complete if candidate.suffix.lower() == ".mp4"]
        if mp4_files:
            return mp4_files[0]
        return complete[0] if complete else None

    def _run_command(self, command: list[str], label: str) -> None:
        try:
            subprocess.run(command, check=True, capture_output=True, text=True)
        except subprocess.CalledProcessError as exc:
            details = "\n".join(part.strip() for part in [exc.stderr, exc.stdout] if part and part.strip())
            if not details:
                details = str(exc)
            raise RuntimeError(f"{label}: {details}") from exc

    def _find_ffmpeg(self) -> str | None:
        local_app_data = Path(os.environ.get("LOCALAPPDATA", ""))
        candidates = [
            Path(__file__).resolve().parents[2] / "tools" / "ffmpeg" / "bin" / "ffmpeg.exe",
        ]
        winget_packages = local_app_data / "Microsoft" / "WinGet" / "Packages"
        if winget_packages.exists():
            candidates.extend(winget_packages.glob("Gyan.FFmpeg_*/*/bin/ffmpeg.exe"))
        for candidate in candidates:
            if candidate.exists() and candidate.stat().st_size > 0:
                return str(candidate)
        return shutil.which("ffmpeg")

    def _update_status(self, video_id: str, status: str) -> None:
        with connect() as connection:
            connection.execute(
                "UPDATE videos SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE video_id = ?",
                (status, video_id),
            )

    def _mark_failed(self, video_id: str, url: str, error: str) -> None:
        with connect() as connection:
            connection.execute(
                """
                INSERT INTO videos(video_id, url, status, error)
                VALUES (?, ?, 'failed', ?)
                ON CONFLICT(video_id) DO UPDATE SET
                    status = 'failed',
                    error = excluded.error,
                    updated_at = CURRENT_TIMESTAMP
                """,
                (video_id, url, error),
            )
