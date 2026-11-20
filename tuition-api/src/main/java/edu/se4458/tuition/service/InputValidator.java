package edu.se4458.tuition.service;

import edu.se4458.tuition.dto.Patterns;
import edu.se4458.tuition.error.InvalidInputException;
import java.util.Currency;

/**
 * Validates inputs inside the services. Request DTOs are also validated by Bean Validation, but the CSV batch
 * path has no DTO validation, so the services check everything again.
 */
final class InputValidator {

    private InputValidator() {
    }

    static String studentNo(String value) {
        if (value == null || !value.matches(Patterns.STUDENT_NO)) {
            throw new InvalidInputException("studentNo " + Patterns.STUDENT_NO_MESSAGE);
        }
        return value;
    }

    static String term(String value) {
        if (value == null || !value.matches(Patterns.TERM)) {
            throw new InvalidInputException("term " + Patterns.TERM_MESSAGE);
        }
        return value;
    }

    static String currency(String value) {
        if (value == null || !value.matches(Patterns.CURRENCY)) {
            throw new InvalidInputException("currency " + Patterns.CURRENCY_MESSAGE);
        }
        try {
            Currency.getInstance(value);
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("currency '" + value + "' is not a known ISO 4217 code");
        }
        return value;
    }

    static String transactionId(String value) {
        if (value == null || value.isBlank() || value.length() > Patterns.TRANSACTION_ID_MAX) {
            throw new InvalidInputException(
                    "transactionId is required and must be at most " + Patterns.TRANSACTION_ID_MAX + " characters");
        }
        return value;
    }
}
