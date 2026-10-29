package com.pms.propertymanagement.utils;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cryptographic password hashing and constant-time verification utility.
 * Uses PBKDF2WithHmacSHA256 with a 16-byte random salt while supporting
 * constant-time backwards-compatible verification for legacy seed passwords.
 */
public final class PasswordUtil {

    private static final String PREFIX = "{pbkdf2}";
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 10000;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_BYTES = 16;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private PasswordUtil() {
    }

    public static boolean isEncoded(String storedPassword) {
        return storedPassword != null && storedPassword.startsWith(PREFIX);
    }

    public static String encode(String rawPassword) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("Password cannot be null");
        }
        if (isEncoded(rawPassword)) {
            return rawPassword;
        }
        byte[] salt = new byte[SALT_BYTES];
        SECURE_RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(rawPassword.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
        return PREFIX
                + Base64.getEncoder().encodeToString(salt)
                + ":"
                + Base64.getEncoder().encodeToString(hash);
    }

    public static boolean matches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null) {
            return false;
        }
        if (isEncoded(storedPassword)) {
            String payload = storedPassword.substring(PREFIX.length());
            int sep = payload.indexOf(':');
            if (sep <= 0) {
                return false;
            }
            try {
                byte[] salt = Base64.getDecoder().decode(payload.substring(0, sep));
                byte[] expectedHash = Base64.getDecoder().decode(payload.substring(sep + 1));
                byte[] actualHash = pbkdf2(rawPassword.toCharArray(), salt, ITERATIONS, expectedHash.length * 8);
                return MessageDigest.isEqual(expectedHash, actualHash);
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        // Constant-time comparison for legacy plaintext seed passwords
        byte[] rawBytes = rawPassword.getBytes(StandardCharsets.UTF_8);
        byte[] storedBytes = storedPassword.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(rawBytes, storedBytes);
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyLength) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyLength);
            SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGORITHM);
            return skf.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash password with " + ALGORITHM, e);
        }
    }
}
