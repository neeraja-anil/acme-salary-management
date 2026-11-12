package com.acme.salary.bulkimport;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * Reads the uploaded bytes into rows keyed by column name. UTF-8 with or without a byte-order mark,
 * RFC 4180 quoting (so "Smith, Jr" in quotes stays one field). Header names are matched ignoring case
 * and surrounding spaces; unknown extra columns are ignored. Completely blank lines are skipped.
 */
final class CsvFileReader {

    record Row(int row, Map<String, String> values) {
        /** The trimmed value, or "" when the column is missing or blank. */
        String get(String column) {
            String value = values.get(column);
            return value == null ? "" : value.trim();
        }
    }

    private CsvFileReader() {
    }

    static List<Row> read(byte[] bytes) {
        String text = decode(bytes);
        if (text.isBlank()) {
            throw new ImportExceptions.InvalidFile("The file is empty");
        }
        CSVFormat format = CSVFormat.RFC4180.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(false) // so a record's number stays its line number; blank rows are skipped below
                .setAllowMissingColumnNames(true)
                .get();
        try (CSVParser parser = CSVParser.parse(new StringReader(text), format)) {
            Map<String, Integer> columns = columns(parser.getHeaderNames());
            List<Row> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                if (isBlank(record)) {
                    continue;
                }
                Map<String, String> values = new HashMap<>();
                columns.forEach((name, index) -> values.put(name, index < record.size() ? record.get(index) : ""));
                rows.add(new Row((int) record.getRecordNumber() + 1, values));
                if (rows.size() > ImportFormat.MAX_ROWS) {
                    throw new ImportExceptions.FileTooLarge(
                            "The file has more than " + ImportFormat.MAX_ROWS + " rows. Split it into smaller files.");
                }
            }
            if (rows.isEmpty()) {
                throw new ImportExceptions.InvalidFile("The file has a header row but no employees");
            }
            return rows;
        } catch (IOException | UncheckedIOException | IllegalStateException ex) {
            // e.g. a quote that is never closed
            throw new ImportExceptions.InvalidFile("The file isn't a readable CSV: " + ex.getMessage());
        }
    }

    /** Known columns by lower-case name, checking every required one is there. */
    private static Map<String, Integer> columns(List<String> headerNames) {
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < headerNames.size(); i++) {
            String name = headerNames.get(i) == null ? "" : headerNames.get(i).trim().toLowerCase(Locale.ROOT);
            if (ImportFormat.ALL.contains(name)) {
                columns.putIfAbsent(name, i);
            }
        }
        List<String> missing = ImportFormat.REQUIRED.stream().filter(c -> !columns.containsKey(c)).toList();
        if (!missing.isEmpty()) {
            throw new ImportExceptions.InvalidFile("Missing columns: " + String.join(", ", missing)
                    + ". Download the template to see the expected header row.");
        }
        return columns;
    }

    private static boolean isBlank(CSVRecord record) {
        for (String value : record) {
            if (value != null && !value.isBlank()) {
                return false;
            }
        }
        return true;
    }

    /** Strict UTF-8, so a file saved in another encoding is reported rather than read as garbled names. */
    private static String decode(byte[] bytes) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            return text.startsWith("﻿") ? text.substring(1) : text; // Excel's byte-order mark
        } catch (CharacterCodingException ex) {
            throw new ImportExceptions.InvalidFile("The file isn't UTF-8 text. In Excel, save it as \"CSV UTF-8\".");
        }
    }
}
