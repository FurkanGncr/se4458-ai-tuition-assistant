package edu.se4458.tuition.domain;

import java.util.Comparator;

/** Term helpers. A term is "YYYY-SPRING" or "YYYY-FALL"; SPRING comes before FALL within a year. */
public final class Terms {

    /** Orders terms chronologically, e.g. 2026-SPRING, 2026-FALL, 2027-SPRING. */
    public static final Comparator<String> CHRONOLOGICAL =
            Comparator.comparingInt(Terms::year).thenComparingInt(Terms::seasonOrder);

    private Terms() {
    }

    private static int year(String term) {
        return Integer.parseInt(term.substring(0, 4));
    }

    private static int seasonOrder(String term) {
        return term.endsWith("SPRING") ? 0 : 1;
    }
}
