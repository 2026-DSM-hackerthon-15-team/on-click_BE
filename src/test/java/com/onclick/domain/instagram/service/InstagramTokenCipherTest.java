package com.onclick.domain.instagram.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import com.onclick.global.config.properties.InstagramProperties;

import org.junit.jupiter.api.Test;

class InstagramTokenCipherTest {

    private static final String KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @Test
    void encryptsWithRandomNonceAndDecryptsAccessToken() {
        InstagramTokenCipher cipher = new InstagramTokenCipher(properties(KEY));

        String first = cipher.encrypt("secret-access-token");
        String second = cipher.encrypt("secret-access-token");

        assertThat(first).isNotEqualTo("secret-access-token").isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo("secret-access-token");
        assertThat(cipher.decrypt(second)).isEqualTo("secret-access-token");
    }

    @Test
    void rejectsInvalidKeyLengthAndTamperedCiphertext() {
        assertThatThrownBy(() -> new InstagramTokenCipher(properties("short")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");

        InstagramTokenCipher cipher = new InstagramTokenCipher(properties(KEY));
        String encrypted = cipher.encrypt("secret");
        String tampered = encrypted.substring(0, encrypted.length() - 2) + "aa";
        assertThatThrownBy(() -> cipher.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void allowsEphemeralKeyOnlyForMockProvider() {
        InstagramTokenCipher mockCipher = new InstagramTokenCipher(properties(""));
        assertThat(mockCipher.decrypt(mockCipher.encrypt("mock-token"))).isEqualTo("mock-token");

        InstagramProperties http = new InstagramProperties(
                "http",
                "client",
                "secret",
                "http://localhost/callback",
                "http://localhost/frontend",
                "http://localhost/authorize",
                "http://localhost/token",
                "http://localhost/graph",
                "scope",
                "",
                Duration.ofMinutes(10),
                Duration.ofSeconds(1),
                Duration.ofSeconds(1)
        );
        assertThatThrownBy(() -> new InstagramTokenCipher(http))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("required");
    }

    private InstagramProperties properties(String key) {
        return new InstagramProperties(
                "mock",
                "client",
                "secret",
                "http://localhost/callback",
                "http://localhost/frontend",
                "http://localhost/authorize",
                "http://localhost/token",
                "http://localhost/graph",
                "scope",
                key,
                Duration.ofMinutes(10),
                Duration.ofSeconds(1),
                Duration.ofSeconds(1)
        );
    }
}
