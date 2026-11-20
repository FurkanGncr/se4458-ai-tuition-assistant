package edu.se4458.tuition.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.se4458.tuition.error.InvalidInputException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvTuitionParserTest {

    @Test
    void parsesRows_withFileLineNumbers_skippingBlankLines() throws Exception {
        String csv = "﻿StudentNo, Term ,AMOUNT,currency\n"
                + "20230010,2026-FALL,5000.00,USD\n"
                + "\n"
                + " 20230011 , 2027-SPRING , 4200 , EUR \r\n";

        List<CsvTuitionParser.Line> lines = CsvTuitionParser.parse(stream(csv));

        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).lineNumber()).isEqualTo(2);
        assertThat(lines.get(0).fields()).containsExactly("20230010", "2026-FALL", "5000.00", "USD");
        assertThat(lines.get(1).lineNumber()).isEqualTo(4);
        assertThat(lines.get(1).fields()).containsExactly("20230011", "2027-SPRING", "4200", "EUR");
    }

    @Test
    void wrongHeader_isRejected() {
        assertThatThrownBy(() -> CsvTuitionParser.parse(stream("student,term,amount\n1,2026-FALL,10\n")))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("header");
    }

    @Test
    void emptyFile_isRejected() {
        assertThatThrownBy(() -> CsvTuitionParser.parse(stream("\n\n")))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void headerOnly_isRejected() {
        assertThatThrownBy(() -> CsvTuitionParser.parse(stream("studentNo,term,amount,currency\n")))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("no data rows");
    }

    private static InputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
