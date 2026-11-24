package com.example.budgetapp.processing;

import org.junit.Test;
import static org.junit.Assert.*;

public class MoneyTest {
    @Test public void parsesExactFen() {
        assertEquals(2880L, Money.parseFen("28.80"));
        assertEquals(1L, Money.parseFen("0.01"));
        assertEquals(123456L, Money.parseFen("1,234.56"));
        assertEquals(100L, Money.parseFen("1"));
        assertEquals(120L, Money.parseFen("1.2"));
        assertEquals(Long.MAX_VALUE, Money.parseFen("92233720368547758.07"));
    }
    @Test public void rejectsInvalidOrOverflowingAmounts() {
        for (String input : new String[]{null,""," ","0","0.00","28.800","-1.00","+1","1e3","1,23.45","12,34,567","1,2345",".01","1.","NaN","92233720368547758.08"}) {
            assertThrows("input=" + input, IllegalArgumentException.class, () -> Money.parseFen(input));
        }
    }
    @Test public void formatsSignedTotalsWithoutOverflow() {
        assertEquals("0.00", Money.formatFen(0));
        assertEquals("-2961.20", Money.formatFen(-296120L));
        assertEquals("0.01", Money.formatFen(1));
        assertEquals("92233720368547758.07", Money.formatFen(Long.MAX_VALUE));
        assertEquals("-92233720368547758.08", Money.formatFen(Long.MIN_VALUE));
    }
}
