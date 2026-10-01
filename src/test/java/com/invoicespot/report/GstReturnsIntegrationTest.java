package com.invoicespot.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// The GST helper files for a Maharashtra seller's month: each GSTR-1 table in
// the offline tool's columns, credit notes netted or listed where their
// invoices went, exports valued in rupees, the B2C large limit, and the
// GSTR-3B figures they add up to. Documents made outside GST mode are left
// out and counted.
class GstReturnsIntegrationTest extends IntegrationTest {

    private static final String MUMBAI = "27AAACB1234C1ZF";
    private static final String BENGALURU = "29AAGCB7383J1Z4";

    private final YearMonth month = YearMonth.now(ZoneOffset.UTC);
    private final String date =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH).format(LocalDate.now(ZoneOffset.UTC));
    private String session;

    private ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private void settings(String regime) throws Exception {
        send(put("/api/v1/settings"), """
                {"defaultCurrency": "INR", "paymentTermsDays": 15, "defaultTaxRate": 0, "taxLabel": "GST",
                 "timeZone": "UTC", "taxRegime": "%s", "lutReference": "AD270924012345X"}
                """.formatted(regime))
                .andExpect(status().isOk());
    }

    private String client(String name, String extra) throws Exception {
        return JsonPath.read(
                body(send(post("/api/v1/customer/create"), """
                        {"name": "%s", "email": "%s@clients.test", "phoneNumber": "+91 1" %s}
                        """.formatted(name, UUID.randomUUID(), extra))),
                "$.createdCustomer._id");
    }

    // A line is "quantity x price @ rate", with an HSN/SAC code and a unit if
    // given.
    private static String line(String quantity, String price, String rate, String code, String unit) {
        return """
                {"itemName": "Work", "quantity": %s, "unitPrice": %s, "taxRate": "%s" %s %s}"""
                .formatted(
                        quantity,
                        price,
                        rate,
                        code == null ? "" : ", \"hsnSac\": \"%s\"".formatted(code),
                        unit == null ? "" : ", \"unit\": \"%s\"".formatted(unit));
    }

    private String invoice(String client, String extra, String... lines) throws Exception {
        return JsonPath.read(
                body(send(post("/api/v1/document/create"), """
                                {"customerId": "%s", "documentType": "Invoice", "currency": "INR" %s,
                                 "billingItems": [%s]}
                                """.formatted(client, extra, String.join(", ", lines)))
                        .andExpect(status().isOk())),
                "$.newDocument._id");
    }

    private void credit(String invoice, String line) throws Exception {
        String note = JsonPath.read(
                body(send(post("/api/v1/document/{id}/credit-note", invoice), "").andExpect(status().isCreated())),
                "$.newDocument._id");
        send(patch("/api/v1/document/{id}", note), "{\"billingItems\": [%s]}".formatted(line))
                .andExpect(status().isOk());
        send(post("/api/v1/document/{id}/issue", note), "{}").andExpect(status().isOk());
    }

    private ResultActions summary(String from, String to) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/report/gst");
        if (from != null) {
            request.param("from", from);
        }
        if (to != null) {
            request.param("to", to);
        }
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, session));
    }

    private List<String> csv(String section) throws Exception {
        return new String(
                        mockMvc.perform(get("/api/v1/report/gst/{section}/csv", section)
                                        .param("from", month.toString())
                                        .param("to", month.toString())
                                        .header(HttpHeaders.AUTHORIZATION, session))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsByteArray(),
                        StandardCharsets.UTF_8)
                .lines()
                .skip(1)
                .toList();
    }

    @BeforeEach
    void setUp() throws Exception {
        session = bearer(createUser());
        send(patch("/api/v1/business"), "{\"taxId\": \"27AAPFU0939F1ZV\"}").andExpect(status().isOk());
        settings("GST_INDIA");
        String mumbai = client("Mumbai Co", ", \"gstin\": \"%s\"".formatted(MUMBAI));
        String bengaluru = client("Bengaluru Co", ", \"gstin\": \"%s\"".formatted(BENGALURU));
        String delhi = client("Delhi Walk In", ", \"stateCode\": \"07\"");
        String pune = client("Pune Walk In", ", \"stateCode\": \"27\"");
        String abroad = client("Acme Inc", ", \"stateCode\": \"96\"");

        String within = invoice(mumbai, "", line("1", "1000", "18", "998314", null)); // INV-0001
        invoice(bengaluru, "", line("1", "2000", "18", "998314", null), line("5", "100", "5", "4901", "PCS"));
        invoice(bengaluru, ", \"reverseCharge\": true", line("1", "300", "18", "998314", null)); // INV-0003
        String large = invoice(delhi, "", line("1", "90000", "18", "998314", null)); // INV-0004
        String small = invoice(delhi, "", line("1", "1000", "18", "998314", null)); // INV-0005
        invoice(pune, "", line("20", "100", "5", "4901", "pcs")); // INV-0006
        invoice(abroad, ", \"currency\": \"USD\", \"exchangeRate\": \"83.25\"",
                line("1", "1000", "0", "998314", null)); // INV-0007, under the LUT
        String withIgst = invoice(
                abroad,
                ", \"currency\": \"USD\", \"exchangeRate\": \"83.25\", \"exportType\": \"IGST\"",
                line("1", "100", "18", "998314", null)); // INV-0008
        invoice(delhi, "", line("1", "100000", "0", null, null)); // INV-0009, exactly the limit, no code

        credit(within, line("1", "100", "18", "998314", null)); // CN-0001
        credit(large, line("1", "1000", "18", "998314", null)); // CN-0002
        credit(small, line("1", "200", "18", "998314", null)); // CN-0003
        credit(withIgst, line("1", "10", "18", "998314", null)); // CN-0004

        // Left out: a void invoice, a draft, and a receipt made outside GST mode.
        String voided = invoice(mumbai, "", line("1", "5000", "18", "998314", null)); // INV-0010
        send(post("/api/v1/document/{id}/void", voided), "{\"reason\": \"Sent twice\"}").andExpect(status().isOk());
        invoice(mumbai, ", \"draft\": true", line("1", "7000", "18", "998314", null));
        settings("STANDARD");
        send(post("/api/v1/document/create"), """
                {"customerId": "%s", "documentType": "Receipt", "currency": "INR",
                 "billingItems": [{"itemName": "Work", "quantity": 1, "unitPrice": 50, "taxRate": "18"}]}
                """.formatted(pune))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("invoices to registered buyers by rate, with their place of supply and reverse charge")
    void b2b() throws Exception {
        assertThat(csv("b2b")).containsExactly(
                MUMBAI + ",Mumbai Co,INV-0001," + date + ",1180.00,27-Maharashtra,N,,Regular B2B,,18,1000.00,",
                BENGALURU + ",Bengaluru Co,INV-0002," + date + ",2885.00,29-Karnataka,N,,Regular B2B,,5,500.00,",
                BENGALURU + ",Bengaluru Co,INV-0002," + date + ",2885.00,29-Karnataka,N,,Regular B2B,,18,2000.00,",
                BENGALURU + ",Bengaluru Co,INV-0003," + date + ",300.00,29-Karnataka,Y,,Regular B2B,,18,300.00,");
        assertThat(csv("cdnr")).containsExactly(
                MUMBAI + ",Mumbai Co,CN-0001," + date + ",C,27-Maharashtra,N,Regular B2B,118.00,,18,100.00,");
    }

    @Test
    @DisplayName("unregistered sales: above the limit to another state one by one, the rest per place and rate")
    void b2c() throws Exception {
        assertThat(csv("b2cl")).containsExactly("INV-0004," + date + ",106200.00,07-Delhi,,18,90000.00,,");
        // INV-0009 is worth exactly the limit, so it is a small one; CN-0003
        // comes off INV-0005.
        assertThat(csv("b2cs")).containsExactly(
                "OE,07-Delhi,,0,100000.00,,",
                "OE,07-Delhi,,18,800.00,,",
                "OE,27-Maharashtra,,5,2000.00,,");
        assertThat(csv("cdnur")).containsExactly(
                "B2CL,CN-0002," + date + ",C,07-Delhi,1180.00,,18,1000.00,",
                "EXPWP,CN-0004," + date + ",C,,982.35,,18,832.50,");
    }

    @Test
    @DisplayName("exports under the LUT or with IGST, valued in rupees at their own rate")
    void exports() throws Exception {
        assertThat(csv("exp")).containsExactly(
                "WOPAY,INV-0007," + date + ",83250.00,,,,0,83250.00,",
                "WPAY,INV-0008," + date + ",9823.50,,,,18,8325.00,");
    }

    @Test
    @DisplayName("the HSN summary per code, unit and rate, less credit notes, with lines missing a code apart")
    void hsn() throws Exception {
        assertThat(csv("hsn-b2b")).containsExactly(
                "4901,,PCS,5,525.00,5,500.00,25.00,0.00,0.00,",
                "998314,,NA,0,3776.00,18,3200.00,414.00,81.00,81.00,");
        assertThat(csv("hsn-b2c")).containsExactly(
                "4901,,PCS,20,2100.00,5,2000.00,0.00,50.00,50.00,",
                "998314,,NA,0,83250.00,0,83250.00,0.00,0.00,0.00,",
                "998314,,NA,0,114805.15,18,97292.50,17512.65,0.00,0.00,",
                "(missing HSN/SAC),,OTH,1,100000.00,0,100000.00,0.00,0.00,0.00,");
    }

    @Test
    @DisplayName("GSTR-3B: taxable, zero-rated and nil-rated supplies, inter-state ones to unregistered buyers")
    void gstr3b() throws Exception {
        assertThat(csv("gstr3b")).containsExactly(
                "3.1(a),\"Outward taxable supplies (other than zero rated, nil rated and exempted)\","
                        + "95200.00,16549.00,131.00,131.00,",
                "3.1(b),Outward taxable supplies (zero rated),90742.50,1348.65,,,",
                "3.1(c),\"Other outward supplies (nil rated, exempted)\",100000.00,,,,",
                "3.2,\"Inter-state supplies to unregistered persons, 07-Delhi\",89800.00,16164.00,,,",
                ",\"Outward supplies under reverse charge, whose tax the recipient pays (not in 3.1(a))\","
                        + "300.00,,,,");
    }

    @Test
    @DisplayName("the summary counts each file's rows and what was left out, for at most 12 months up to this one")
    void summary() throws Exception {
        summary(month.toString(), month.toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value(month.toString()))
                .andExpect(jsonPath("$.b2clLimit").value(100000))
                .andExpect(jsonPath("$.files[0].key").value("b2b"))
                .andExpect(jsonPath("$.files[0].rows").value(4))
                .andExpect(jsonPath("$.files[2].key").value("b2cs"))
                .andExpect(jsonPath("$.files[2].rows").value(3))
                .andExpect(jsonPath("$.files[7].key").value("hsn-b2c"))
                .andExpect(jsonPath("$.files[7].rows").value(4))
                .andExpect(jsonPath("$.gstr3b[0].taxable").value(95200.0))
                .andExpect(jsonPath("$.gstr3b[1].cgst").doesNotExist())
                .andExpect(jsonPath("$.documents").value(13))
                .andExpect(jsonPath("$.skipped").value(1))
                .andExpect(jsonPath("$.unconverted").value(0))
                .andExpect(jsonPath("$.missingHsn").value(1));
        summary(null, null)
                .andExpect(jsonPath("$.from").value(month.minusMonths(1).toString()))
                .andExpect(jsonPath("$.to").value(month.minusMonths(1).toString()))
                .andExpect(jsonPath("$.documents").value(0));
        mockMvc.perform(get("/api/v1/report/gst/b2b/csv").header(HttpHeaders.AUTHORIZATION, session))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"invoice-spot-gstr1-b2b-%s.csv\"".formatted(month.minusMonths(1))));

        summary("2026-13", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Enter months as YYYY-MM"));
        summary(month.toString(), month.minusMonths(1).toString())
                .andExpect(jsonPath("$.message").value("The first month can't come after the last"));
        summary(month.minusMonths(12).toString(), month.toString())
                .andExpect(jsonPath("$.message").value("Choose at most 12 months"));
        summary(month.plusMonths(1).toString(), null)
                .andExpect(jsonPath("$.message").value("Choose months up to this one"));
        summary("2017-06", "2017-06").andExpect(jsonPath("$.message").value("GST returns start from July 2017"));
        mockMvc.perform(get("/api/v1/report/gst/gstr2/csv").header(HttpHeaders.AUTHORIZATION, session))
                .andExpect(status().isNotFound());
    }
}
