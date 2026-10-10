package com.invoicespot.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

class CsvFileTest {

    private static String text(CsvFile file) {
        return new String(file.content(), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("writes a UTF-8 header and quotes only the cells that need it")
    void writesRows() {
        CsvFile file = CsvFile.of(
                "clients.csv",
                List.of("Client", "Owed", "Since"),
                List.of(
                        List.of("Café, \"Le Monde\"", new BigDecimal("1E+3").setScale(2), LocalDate.of(2026, 9, 1)),
                        Arrays.asList("Two\nlines", null, "")));

        assertThat(text(file)).isEqualTo("﻿Client,Owed,Since\r\n"
                + "\"Café, \"\"Le Monde\"\"\",1000.00,2026-09-01\r\n"
                + "\"Two\nlines\",,\r\n");
    }

    @Test
    @DisplayName("text a spreadsheet would run as a formula starts with an apostrophe; numbers are left alone")
    void guardsFormulas() {
        CsvFile file = CsvFile.of(
                "x.csv",
                List.of("A", "B", "C", "D", "E", "F"),
                List.of(List.of("=SUM(A1:A9)", "+1", "-1", "@cmd", "\tx", new BigDecimal("-5.00"))));

        assertThat(text(file)).endsWith("'=SUM(A1:A9),'+1,'-1,'@cmd,'\tx,-5.00\r\n");
    }

    @Test
    @DisplayName("text that starts with apostrophes ahead of a formula character gets one more, so it reads back")
    void guardsApostrophes() {
        CsvFile file = CsvFile.of("x.csv", List.of("A", "B", "C"), List.of(List.of("'+Acme", "''=x", "'90s")));

        assertThat(text(file)).endsWith("''+Acme,'''=x,'90s\r\n");
    }

    @Test
    @DisplayName("downloads as an attachment that is never cached")
    void download() {
        ResponseEntity<byte[]> response = CsvFile.of("report.csv", List.of("A"), List.of()).download();

        assertThat(response.getHeaders().getContentType()).hasToString("text/csv;charset=UTF-8");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"report.csv\"");
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
    }
}
