package com.semanticbase.cache.redis;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class CacheKeys {

    private static final String PREFIX = "sb:cache";

    private CacheKeys() {}

    static String normalize(String query) {
        if (query == null) return "";
        return query.toLowerCase().replaceAll("\\s+", " ").trim();
    }

    static String exactKey(String tenantId, String query) {
        return PREFIX + ":exact:" + tenantId + ":" + sha256(normalize(query));
    }

    static String semanticListKey(String tenantId) {
        return PREFIX + ":semantic:" + tenantId;
    }

    static String tenantPattern(String tenantId) {
        return PREFIX + ":*:" + tenantId + "*";
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
