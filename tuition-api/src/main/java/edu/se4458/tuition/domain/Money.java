package edu.se4458.tuition.domain;

import edu.se4458.tuition.error.InvalidInputException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Money rules: 2 decimal places, HALF_UP rounding, fits numeric(12,2). */
public final class Money {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);
    public static final BigDecimal MAX = new BigDecimal("9999999999.99");

    private Money() {
    }

    /**
     * Rounds to 2 decimals (HALF_UP) and checks that the result is greater than 0 and fits the column.
     *
     * @throws InvalidInputException if the value is missing, not positive after rounding, or too large
     */
    public static BigDecimal positive(BigDecimal value, String field) {
        if (value == null) {
            throw new InvalidInputException(field + " is required");
        }
        BigDecimal rounded = value.setScale(SCALE, ROUNDING);
        if (rounded.signum() <= 0) {
            throw new InvalidInputException(field + " must be greater than 0 (after rounding to 2 decimals)");
        }
        if (rounded.compareTo(MAX) > 0) {
            throw new InvalidInputException(field + " must not exceed " + MAX.toPlainString());
        }
        return rounded;
    }
}
