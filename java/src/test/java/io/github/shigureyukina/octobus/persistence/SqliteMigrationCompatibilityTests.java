package io.github.shigureyukina.octobus.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SqliteMigrationCompatibilityTests {

    @Test
    void sqliteV2MigrationMatchesLegacyChecksumSensitiveContent() throws IOException {
        assertThat(readResource("db/migration/sqlite/V2__model_log_stats.sql")).isEqualTo(
            """
            -- Model pricing table
            CREATE TABLE IF NOT EXISTS model (
                name TEXT PRIMARY KEY NOT NULL,
                input REAL NOT NULL DEFAULT 0,
                output REAL NOT NULL DEFAULT 0,
                cache_read REAL NOT NULL DEFAULT 0,
                cache_write REAL NOT NULL DEFAULT 0
            );

            -- Relay log table
            CREATE TABLE IF NOT EXISTS relay_log (
                id INTEGER PRIMARY KEY,
                time INTEGER NOT NULL DEFAULT 0,
                request_model_name TEXT NOT NULL DEFAULT '',
                request_api_key_name TEXT NOT NULL DEFAULT '',
                channel_id INTEGER NOT NULL DEFAULT 0,
                channel_name TEXT NOT NULL DEFAULT '',
                actual_model_name TEXT NOT NULL DEFAULT '',
                input_tokens INTEGER NOT NULL DEFAULT 0,
                output_tokens INTEGER NOT NULL DEFAULT 0,
                ftut INTEGER NOT NULL DEFAULT 0,
                use_time INTEGER NOT NULL DEFAULT 0,
                cost REAL NOT NULL DEFAULT 0,
                usage_recorded INTEGER NOT NULL DEFAULT 0,
                request_content TEXT NOT NULL DEFAULT '',
                response_content TEXT NOT NULL DEFAULT '',
                error TEXT NOT NULL DEFAULT '',
                attempts TEXT NOT NULL DEFAULT '[]',
                total_attempts INTEGER NOT NULL DEFAULT 0,
                cache_hit_tokens INTEGER NOT NULL DEFAULT 0
            );

            -- Stats total table
            CREATE TABLE IF NOT EXISTS stats_total (
                id INTEGER PRIMARY KEY,
                input_token BIGINT NOT NULL DEFAULT 0,
                output_token BIGINT NOT NULL DEFAULT 0,
                input_cost REAL NOT NULL DEFAULT 0,
                output_cost REAL NOT NULL DEFAULT 0,
                wait_time BIGINT NOT NULL DEFAULT 0,
                request_success BIGINT NOT NULL DEFAULT 0,
                request_failed BIGINT NOT NULL DEFAULT 0,
                cache_hit_token BIGINT NOT NULL DEFAULT 0
            );

            -- Stats daily table
            CREATE TABLE IF NOT EXISTS stats_daily (
                date TEXT PRIMARY KEY,
                input_token BIGINT NOT NULL DEFAULT 0,
                output_token BIGINT NOT NULL DEFAULT 0,
                input_cost REAL NOT NULL DEFAULT 0,
                output_cost REAL NOT NULL DEFAULT 0,
                wait_time BIGINT NOT NULL DEFAULT 0,
                request_success BIGINT NOT NULL DEFAULT 0,
                request_failed BIGINT NOT NULL DEFAULT 0,
                cache_hit_token BIGINT NOT NULL DEFAULT 0
            );

            -- Stats hourly table
            CREATE TABLE IF NOT EXISTS stats_hourly (
                hour INTEGER NOT NULL,
                date TEXT NOT NULL,
                input_token BIGINT NOT NULL DEFAULT 0,
                output_token BIGINT NOT NULL DEFAULT 0,
                input_cost REAL NOT NULL DEFAULT 0,
                output_cost REAL NOT NULL DEFAULT 0,
                wait_time BIGINT NOT NULL DEFAULT 0,
                request_success BIGINT NOT NULL DEFAULT 0,
                request_failed BIGINT NOT NULL DEFAULT 0,
                cache_hit_token BIGINT NOT NULL DEFAULT 0,
                PRIMARY KEY (hour, date)
            );
            """
        );
    }

    @Test
    void sqliteV6MigrationMatchesLegacyChecksumSensitiveContent() throws IOException {
        assertThat(readResource("db/migration/sqlite/V6__setting.sql")).isEqualTo(
            """
            CREATE TABLE IF NOT EXISTS setting (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL
            );
            """
        );
    }

    private String readResource(String path) throws IOException {
        try (InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            assertThat(inputStream).as("resource %s should exist", path).isNotNull();
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
