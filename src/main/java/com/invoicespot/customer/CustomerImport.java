package com.invoicespot.customer;

import static java.util.Map.entry;

import com.invoicespot.business.BusinessAccess;
import com.invoicespot.business.BusinessRepository;
import com.invoicespot.common.ApiException;
import com.invoicespot.customer.dto.CreateCustomerRequest;
import com.invoicespot.customer.dto.ImportPreviewResponse;
import com.invoicespot.customer.dto.ImportResultResponse;
import com.invoicespot.customer.dto.ImportRow;
import com.invoicespot.tax.Gstin;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

// Reads clients from a CSV file whose first row names the columns, in any
// order; a clients file exported from the app reads as it is. The preview and
// the import read the same file the same way, so the preview shows exactly
// which rows are added and which are skipped, and why.
@Service
public class CustomerImport {

    static final int MOST_ROWS = 1000;

    private static final List<String> FIELDS =
            List.of("name", "email", "phoneNumber", "vatTinNo", "address", "city", "country", "gstin");
    private static final Map<String, String> COLUMNS = Map.ofEntries(
            entry("name", "name"),
            entry("client", "name"),
            entry("client name", "name"),
            entry("customer", "name"),
            entry("customer name", "name"),
            entry("email", "email"),
            entry("e-mail", "email"),
            entry("email address", "email"),
            entry("phone", "phoneNumber"),
            entry("phone number", "phoneNumber"),
            entry("telephone", "phoneNumber"),
            entry("vat/tin", "vatTinNo"),
            entry("vat", "vatTinNo"),
            entry("tin", "vatTinNo"),
            entry("vat number", "vatTinNo"),
            entry("tax id", "vatTinNo"),
            entry("gstin", "gstin"),
            entry("gst number", "gstin"),
            entry("gst no", "gstin"),
            entry("address", "address"),
            entry("city", "city"),
            entry("country", "country"));
    private static final Pattern GUARD = Pattern.compile("'+[=+\\-@\t\r]");

    private final CustomerRepository customerRepository;
    private final CustomerService customerService;
    private final BusinessRepository businessRepository;
    private final Validator validator;

    public CustomerImport(
            CustomerRepository customerRepository,
            CustomerService customerService,
            BusinessRepository businessRepository,
            Validator validator) {
        this.customerRepository = customerRepository;
        this.customerService = customerService;
        this.businessRepository = businessRepository;
        this.validator = validator;
    }

    @Transactional(readOnly = true)
    public ImportPreviewResponse preview(BusinessAccess access, MultipartFile file) {
        List<ImportRow> rows = read(access.businessPkid(), file);
        return new ImportPreviewResponse(true, rows.size(), (int) rows.stream().filter(ImportRow::ready).count(), rows);
    }

    // Adds every row that is ready and skips the rest; nothing is added when
    // no row is ready. Imports into one business take turns, so two at once
    // never each wait for a client the other is adding.
    @Transactional
    public ImportResultResponse importAll(BusinessAccess access, MultipartFile file) {
        businessRepository
                .findForUpdate(access.businessPkid())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found"));
        List<ImportRow> rows = read(access.businessPkid(), file);
        List<ImportRow> ready = rows.stream().filter(ImportRow::ready).toList();
        if (ready.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No row in the file can be added");
        }
        ready.forEach(row -> customerService.create(access, row.request()));
        int skipped = rows.size() - ready.size();
        return new ImportResultResponse(
                true,
                count(ready.size(), "client") + " added" + (skipped == 0 ? "" : ", " + skipped + " skipped"),
                ready.size(),
                skipped);
    }

