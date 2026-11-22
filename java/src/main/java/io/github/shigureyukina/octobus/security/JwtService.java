package io.github.shigureyukina.octobus.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.shigureyukina.octobus.config.AuthProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Minimal JWT issue/verify service for admin authentication.
 */
@Service
public class JwtService {

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String JWT_TYPE = "JWT";
    private static final String JWT_ALGORITHM = "HS256";
    private static final Duration DEFAULT_TOKEN_TTL = Duration.ofHours(1);
    private static final String TOKEN_VERSION_CLAIM = "ver";

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final SecretKeySpec signingKey;

    @Autowired
    public JwtService(AuthProperties authProperties, ObjectMapper objectMapper) {
        this(authProperties, objectMapper, Clock.systemUTC());
    }

    JwtService(AuthProperties authProperties, ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.signingKey = new SecretKeySpec(authProperties.jwtSecret().getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
    }

    public String issueToken(String subject) {
        return issueToken(subject, 0);
    }

    public String issueToken(String subject, long tokenVersion) {
        return issueToken(subject, tokenVersion, DEFAULT_TOKEN_TTL).token();
    }

    public IssuedJwt issueToken(String subject, long tokenVersion, Duration ttl) {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("JWT subject must not be blank");
        }
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("JWT ttl must be positive");
        }
        if (tokenVersion < 0) {
            throw new IllegalArgumentException("JWT token version must not be negative");
        }

        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(ttl);

        Map<String, Object> header = Map.of("alg", JWT_ALGORITHM, "typ", JWT_TYPE);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", subject);
        payload.put("iat", issuedAt.getEpochSecond());
        payload.put("exp", expiresAt.getEpochSecond());
        payload.put(TOKEN_VERSION_CLAIM, tokenVersion);

        String encodedHeader = encodeJson(header);
        String encodedPayload = encodeJson(payload);
        String signingInput = encodedHeader + "." + encodedPayload;

        return new IssuedJwt(signingInput + "." + sign(signingInput), expiresAt);
    }

    public JwtClaims verify(String token) {
        String[] segments = token.split("\\.", -1);
        if (segments.length != 3) {
            throw new IllegalArgumentException("Invalid JWT format");
        }

        String signingInput = segments[0] + "." + segments[1];
        String expectedSignature = sign(signingInput);
        byte[] expectedSignatureBytes = expectedSignature.getBytes(StandardCharsets.UTF_8);
        byte[] actualSignatureBytes = segments[2].getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedSignatureBytes, actualSignatureBytes)) {
            throw new IllegalArgumentException("Invalid JWT signature");
        }

        Map<String, Object> header = decodeJson(segments[0]);
        if (!JWT_ALGORITHM.equals(header.get("alg")) || !JWT_TYPE.equals(header.get("typ"))) {
            throw new IllegalArgumentException("Unsupported JWT header");
        }

        Map<String, Object> payload = decodeJson(segments[1]);
        String subject = readStringClaim(payload, "sub");
        Instant issuedAt = readInstantClaim(payload, "iat");
        Instant expiresAt = readInstantClaim(payload, "exp");
        long tokenVersion = readLongClaim(payload, TOKEN_VERSION_CLAIM, 0);
        if (!expiresAt.isAfter(clock.instant())) {
            throw new IllegalArgumentException("JWT is expired");
        }

        return new JwtClaims(subject, issuedAt, expiresAt, tokenVersion);
    }

    private String encodeJson(Map<String, Object> body) {
        try {
            return URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(body));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to encode JWT segment", ex);
        }
    }

    private Map<String, Object> decodeJson(String segment) {
        try {
            return objectMapper.readValue(URL_DECODER.decode(segment), MAP_TYPE);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid JWT payload", ex);
        }
    }

    private String sign(String content) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(signingKey);
            byte[] signature = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
            return URL_ENCODER.encodeToString(signature);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign JWT", ex);
        }
    }

    private String readStringClaim(Map<String, Object> payload, String claimName) {
        Object rawValue = payload.get(claimName);
        if (!(rawValue instanceof String value) || value.isBlank()) {
            throw new IllegalArgumentException("Missing JWT claim: " + claimName);
        }
        return value;
    }

    private Instant readInstantClaim(Map<String, Object> payload, String claimName) {
        Object rawValue = payload.get(claimName);
        if (!(rawValue instanceof Number number)) {
            throw new IllegalArgumentException("Missing JWT claim: " + claimName);
        }
        return Instant.ofEpochSecond(number.longValue());
    }

    private long readLongClaim(Map<String, Object> payload, String claimName, long defaultValue) {
        Object rawValue = payload.get(claimName);
        if (rawValue == null) {
            return defaultValue;
        }
        if (!(rawValue instanceof Number number)) {
            throw new IllegalArgumentException("Invalid JWT claim: " + claimName);
        }
        return number.longValue();
    }
}
