CREATE TABLE IF NOT EXISTS `groups` (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(255) NOT NULL UNIQUE,
  mode INT NOT NULL,
  match_regex TEXT,
  first_token_time_out BIGINT NOT NULL DEFAULT 0,
  session_keep_time BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS group_items (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  group_id BIGINT NOT NULL,
  channel_id BIGINT NOT NULL,
  model_name VARCHAR(255) NOT NULL,
  priority INT NOT NULL DEFAULT 0,
  weight INT NOT NULL DEFAULT 0,
  FOREIGN KEY (group_id) REFERENCES `groups`(id) ON DELETE CASCADE,
  UNIQUE(group_id, channel_id, model_name)
);
