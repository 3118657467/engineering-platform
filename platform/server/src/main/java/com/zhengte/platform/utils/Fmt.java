package com.zhengte.platform.utils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

public final class Fmt {

    public static final DateTimeFormatter MM_DD_HH_MM = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    public static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private Fmt() {
    }

    public static String time(LocalDateTime value) {
        return value == null ? null : MM_DD_HH_MM.format(value);
    }

    public static String full(LocalDateTime value) {
        return value == null ? null : FULL.format(value);
    }

    public static String day(LocalDateTime value) {
        return DAY.format(value);
    }

    public static String duration(Integer millis) {
        if (millis == null) {
            return null;
        }
        return String.format("%.1fs", millis / 1000.0);
    }

    public static String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
