package com.invoicespot.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// India GST mode: a Maharashtra seller charges CGST and SGST within the
// state, IGST to other states, nothing on an export under its LUT and IGST on
// one without, and leaves the tax to the recipient under reverse charge. The
// PDF says so, and an issued document keeps it when the mode is turned off.
class GstInvoiceIntegrationTest extends IntegrationTest {

    private static final String SELLER = "27AAPFU0939F1ZV";
    private static final String MUMBAI_CLIENT = "27AAACB1234C1ZF";
    private static final String BENGALURU_CLIENT = "29AAGCB7383J1Z4";
    private static final String LUT = "AD270924012345X";

    @Autowired
    private RecurringService recurring;

    private String session;

    private ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private ResultActions settings(String regime, String extra) throws Exception {
        return send(put("/api/v1/settings"), """
                {"defaultCurrency": "INR", "paymentTermsDays": 15, "defaultTaxRate": 18, "taxLabel": "GST",
                 "timeZone": "UTC", "taxRegime": "%s", "lutReference": "%s" %s}
                """.formatted(regime, LUT, extra));
    }

    @BeforeEach
    void setUp() throws Exception {
        session = bearer(createUser());
        send(patch("/api/v1/business"), """
                {"legalName": "Ada Analytics Private Limited", "taxId": "%s"}
                """.formatted(SELLER.toLowerCase()))
                .andExpect(status().isOk());
        settings("GST_INDIA", "").andExpect(status().isOk());
    }

    private String client(String name, String gst) throws Exception {
        return JsonPath.read(
                body(send(post("/api/v1/customer/create"), """
                        {"name": "%s", "email": "%s@clients.test", "phoneNumber": "+91 1", "country": "India" %s}
                        """.formatted(name, UUID.randomUUID(), gst))),
                "$.createdCustomer._id");
    }

    private ResultActions invoice(String client, String extra) throws Exception {
        return send(post("/api/v1/document/create"), """
                {"customerId": "%s", "documentType": "Invoice", "currency": "INR" %s,
                 "billingItems": [{"itemName": "Design", "unitPrice": 1000, "quantity": 1, "taxRate": "18"},
                                  {"itemName": "Prints", "unitPrice": 101, "quantity": 1, "taxRate": "5"}]}
                """.formatted(client, extra));
    }

    private ResultActions usdExport(String client, String extra) throws Exception {
        return send(post("/api/v1/document/create"), """
                {"customerId": "%s", "documentType": "Invoice", "currency": "USD", "exchangeRate": "83.25" %s,
                 "billingItems": [{"itemName": "Design", "unitPrice": 1000, "quantity": 1, "taxRate": "18"}]}
                """.formatted(client, extra));
    }

