package io.github.shigureyukina.octobus.persistence;

import io.github.shigureyukina.octobus.domain.UserAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UserAccount> findFirst() {
        return queryOne("SELECT id, username, password, token_version FROM app_user ORDER BY id LIMIT 1");
    }

    public Optional<UserAccount> findByUsername(String username) {
        return queryOne(
            "SELECT id, username, password, token_version FROM app_user WHERE username = ? LIMIT 1",
            username
        );
    }

    public void create(String username, String passwordHash) {
        jdbcTemplate.update(
            "INSERT INTO app_user (username, password, token_version) VALUES (?, ?, 0)",
            username,
            passwordHash
        );
    }

    public void updatePassword(long id, String passwordHash) {
        jdbcTemplate.update(
            "UPDATE app_user SET password = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
            passwordHash,
            id
        );
    }

    public void updateUsername(long id, String username) {
        jdbcTemplate.update(
            "UPDATE app_user SET username = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
            username,
            id
        );
    }

    public void incrementTokenVersion(long id) {
        jdbcTemplate.update(
            "UPDATE app_user SET token_version = token_version + 1, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
            id
        );
    }

    private Optional<UserAccount> queryOne(String sql, Object... args) {
        List<UserAccount> users = jdbcTemplate.query(
            sql,
            (rs, rowNum) -> new UserAccount(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("password"),
                rs.getLong("token_version")
            ),
            args
        );

        return users.stream().findFirst();
    }
}
