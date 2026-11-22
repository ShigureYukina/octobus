-- Stats per API key table
CREATE TABLE IF NOT EXISTS stats_apikey (
  api_key_id BIGINT PRIMARY KEY,
  input_token BIGINT NOT NULL DEFAULT 0,
  output_token BIGINT NOT NULL DEFAULT 0,
  input_cost DOUBLE NOT NULL DEFAULT 0,
  output_cost DOUBLE NOT NULL DEFAULT 0,
  wait_time BIGINT NOT NULL DEFAULT 0,
  request_success BIGINT NOT NULL DEFAULT 0,
  request_failed BIGINT NOT NULL DEFAULT 0,
  cache_hit_token BIGINT NOT NULL DEFAULT 0
);

-- Stats per channel table
CREATE TABLE IF NOT EXISTS stats_channel (
  channel_id BIGINT PRIMARY KEY,
  input_token BIGINT NOT NULL DEFAULT 0,
  output_token BIGINT NOT NULL DEFAULT 0,
  input_cost DOUBLE NOT NULL DEFAULT 0,
  output_cost DOUBLE NOT NULL DEFAULT 0,
  wait_time BIGINT NOT NULL DEFAULT 0,
  request_success BIGINT NOT NULL DEFAULT 0,
  request_failed BIGINT NOT NULL DEFAULT 0,
  cache_hit_token BIGINT NOT NULL DEFAULT 0
);
