package com.onclick.global.security;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityConfigTest {

    private static final String HS256_SECRET = "test-secret-test-secret-test-secret-1234";

    private final SecurityConfig configuration = new SecurityConfig();

    @Test
    void issuesOneHourHs256TokenContainingOnlyUserIdentity() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        JwtTokenProvider provider = new JwtTokenProvider(
                configuration.jwtEncoder(HS256_SECRET),
                Clock.fixed(now, ZoneOffset.UTC)
        );

        IssuedAccessToken issuedToken = provider.issue(27L);
        Jwt decoded = configuration.jwtDecoder(HS256_SECRET).decode(issuedToken.value());

        assertThat(decoded.getSubject()).isEqualTo("27");
        assertThat(decoded.getIssuer()).hasToString(JwtTokenProvider.ISSUER);
        assertThat(decoded.getIssuedAt()).isEqualTo(now);
        assertThat(decoded.getExpiresAt()).isEqualTo(now.plusSeconds(3600));
        assertThat(decoded.hasClaim("storeId")).isFalse();
        assertThat(issuedToken.expiresInSeconds()).isEqualTo(3600);
    }

    @Test
    void rejectsSecretShorterThanHs256Requirement() {
        assertThatThrownBy(() -> configuration.jwtDecoder("too-short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 UTF-8 bytes");
        assertThatThrownBy(() -> configuration.jwtEncoder("too-short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 UTF-8 bytes");
    }

    @Test
    void passwordEncoderUsesNonPlaintextHash() {
        String encoded = configuration.passwordEncoder().encode("password123");

        assertThat(encoded).isNotEqualTo("password123");
        assertThat(configuration.passwordEncoder().matches("password123", encoded)).isTrue();
    }
}
