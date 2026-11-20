package edu.se4458.tuition.service;

import edu.se4458.tuition.error.InvalidInputException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Parses the batch CSV. Format: a header line {@code studentNo,term,amount,currency} followed by one record
 * per line. Values are trimmed; blank lines are skipped; quoting is not supported (no field needs commas).
 */
public final class CsvTuitionParser {

    static final List<String> HEADER = List.of("studentno", "term", "amount", "currency");

    /** A data line. {@code lineNumber} counts every line of the file, starting at 1 for the header. */
    public record Line(int lineNumber, List<String> fields) {
    }

    private CsvTuitionParser() {
    }

    /**
     * @throws InvalidInputException if the header is missing or wrong, or there are no data lines
     */
    public static List<Line> parse(InputStream input) throws IOException {
        List<Line> lines = new ArrayList<>();
        boolean headerSeen = false;
        int lineNumber = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String raw;
            while ((raw = reader.readLine()) != null) {
                lineNumber++;
                if (lineNumber == 1 && raw.startsWith("﻿")) {
                    raw = raw.substring(1); // UTF-8 byte order mark written by some spreadsheet tools
                }
                if (raw.isBlank()) {
                    continue;
                }
                List<String> fields = Arrays.stream(raw.split(",", -1)).map(String::trim).toList();
                if (!headerSeen) {
                    List<String> header = fields.stream().map(f -> f.toLowerCase(Locale.ROOT)).toList();
                    if (!header.equals(HEADER)) {
                        throw new InvalidInputException("invalid-csv",
                                "CSV header must be: studentNo,term,amount,currency");
                    }
                    headerSeen = true;
                    continue;
                }
                lines.add(new Line(lineNumber, fields));
            }
        }
        if (!headerSeen) {
            throw new InvalidInputException("invalid-csv", "CSV file is empty");
        }
        if (lines.isEmpty()) {
            throw new InvalidInputException("invalid-csv", "CSV file has no data rows");
        }
        return lines;
    }
}
