package com.onclick.domain.instagram.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.onclick.global.config.properties.InstagramProperties;

import org.springframework.stereotype.Component;

@Component
public class InstagramTokenCipher {

    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKey key;
    private final SecureRandom secureRandom = new SecureRandom();

    public InstagramTokenCipher(InstagramProperties properties) {
        byte[] keyBytes = decodeKey(properties);
        if (keyBytes.length != 32) {
            throw new IllegalStateException("app.instagram.token-encryption-key must decode to 32 bytes");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plainText) {
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array()
            );
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to encrypt Instagram access token", exception);
        }
    }

    public String decrypt(String encoded) {
        try {
            byte[] value = Base64.getDecoder().decode(encoded);
            if (value.length <= NONCE_LENGTH) {
                throw new IllegalArgumentException("Encrypted token is invalid");
            }
            ByteBuffer buffer = ByteBuffer.wrap(value);
            byte[] nonce = new byte[NONCE_LENGTH];
            buffer.get(nonce);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Unable to decrypt Instagram access token", exception);
        }
    }

    private byte[] decodeKey(InstagramProperties properties) {
        String configured = properties.tokenEncryptionKey();
        if (configured == null || configured.isBlank()) {
            if ("mock".equalsIgnoreCase(properties.provider())) {
                byte[] ephemeralKey = new byte[32];
                secureRandom.nextBytes(ephemeralKey);
                return ephemeralKey;
            }
            throw new IllegalStateException(
                    "app.instagram.token-encryption-key is required when the Instagram HTTP provider is enabled"
            );
        }
        try {
            return Base64.getDecoder().decode(configured.trim());
        } catch (IllegalArgumentException ignored) {
            return configured.getBytes(StandardCharsets.UTF_8);
        }
    }
}
