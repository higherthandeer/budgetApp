package com.example.budgetapp.processing;

import java.math.BigDecimal;
import java.util.regex.Pattern;

public final class Money {
    private static final Pattern DECIMAL = Pattern.compile("(?:[0-9]+|[1-9][0-9]{0,2}(?:,[0-9]{3})+)(?:\\.[0-9]{1,2})?");
    private Money() {}
    public static long parseFen(String text) {
        if (text == null || !DECIMAL.matcher(text).matches()) {
            throw new IllegalArgumentException("Invalid decimal amount");
        }
        try {
            long fen = new BigDecimal(text.replace(",", "")).movePointRight(2).longValueExact();
            if (fen <= 0) throw new IllegalArgumentException("Amount must be positive");
            return fen;
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Amount exceeds integer fen range", e);
        }
    }
    public static String formatFen(long fen) {
        return BigDecimal.valueOf(fen, 2).toPlainString();
    }
}
