package io.github.shigureyukina.octobus.domain;

import io.github.shigureyukina.octobus.common.ApiException;
import io.github.shigureyukina.octobus.persistence.UserRepository;
import io.github.shigureyukina.octobus.security.IssuedJwt;
import io.github.shigureyukina.octobus.security.JwtService;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService implements AdminSessionValidator {

    private static final String DEFAULT_USERNAME = "admin";
    private static final String DEFAULT_PASSWORD = "admin";
    private static final Duration DEFAULT_LOGIN_TTL = Duration.ofMinutes(15);
    private static final Duration REMEMBER_ME_TTL = Duration.ofDays(30);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public void initializeDefaultUser() {
        if (userRepository.findFirst().isPresent()) {
            return;
        }

        userRepository.create(DEFAULT_USERNAME, passwordEncoder.encode(DEFAULT_PASSWORD));
    }

    public UserLoginResponse login(UserLoginRequest request) {
        UserAccount user = userRepository.findByUsername(request.username())
            .orElseThrow(this::invalidCredentials);
        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw invalidCredentials();
        }

        IssuedJwt issuedJwt = jwtService.issueToken(user.username(), user.tokenVersion(), resolveLoginTtl(request.expire()));
        return new UserLoginResponse(issuedJwt.token(), issuedJwt.expiresAt().toString());
    }

    public String logout(String username) {
        UserAccount user = requireUser(username);
        userRepository.incrementTokenVersion(user.id());
        return "logout successful";
    }

    public String changePassword(String username, ChangePasswordRequest request) {
        UserAccount user = requireUser(username);
        if (!passwordEncoder.matches(request.oldPassword(), user.passwordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Incorrect old password");
        }

        userRepository.updatePassword(user.id(), passwordEncoder.encode(request.newPassword()));
        userRepository.incrementTokenVersion(user.id());
        return "password changed successfully";
    }

    public String changeUsername(String username, ChangeUsernameRequest request) {
        UserAccount user = requireUser(username);
        String newUsername = request.newUsername().trim();
        if (newUsername.equals(user.username())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "New username must be different");
        }
        if (userRepository.findByUsername(newUsername).isPresent()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Username already exists");
        }

        userRepository.updateUsername(user.id(), newUsername);
        return "username changed successfully";
    }

    @Override
    public void validate(String username, long tokenVersion) {
        UserAccount user = requireUser(username);
        if (user.tokenVersion() != tokenVersion) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Session has expired");
        }
    }

    private UserAccount requireUser(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized"));
    }

    private ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }

    private Duration resolveLoginTtl(Integer expire) {
        if (expire == null || expire == 0) {
            return DEFAULT_LOGIN_TTL;
        }
        if (expire == -1) {
            return REMEMBER_ME_TTL;
        }
        if (expire < -1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid expire value");
        }
        return Duration.ofMinutes(expire.longValue());
    }
}