    private String pdfText(String id) throws Exception {
        return PdfText.of(mockMvc.perform(get("/api/v1/document/{id}/pdf", id)
                        .header(HttpHeaders.AUTHORIZATION, session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray())
                .replaceAll("\\s+", " ");
    }

    // The invoice a monthly schedule starting today makes from a template.
    private String repeated(String template) throws Exception {
        send(post("/api/v1/recurring"), """
                {"documentId": "%s", "frequency": "monthly", "startDate": "%s"}
                """.formatted(template, LocalDate.now(ZoneOffset.UTC)))
                .andExpect(status().isCreated());
        recurring.makeDue(Instant.now());
        return JsonPath.read(
                body(send(get("/api/v1/recurring"), "").andExpect(jsonPath("$.schedules[0].runsDone").value(1))),
                "$.schedules[0].lastInvoice._id");
    }

    @Test
    @DisplayName("within the seller's state each rate is halved into CGST and SGST, each rounded, so they match")
    void intraState() throws Exception {
        String invoice = JsonPath.read(
                body(invoice(client("Mumbai Co", ", \"gstin\": \"%s\"".formatted(MUMBAI_CLIENT)), "")
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.newDocument.gst.sellerStateCode").value("27"))
                        .andExpect(jsonPath("$.newDocument.gst.sellerStateName").value("Maharashtra"))
                        .andExpect(jsonPath("$.newDocument.gst.placeOfSupply").value("27"))
                        .andExpect(jsonPath("$.newDocument.gst.supplyType").value("INTRA"))
                        .andExpect(jsonPath("$.newDocument.taxes[0].rate").value(5))
                        .andExpect(jsonPath("$.newDocument.taxes[0].cgst").value(2.53))
                        .andExpect(jsonPath("$.newDocument.taxes[0].sgst").value(2.53))
                        .andExpect(jsonPath("$.newDocument.taxes[0].tax").value(5.06))
                        .andExpect(jsonPath("$.newDocument.taxes[1].cgst").value(90.0))
                        .andExpect(jsonPath("$.newDocument.taxes[1].igst").value(0.0))
                        .andExpect(jsonPath("$.newDocument.total").value(1286.06))),
                "$.newDocument._id");
        assertThat(pdfText(invoice)).contains(
                "Tax Invoice",
                "GSTIN " + SELLER,
                "State: Maharashtra (27)",
                "GSTIN " + MUMBAI_CLIENT,
                "Place of supply: 27 – Maharashtra",
                "Reverse charge No",
                "CGST 9% INR 90.00",
                "SGST 9% INR 90.00",
                "CGST 2.5% INR 2.53",
                "Rupees One Thousand Two Hundred Eighty Six and Six Paise Only",
                "For Ada Analytics Private Limited",
                "Authorised signatory");

        // The share page shows the seller as the PDF does.
        String url = JsonPath.read(body(send(post("/api/v1/document/{id}/share", invoice), "")), "$.url");
        mockMvc.perform(get("/api/v1/public/share/{token}", url.substring(url.lastIndexOf('/') + 1)))
                .andExpect(jsonPath("$.business.lines", hasItems("GSTIN " + SELLER, "State: Maharashtra (27)")));
    }

    @Test
    @DisplayName("to another state it is IGST, and under reverse charge the recipient pays it outside the total")
    void interStateAndReverseCharge() throws Exception {
        String bengaluru = client("Bengaluru Co", ", \"gstin\": \"%s\"".formatted(BENGALURU_CLIENT));
        invoice(bengaluru, "")
                .andExpect(jsonPath("$.newDocument.gst.placeOfSupply").value("29"))
                .andExpect(jsonPath("$.newDocument.gst.supplyType").value("INTER"))
                .andExpect(jsonPath("$.newDocument.taxes[0].igst").value(5.05))
                .andExpect(jsonPath("$.newDocument.total").value(1286.05));
        String reverse = JsonPath.read(
                body(invoice(bengaluru, ", \"reverseCharge\": true")
                        .andExpect(jsonPath("$.newDocument.gst.reverseCharge").value(true))
                        .andExpect(jsonPath("$.newDocument.taxes[1].igst").value(180.0))
                        .andExpect(jsonPath("$.newDocument.salesTax").value(0))
                        .andExpect(jsonPath("$.newDocument.total").value(1101.0))),
                "$.newDocument._id");
        assertThat(pdfText(reverse)).contains(
                "Place of supply: 29 – Karnataka",
                "Reverse charge Yes",
                "IGST 18% (payable by the recipient) INR 180.00",
                "Balance due INR 1,101.00");

        // An unregistered client in the seller's state, sent elsewhere by choice.
        String walkIn = client("Walk In", ", \"stateCode\": \"27\"");
        invoice(walkIn, ", \"placeOfSupply\": \"07\"")
                .andExpect(jsonPath("$.newDocument.gst.placeOfSupply").value("07"))
                .andExpect(jsonPath("$.newDocument.gst.supplyType").value("INTER"));
        invoice(walkIn, ", \"placeOfSupply\": \"99\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose the place of supply from the list"));
    }

    @Test
    @DisplayName("an export goes under the LUT without tax, or with IGST, valued in rupees at its rate")
    void exports() throws Exception {
        String abroad = client("Acme Inc", ", \"stateCode\": \"96\"");
        String lut = JsonPath.read(
                body(send(post("/api/v1/document/create"), """
                                {"customerId": "%s", "documentType": "Invoice", "currency": "USD",
                                 "exchangeRate": "83.25",
                                 "billingItems": [{"itemName": "Design", "unitPrice": 1000, "quantity": 1,
                                                   "taxRate": "18"}]}
                                """.formatted(abroad))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.newDocument.gst.supplyType").value("EXPORT_LUT"))
                        .andExpect(jsonPath("$.newDocument.gst.lutReference").value(LUT))
                        .andExpect(jsonPath("$.newDocument.billingItems[0].taxRate").value(0))
                        .andExpect(jsonPath("$.newDocument.total").value(1000.0))),
                "$.newDocument._id");
        assertThat(pdfText(lut)).contains(
                "Tax Invoice",
                "Unregistered",
                "Place of supply: Outside India (96)",
                "Country of destination: India",
                "Supply meant for export under LUT " + LUT + " without payment of IGST",
                "1 USD = ₹83.2500; taxable value ₹83,250.00",
                "USD One Thousand and 00/100");

        send(post("/api/v1/document/create"), """
                        {"customerId": "%s", "documentType": "Invoice", "currency": "USD", "exchangeRate": "83.25",
                         "exportType": "IGST",
                         "billingItems": [{"itemName": "Design", "unitPrice": 1000, "quantity": 1, "taxRate": "18"}]}
                        """.formatted(abroad))
                .andExpect(jsonPath("$.newDocument.gst.supplyType").value("EXPORT_IGST"))
                .andExpect(jsonPath("$.newDocument.taxes[0].igst").value(180.0));
        invoice(abroad, ", \"reverseCharge\": true")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Reverse charge doesn't apply to exports"));

        String unrated = JsonPath.read(
                body(send(post("/api/v1/document/create"), """
                        {"customerId": "%s", "documentType": "Invoice", "currency": "USD", "draft": true,
                         "billingItems": [{"itemName": "Design", "unitPrice": 1000, "quantity": 1}]}
                        """.formatted(abroad))),
                "$.newDocument._id");
        send(post("/api/v1/document/{id}/issue", unrated), "{}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A document in USD needs an exchange rate to INR in GST mode"));
    }