    private List<ImportRow> read(Long businessPkid, MultipartFile file) {
        String text = decode(file);
        // Blank lines stay records, so each record's number is its row in the
        // sheet; blank rows are left out after.
        CSVFormat format = CSVFormat.RFC4180.builder()
                .setDelimiter(delimiter(text))
                .setIgnoreEmptyLines(false)
                .get();
        List<CSVRecord> records;
        try (CSVParser parser = CSVParser.parse(text, format)) {
            records = parser.getRecords();
        } catch (IOException | UncheckedIOException | IllegalStateException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This file could not be read as CSV");
        }
        if (records.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The file is empty");
        }
        Map<String, Integer> columns = columns(records.get(0));
        if (!named(columns)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "The first row must name the columns, with at least Name and Email");
        }
        List<CSVRecord> clients = records.stream().skip(1).filter(record -> !blank(record)).toList();
        if (clients.size() > MOST_ROWS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Import at most 1,000 clients at a time");
        }
        Set<String> existing = customerRepository.findByBusinessPkidOrderByCreatedAtDesc(businessPkid).stream()
                .map(customer -> customer.getEmail().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        Map<String, Integer> seen = new HashMap<>();
        List<ImportRow> rows = new ArrayList<>();
        for (CSVRecord record : clients) {
            Map<String, String> values = new HashMap<>();
            columns.forEach((field, index) -> values.put(field, cell(record, index)));
            int row = (int) record.getRecordNumber();
            CreateCustomerRequest request = new CreateCustomerRequest(
                    values.get("name"),
                    values.get("email"),
                    values.get("phoneNumber"),
                    values.get("vatTinNo"),
                    values.get("address"),
                    values.get("city"),
                    values.get("country"),
                    values.get("gstin"),
                    null);
            List<String> problems = problems(request);
            String email = request.email() == null ? null : request.email().toLowerCase(Locale.ROOT);
            if (email != null && existing.contains(email)) {
                problems.add("You already have a client with this email");
            } else if (email != null && seen.containsKey(email)) {
                problems.add("This email is already on row " + seen.get(email));
            }
            if (email != null) {
                seen.putIfAbsent(email, row);
            }
            rows.add(new ImportRow(
                    row,
                    request.name(),
                    request.email(),
                    request.phoneNumber(),
                    request.vatTinNo(),
                    request.address(),
                    request.city(),
                    request.country(),
                    request.gstin(),
                    problems));
        }
        return rows;
    }

    // What stops a row from becoming a client, in the order of its fields.
    private List<String> problems(CreateCustomerRequest request) {
        List<String> problems = new ArrayList<>();
        if (request.name() == null) {
            problems.add("The name is missing");
        }
        if (request.email() == null) {
            problems.add("The email is missing");
        }
        if (request.phoneNumber() == null) {
            problems.add("The phone number is missing");
        }
        validator.validate(request).stream()
                .sorted(Comparator.comparing(violation -> FIELDS.indexOf(violation.getPropertyPath().toString())))
                .map(ConstraintViolation::getMessage)
                .forEach(problems::add);
        if (request.gstin() != null && !Gstin.isValid(request.gstin().trim().toUpperCase(Locale.ROOT))) {
            problems.add("The GSTIN isn't valid");
        }
        return problems;
    }

    private static Map<String, Integer> columns(CSVRecord header) {
        Map<String, Integer> columns = new HashMap<>();
        for (int index = 0; index < header.size(); index++) {
            String name = header.get(index).trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
            String field = COLUMNS.get(name);
            if (field != null) {
                columns.putIfAbsent(field, index);
            }
        }
        return columns;
    }

    // A cell as typed: trimmed, empty as missing, and without the apostrophe
    // an export puts before text a spreadsheet would read as a formula, and
    // before text already starting with apostrophes ahead of such a character.
    private static String cell(CSVRecord record, int index) {
        if (index >= record.size()) {
            return null;
        }
        String value = record.get(index).trim();
        if (GUARD.matcher(value).lookingAt()) {
            value = value.substring(1);
        }
        return value.isEmpty() ? null : value;
    }

    private static boolean blank(CSVRecord record) {
        return record.stream().allMatch(String::isBlank);
    }

    // Spreadsheets set to a comma for decimals save with semicolons. A file is
    // read with semicolons when its first row, split at them, names the
    // columns; counting the characters can't tell, as a heading may hold either.
    private static char delimiter(String text) {
        try (CSVParser parser = CSVParser.parse(text, CSVFormat.RFC4180.builder().setDelimiter(';').get())) {
            Iterator<CSVRecord> records = parser.iterator();
            return records.hasNext() && named(columns(records.next())) ? ';' : ',';
        } catch (IOException | UncheckedIOException | IllegalStateException exception) {
            return ',';
        }
    }

    // Whether a first row names at least the Name and Email columns.
    private static boolean named(Map<String, Integer> columns) {
        return columns.containsKey("name") && columns.containsKey("email");
    }

    private static String decode(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a CSV file to import");
        }
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(file.getBytes()))
                    .toString();
            return text.startsWith("﻿") ? text.substring(1) : text;
        } catch (CharacterCodingException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Save the file as CSV UTF-8, then try again");
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The file could not be read");
        }
    }

    private static String count(int number, String noun) {
        return number + " " + noun + (number == 1 ? "" : "s");
    }
}
