package edu.se4458.tuition.dto;

/** Input formats shared by request validation and the services. */
public final class Patterns {

    public static final String STUDENT_NO = "^[A-Za-z0-9]{1,20}$";
    public static final String STUDENT_NO_MESSAGE = "must be 1-20 letters or digits";

    public static final String TERM = "^[0-9]{4}-(FALL|SPRING)$";
    public static final String TERM_MESSAGE = "must be YYYY-FALL or YYYY-SPRING, e.g. 2026-FALL";

    public static final String CURRENCY = "^[A-Z]{3}$";
    public static final String CURRENCY_MESSAGE = "must be a 3-letter ISO 4217 code, e.g. USD";

    public static final int TRANSACTION_ID_MAX = 64;

    private Patterns() {
    }
}
