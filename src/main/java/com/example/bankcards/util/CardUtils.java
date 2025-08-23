package com.example.bankcards.util;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

public final class CardUtils {
    private static final Pattern PAN_16 = Pattern.compile("^\\d{16}$");
    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyy-MM");

    private CardUtils(){}

    public static String normalizePan(String pan) {
        if (pan == null) return null;
        return pan.replaceAll("\\s+", "");
    }

    public static void validatePan16(String pan) {
        if (pan == null || !PAN_16.matcher(pan).matches()) {
            throw new IllegalArgumentException("PAN must be exactly 16 digits");
        }
    }

    public static String last4(String pan) {
        return pan.substring(pan.length() - 4);
    }

    public static YearMonth parseExpiry(String ym) {
        return YearMonth.parse(ym, YM);
    }
}
