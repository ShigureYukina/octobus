CREATE TABLE IF NOT EXISTS groups (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE,
    mode INTEGER NOT NULL,
    match_regex TEXT,
    first_token_time_out INTEGER NOT NULL DEFAULT 0,
    session_keep_time INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS group_items (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    group_id INTEGER NOT NULL,
    channel_id INTEGER NOT NULL,
    model_name TEXT NOT NULL,
    priority INTEGER NOT NULL DEFAULT 0,
    weight INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE,
    UNIQUE(group_id, channel_id, model_name)
);
