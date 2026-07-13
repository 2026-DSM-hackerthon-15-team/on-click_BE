package com.onclick.global.security;

import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUserIdResolverTest {

    private final JwtUserIdResolver resolver = new JwtUserIdResolver();

    @Test
    void resolvesPositiveNumericSubject() {
        assertThat(resolver.resolve(jwtWithSubject("42"))).isEqualTo(42L);
    }

    @Test
    void rejectsMissingMalformedAndNonPositiveSubject() {
        assertUnauthorized(jwtWithoutSubject());
        assertUnauthorized(jwtWithSubject("user"));
        assertUnauthorized(jwtWithSubject("0"));
        assertUnauthorized(jwtWithSubject("-1"));
    }

    private void assertUnauthorized(Jwt jwt) {
        assertThatThrownBy(() -> resolver.resolve(jwt))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    private Jwt jwtWithSubject(String subject) {
        return Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(subject)
                .build();
    }

    private Jwt jwtWithoutSubject() {
        return Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .claim("scope", "none")
                .build();
    }
}
