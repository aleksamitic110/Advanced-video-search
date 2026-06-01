CREATE TABLE IF NOT EXISTS runtime_config (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS videos (
    video_id TEXT PRIMARY KEY,
    url TEXT NOT NULL,
    title TEXT NOT NULL DEFAULT '',
    description TEXT NOT NULL DEFAULT '',
    thumbnail_url TEXT NOT NULL DEFAULT '',
    channel_title TEXT NOT NULL DEFAULT '',
    duration TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'queued',
    error TEXT NOT NULL DEFAULT '',
    comment_count INTEGER NOT NULL DEFAULT 0,
    transcript_count INTEGER NOT NULL DEFAULT 0,
    frame_count INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS text_docs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    video_id TEXT NOT NULL,
    source_type TEXT NOT NULL,
    source_id TEXT NOT NULL DEFAULT '',
    timestamp_seconds INTEGER,
    title TEXT NOT NULL DEFAULT '',
    content TEXT NOT NULL,
    FOREIGN KEY(video_id) REFERENCES videos(video_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_text_docs_video_id ON text_docs(video_id);
CREATE INDEX IF NOT EXISTS idx_text_docs_source_type ON text_docs(source_type);

CREATE TABLE IF NOT EXISTS frames (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    video_id TEXT NOT NULL,
    timestamp_seconds INTEGER NOT NULL,
    frame_path TEXT NOT NULL,
    embedding TEXT NOT NULL,
    FOREIGN KEY(video_id) REFERENCES videos(video_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_frames_video_id ON frames(video_id);
CREATE INDEX IF NOT EXISTS idx_frames_timestamp ON frames(video_id, timestamp_seconds);
