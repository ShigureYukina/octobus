package io.github.shigureyukina.octobus.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.sqlite.SQLiteDataSource;

@Configuration
class PersistenceConfiguration {

    @Bean
    DataSource dataSource(DatabaseProperties databaseProperties) {
        if (databaseProperties.isMysqlLike()) {
            HikariDataSource dataSource = new HikariDataSource();
            dataSource.setJdbcUrl(databaseProperties.url());
            if (databaseProperties.username() != null && !databaseProperties.username().isBlank()) {
                dataSource.setUsername(databaseProperties.username());
            }
            if (databaseProperties.password() != null) {
                dataSource.setPassword(databaseProperties.password());
            }
            return dataSource;
        }

        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl(databaseProperties.url());
        return dataSource;
    }

    @Bean
    FlywayConfigurationCustomizer flywayConfigurationCustomizer(DatabaseProperties databaseProperties) {
        return configuration -> {
            if (databaseProperties.isMysqlLike()) {
                configuration.locations("classpath:db/migration/mysql");
            } else {
                configuration.locations("classpath:db/migration/sqlite");
            }
        };
    }
}