    @Test
    @DisplayName("a draft exported under the LUT keeps its lines' rates, is taxed at them when it goes elsewhere, "
            + "and is issued at 0%")
    void lutDraftKeepsRates() throws Exception {
        String abroad = client("Acme Inc", ", \"stateCode\": \"96\"");
        String draft = JsonPath.read(
                body(invoice(abroad, ", \"draft\": true")
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.newDocument.gst.supplyType").value("EXPORT_LUT"))
                        .andExpect(jsonPath("$.newDocument.billingItems[*].taxRate", contains(18, 5)))
                        .andExpect(jsonPath("$.newDocument.salesTax").value(0))
                        .andExpect(jsonPath("$.newDocument.total").value(1101.0))),
                "$.newDocument._id");
        send(get("/api/v1/document/{id}", draft), "")
                .andExpect(jsonPath("$.document.billingItems[*].taxRate", contains(18, 5)));

        // Sent within the state after all, it is taxed at the rates its lines kept.
        send(patch("/api/v1/document/{id}", draft), "{\"placeOfSupply\": \"27\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedDocument.gst.supplyType").value("INTRA"))
                .andExpect(jsonPath("$.updatedDocument.salesTax").value(185.06))
                .andExpect(jsonPath("$.updatedDocument.total").value(1286.06));
        send(patch("/api/v1/document/{id}", draft), "{\"placeOfSupply\": \"96\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedDocument.gst.supplyType").value("EXPORT_LUT"))
                .andExpect(jsonPath("$.updatedDocument.salesTax").value(0));
        send(post("/api/v1/document/{id}/issue", draft), "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedDocument.billingItems[*].taxRate", contains(0, 0)))
                .andExpect(jsonPath("$.updatedDocument.total").value(1101.0));
    }

    @Test
    @DisplayName("each line can carry an HSN or SAC code, and the PDF sums the tax by code within each rate")
    void hsnCodes() throws Exception {
        String mumbai = client("Mumbai Co", ", \"gstin\": \"%s\"".formatted(MUMBAI_CLIENT));
        String invoice = JsonPath.read(
                body(send(post("/api/v1/document/create"), """
                                {"customerId": "%s", "documentType": "Invoice", "currency": "INR",
                                 "billingItems": [{"itemName": "Design", "unitPrice": 10.05, "quantity": 1,
                                                   "taxRate": "18", "hsnSac": "9983 91"},
                                                  {"itemName": "Prints", "unitPrice": 20, "quantity": 1,
                                                   "taxRate": "5"},
                                                  {"itemName": "Layout", "unitPrice": 10.05, "quantity": 1,
                                                   "taxRate": "18", "hsnSac": "998314"}]}
                                """.formatted(mumbai))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.newDocument.billingItems[0].hsnSac").value("998391"))
                        .andExpect(jsonPath("$.newDocument.billingItems[1].hsnSac").doesNotExist())),
                "$.newDocument._id");
        assertThat(pdfText(invoice)).contains(
                "Design HSN/SAC 998391",
                "998314 18% INR 10.05 0.91 0.91 0.00 1.82",
                "998391 18% INR 10.05 0.90 0.90 0.00 1.80",
                "— 5% INR 20.00 0.50 0.50 0.00 1.00");

        // A copy keeps each line's code.
        send(post("/api/v1/document/{id}/duplicate", invoice), "")
                .andExpect(jsonPath("$.newDocument.billingItems[2].hsnSac").value("998314"));
        send(post("/api/v1/document/create"), """
                        {"customerId": "%s", "documentType": "Invoice", "currency": "INR",
                         "billingItems": [{"itemName": "Design", "unitPrice": 10, "quantity": 1, "hsnSac": "99A3"}]}
                        """.formatted(mumbai))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Enter an HSN or SAC code of 4 to 8 digits"));
    }

    @Test
    @DisplayName("GST mode needs the business's GSTIN, reports in rupees and keeps numbers GST can take")
    void settingsRules() throws Exception {
        send(get("/api/v1/settings"), "")
                .andExpect(jsonPath("$.settings.taxRegime").value("GST_INDIA"))
                .andExpect(jsonPath("$.settings.baseCurrency").value("INR"));
        send(get("/api/v1/business"), "").andExpect(jsonPath("$.business.taxId").value(SELLER));
        send(patch("/api/v1/business"), "{\"taxId\": \"27AAPFU0939F1ZW\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("In GST mode your tax ID must be a valid GSTIN"));
        settings("GST_INDIA", ", \"invoicePrefix\": \"INV 2026 \"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "In GST mode, invoice, receipt and credit note prefixes can use only letters, digits, / and "
                                + "-, up to 12 characters"));

        session = bearer(createUser());
        settings("GST_INDIA", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Add your business's GSTIN under Settings → Business before turning on GST mode"));
    }

    @Test
    @DisplayName("an issued document keeps its GST when the mode is turned off, and its credit note copies it")
    void keptAsIssued() throws Exception {
        String mumbai = client("Mumbai Co", ", \"gstin\": \"%s\"".formatted(MUMBAI_CLIENT));
        String invoice = JsonPath.read(body(invoice(mumbai, "")), "$.newDocument._id");
        String draft = JsonPath.read(body(invoice(mumbai, ", \"draft\": true")), "$.newDocument._id");
        String note = JsonPath.read(
                body(send(post("/api/v1/document/{id}/credit-note", invoice), "")
                        .andExpect(jsonPath("$.newDocument.gst.supplyType").value("INTRA"))
                        .andExpect(jsonPath("$.newDocument.taxes[1].sgst").value(90.0))),
                "$.newDocument._id");

        settings("STANDARD", "").andExpect(status().isOk());
        send(get("/api/v1/document/{id}", invoice), "")
                .andExpect(jsonPath("$.document.gst.supplyType").value("INTRA"))
                .andExpect(jsonPath("$.document.taxes[1].cgst").value(90.0))
                .andExpect(jsonPath("$.document.total").value(1286.06));
        assertThat(pdfText(invoice)).contains("Tax Invoice", "CGST 9% INR 90.00");
        send(patch("/api/v1/document/{id}", draft), "{\"additionalInfo\": \"Thanks\"}")
                .andExpect(jsonPath("$.updatedDocument.gst").doesNotExist())
                .andExpect(jsonPath("$.updatedDocument.taxes[0].tax").value(5.05));
        send(post("/api/v1/document/{id}/issue", note), "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedDocument.gst.supplyType").value("INTRA"));
    }

    @Test
    @DisplayName("a draft issued after its client moves goes where the client is now, unless its place was chosen")
    void placeFollowsTheClientToIssue() throws Exception {
        String mumbai = client("Mumbai Co", ", \"gstin\": \"%s\"".formatted(MUMBAI_CLIENT));
        String draft = JsonPath.read(
                body(invoice(mumbai, ", \"draft\": true")
                        .andExpect(jsonPath("$.newDocument.gst.placeOfSupply").value("27"))
                        .andExpect(jsonPath("$.newDocument.gst.supplyType").value("INTRA"))),
                "$.newDocument._id");
        String chosen = JsonPath.read(
                body(invoice(mumbai, ", \"draft\": true, \"placeOfSupply\": \"07\"")), "$.newDocument._id");

        // The client re-registers in Karnataka before the drafts are issued.
        send(patch("/api/v1/customer/{id}", mumbai), "{\"gstin\": \"%s\"}".formatted(BENGALURU_CLIENT))
                .andExpect(status().isOk());

        send(post("/api/v1/document/{id}/issue", draft), "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedDocument.customer.gstin").value(BENGALURU_CLIENT))
                .andExpect(jsonPath("$.updatedDocument.gst.placeOfSupply").value("29"))
                .andExpect(jsonPath("$.updatedDocument.gst.supplyType").value("INTER"))
                .andExpect(jsonPath("$.updatedDocument.taxes[0].igst").value(5.05))
                .andExpect(jsonPath("$.updatedDocument.taxes[1].cgst").value(0.0))
                .andExpect(jsonPath("$.updatedDocument.total").value(1286.05));
        assertThat(pdfText(draft)).contains(
                "GSTIN " + BENGALURU_CLIENT, "Place of supply: 29 – Karnataka", "IGST 18% INR 180.00");
        send(post("/api/v1/document/{id}/issue", chosen), "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedDocument.gst.placeOfSupply").value("07"))
                .andExpect(jsonPath("$.updatedDocument.gst.supplyType").value("INTER"));
    }

    @Test
    @DisplayName("a repeated invoice goes where its client is now")
    void repeatedInvoiceFollowsTheClient() throws Exception {
        String mumbai = client("Mumbai Co", ", \"gstin\": \"%s\"".formatted(MUMBAI_CLIENT));
        String template = JsonPath.read(body(invoice(mumbai, "")), "$.newDocument._id");
        send(patch("/api/v1/customer/{id}", mumbai), "{\"gstin\": \"%s\"}".formatted(BENGALURU_CLIENT))
                .andExpect(status().isOk());

        send(get("/api/v1/document/{id}", repeated(template)), "")
                .andExpect(jsonPath("$.document.documentNumber").value("INV-0002"))
                .andExpect(jsonPath("$.document.customer.gstin").value(BENGALURU_CLIENT))
                .andExpect(jsonPath("$.document.gst.placeOfSupply").value("29"))
                .andExpect(jsonPath("$.document.gst.supplyType").value("INTER"))
                .andExpect(jsonPath("$.document.total").value(1286.05));
    }

    @Test
    @DisplayName("a repeated invoice keeps a place of supply that was chosen")
    void repeatedInvoiceKeepsAChosenPlace() throws Exception {
        String mumbai = client("Mumbai Co", ", \"gstin\": \"%s\"".formatted(MUMBAI_CLIENT));
        String template = JsonPath.read(body(invoice(mumbai, ", \"placeOfSupply\": \"07\"")), "$.newDocument._id");
        send(patch("/api/v1/customer/{id}", mumbai), "{\"gstin\": \"%s\"}".formatted(BENGALURU_CLIENT))
                .andExpect(status().isOk());

        send(get("/api/v1/document/{id}", repeated(template)), "")
                .andExpect(jsonPath("$.document.documentNumber").value("INV-0002"))
                .andExpect(jsonPath("$.document.gst.placeOfSupply").value("07"))
                .andExpect(jsonPath("$.document.gst.supplyType").value("INTER"));
    }

    @Test
    @DisplayName("a copy goes where its original went, split and charged the same")
    void copies() throws Exception {
        String intra = JsonPath.read(
                body(invoice(client("Mumbai Co", ", \"gstin\": \"%s\"".formatted(MUMBAI_CLIENT)), "")),
                "$.newDocument._id");
        String reverse = JsonPath.read(
                body(invoice(
                        client("Bengaluru Co", ", \"gstin\": \"%s\"".formatted(BENGALURU_CLIENT)),
                        ", \"reverseCharge\": true")),
                "$.newDocument._id");
        String chosen = JsonPath.read(
                body(invoice(client("Walk In", ", \"stateCode\": \"27\""), ", \"placeOfSupply\": \"07\"")),
                "$.newDocument._id");
        String export = JsonPath.read(
                body(usdExport(client("Acme Inc", ", \"stateCode\": \"96\""), ", \"exportType\": \"IGST\"")),
                "$.newDocument._id");

        send(post("/api/v1/document/{id}/duplicate", intra), "")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.newDocument.gst.sellerStateCode").value("27"))
                .andExpect(jsonPath("$.newDocument.gst.supplyType").value("INTRA"))
                .andExpect(jsonPath("$.newDocument.taxes[0].cgst").value(2.53))
                .andExpect(jsonPath("$.newDocument.taxes[1].sgst").value(90.0))
                .andExpect(jsonPath("$.newDocument.total").value(1286.06));
        send(post("/api/v1/document/{id}/duplicate", reverse), "")
                .andExpect(jsonPath("$.newDocument.gst.reverseCharge").value(true))
                .andExpect(jsonPath("$.newDocument.taxes[1].igst").value(180.0))
                .andExpect(jsonPath("$.newDocument.salesTax").value(0))
                .andExpect(jsonPath("$.newDocument.total").value(1101.0));
        send(post("/api/v1/document/{id}/duplicate", chosen), "")
                .andExpect(jsonPath("$.newDocument.gst.placeOfSupply").value("07"))
                .andExpect(jsonPath("$.newDocument.gst.supplyType").value("INTER"));
        send(post("/api/v1/document/{id}/duplicate", export), "")
                .andExpect(jsonPath("$.newDocument.gst.supplyType").value("EXPORT_IGST"))
                .andExpect(jsonPath("$.newDocument.gst.lutReference").doesNotExist())
                .andExpect(jsonPath("$.newDocument.taxes[0].igst").value(180.0))
                .andExpect(jsonPath("$.newDocument.exchangeRate").value("83.25"));
    }

    @Test
    @DisplayName("a repeated export in dollars is issued at its template's rate to rupees")
    void repeatedExport() throws Exception {
        String template = JsonPath.read(
                body(usdExport(client("Acme Inc", ", \"stateCode\": \"96\""), "")), "$.newDocument._id");

        send(get("/api/v1/document/{id}", repeated(template)), "")
                .andExpect(jsonPath("$.document.state").value("issued"))
                .andExpect(jsonPath("$.document.exchangeRate").value("83.25"))
                .andExpect(jsonPath("$.document.exchangeBase").value("INR"))
                .andExpect(jsonPath("$.document.gst.supplyType").value("EXPORT_LUT"))
                .andExpect(jsonPath("$.document.gst.lutReference").value(LUT));
    }
}
