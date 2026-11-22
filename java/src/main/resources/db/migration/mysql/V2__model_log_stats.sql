-- Model pricing table
CREATE TABLE IF NOT EXISTS model (
  name VARCHAR(255) PRIMARY KEY NOT NULL,
  input DOUBLE NOT NULL DEFAULT 0,
  output DOUBLE NOT NULL DEFAULT 0,
  cache_read DOUBLE NOT NULL DEFAULT 0,
  cache_write DOUBLE NOT NULL DEFAULT 0
);

-- Relay log table
CREATE TABLE IF NOT EXISTS relay_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  time BIGINT NOT NULL DEFAULT 0,
  request_model_name VARCHAR(255) NOT NULL DEFAULT '',
  request_api_key_name VARCHAR(255) NOT NULL DEFAULT '',
  channel_id BIGINT NOT NULL DEFAULT 0,
  channel_name VARCHAR(255) NOT NULL DEFAULT '',
  actual_model_name VARCHAR(255) NOT NULL DEFAULT '',
  input_tokens BIGINT NOT NULL DEFAULT 0,
  output_tokens BIGINT NOT NULL DEFAULT 0,
  ftut BIGINT NOT NULL DEFAULT 0,
  use_time BIGINT NOT NULL DEFAULT 0,
  cost DOUBLE NOT NULL DEFAULT 0,
  usage_recorded BIGINT NOT NULL DEFAULT 0,
  request_content LONGTEXT,
  response_content LONGTEXT,
  error TEXT,
  attempts TEXT,
  total_attempts INT NOT NULL DEFAULT 0,
  cache_hit_tokens BIGINT NOT NULL DEFAULT 0
);

-- Stats total table
CREATE TABLE IF NOT EXISTS stats_total (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  input_token BIGINT NOT NULL DEFAULT 0,
  output_token BIGINT NOT NULL DEFAULT 0,
  input_cost DOUBLE NOT NULL DEFAULT 0,
  output_cost DOUBLE NOT NULL DEFAULT 0,
  wait_time BIGINT NOT NULL DEFAULT 0,
  request_success BIGINT NOT NULL DEFAULT 0,
  request_failed BIGINT NOT NULL DEFAULT 0,
  cache_hit_token BIGINT NOT NULL DEFAULT 0
);

-- Stats daily table
CREATE TABLE IF NOT EXISTS stats_daily (
  `date` VARCHAR(255) PRIMARY KEY,
  input_token BIGINT NOT NULL DEFAULT 0,
  output_token BIGINT NOT NULL DEFAULT 0,
  input_cost DOUBLE NOT NULL DEFAULT 0,
  output_cost DOUBLE NOT NULL DEFAULT 0,
  wait_time BIGINT NOT NULL DEFAULT 0,
  request_success BIGINT NOT NULL DEFAULT 0,
  request_failed BIGINT NOT NULL DEFAULT 0,
  cache_hit_token BIGINT NOT NULL DEFAULT 0
);

-- Stats hourly table
CREATE TABLE IF NOT EXISTS stats_hourly (
  `hour` INT NOT NULL,
  `date` VARCHAR(255) NOT NULL,
  input_token BIGINT NOT NULL DEFAULT 0,
  output_token BIGINT NOT NULL DEFAULT 0,
  input_cost DOUBLE NOT NULL DEFAULT 0,
  output_cost DOUBLE NOT NULL DEFAULT 0,
  wait_time BIGINT NOT NULL DEFAULT 0,
  request_success BIGINT NOT NULL DEFAULT 0,
  request_failed BIGINT NOT NULL DEFAULT 0,
  cache_hit_token BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`hour`, `date`)
);
