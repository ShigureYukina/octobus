CREATE TABLE IF NOT EXISTS channel (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    type TEXT NOT NULL DEFAULT '',
    enabled INTEGER NOT NULL DEFAULT 1,
    base_urls TEXT NOT NULL DEFAULT '[]',
    model TEXT NOT NULL DEFAULT '',
    custom_model TEXT NOT NULL DEFAULT '',
    proxy INTEGER NOT NULL DEFAULT 0,
    auto_sync INTEGER NOT NULL DEFAULT 0,
    auto_group INTEGER NOT NULL DEFAULT 0,
    custom_header TEXT NOT NULL DEFAULT '[]',
    param_override TEXT,
    channel_proxy TEXT,
    match_regex TEXT
);

CREATE TABLE IF NOT EXISTS channel_key (
    id INTEGER PRIMARY KEY,
    channel_id INTEGER NOT NULL,
    enabled INTEGER NOT NULL DEFAULT 1,
    channel_key TEXT NOT NULL DEFAULT '',
    status_code INTEGER NOT NULL DEFAULT 0,
    last_use_time_stamp INTEGER NOT NULL DEFAULT 0,
    total_cost REAL NOT NULL DEFAULT 0,
    remark TEXT NOT NULL DEFAULT '',
    FOREIGN KEY (channel_id) REFERENCES channel(id)
);
