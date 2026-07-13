package com.onclick.global.security;

public record IssuedAccessToken(String value, long expiresInSeconds) {
}
