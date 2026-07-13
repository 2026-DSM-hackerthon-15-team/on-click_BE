package com.onclick.global.security;

import com.onclick.global.error.ApiException;
import com.onclick.global.error.ErrorCode;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class JwtUserIdResolver {

    public long resolve(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw unauthorized();
        }

        try {
            long userId = Long.parseLong(jwt.getSubject());
            if (userId <= 0) {
                throw unauthorized();
            }
            return userId;
        } catch (NumberFormatException exception) {
            throw unauthorized();
        }
    }

    private ApiException unauthorized() {
        return new ApiException(ErrorCode.UNAUTHORIZED);
    }
}
