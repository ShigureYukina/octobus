CREATE TABLE IF NOT EXISTS channel (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  type VARCHAR(255) NOT NULL DEFAULT '',
  enabled INT NOT NULL DEFAULT 1,
  base_urls TEXT,
  model VARCHAR(255) NOT NULL DEFAULT '',
  custom_model TEXT,
  proxy INT NOT NULL DEFAULT 0,
  auto_sync INT NOT NULL DEFAULT 0,
  auto_group INT NOT NULL DEFAULT 0,
  custom_header TEXT,
  param_override TEXT,
  channel_proxy TEXT,
  match_regex TEXT
);

CREATE TABLE IF NOT EXISTS channel_key (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  channel_id BIGINT NOT NULL,
  enabled INT NOT NULL DEFAULT 1,
  channel_key VARCHAR(255) NOT NULL DEFAULT '',
  status_code INT NOT NULL DEFAULT 0,
  last_use_time_stamp BIGINT NOT NULL DEFAULT 0,
  total_cost DOUBLE NOT NULL DEFAULT 0,
  remark VARCHAR(255) NOT NULL DEFAULT '',
  FOREIGN KEY (channel_id) REFERENCES channel(id)
);
