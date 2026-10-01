package com.invoicespot.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// A document in another currency than the reporting one can keep the rate
// typed in for it, which is fixed once the document is issued; reports can
// then show everything in the reporting currency, and say what they leave out.
class ExchangeRateIntegrationTest extends IntegrationTest {

    private String session;
    private String client;

    private ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private void settings(String baseCurrency) throws Exception {
        send(put("/api/v1/settings"), """
                {"defaultCurrency": "USD", "baseCurrency": "%s", "paymentTermsDays": 15, "defaultTaxRate": 0,
                 "taxLabel": "Tax", "timeZone": "UTC"}
                """.formatted(baseCurrency))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.baseCurrency").value(baseCurrency));
    }

    @BeforeEach
    void setUp() throws Exception {
        session = bearer(createUser());
        settings("INR");
        client = JsonPath.read(
                body(send(post("/api/v1/customer/create"), """
                        {"name": "Beta LLC", "email": "%s@clients.test", "phoneNumber": "+1"}
                        """.formatted(UUID.randomUUID()))),
                "$.createdCustomer._id");
    }

    private ResultActions create(String type, String currency, String amount, String rate, boolean draft)
            throws Exception {
        return send(post("/api/v1/document/create"), """
                {"customerId": "%s", "documentType": "%s", "currency": "%s", "draft": %s, "exchangeRate": %s,
                 "billingItems": [{"itemName": "Work", "unitPrice": %s, "quantity": 1}]}
                """.formatted(client, type, currency, draft, rate == null ? "null" : "\"" + rate + "\"", amount));
    }

    private String id(ResultActions result) throws Exception {
        return JsonPath.read(body(result.andExpect(status().isOk())), "$.newDocument._id");
    }

    private ResultActions edit(String id, String json) throws Exception {
        return send(patch("/api/v1/document/{id}", id), json);
    }

    @Test
    @DisplayName("a draft keeps a rate to the reporting currency until its currency changes, and it is fixed once issued")
    void rates() throws Exception {
        String draft = id(create("Invoice", "EUR", "200", "90.50", true)
                .andExpect(jsonPath("$.newDocument.exchangeRate").value("90.5"))
                .andExpect(jsonPath("$.newDocument.exchangeBase").value("INR")));

        edit(draft, "{\"currency\": \"INR\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedDocument.exchangeRate").doesNotExist());
        edit(draft, "{\"currency\": \"EUR\", \"exchangeRate\": \"90.25\"}")
                .andExpect(jsonPath("$.updatedDocument.exchangeRate").value("90.25"));
        edit(draft, "{\"currency\": \"USD\"}").andExpect(jsonPath("$.updatedDocument.exchangeRate").doesNotExist());
        edit(draft, "{\"exchangeRate\": \"0\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Enter the exchange rate as a number greater than zero"));
        edit(draft, "{\"exchangeRate\": \"83.12345678901\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The exchange rate can have at most 10 decimals"));
        edit(draft, "{\"currency\": \"INR\", \"exchangeRate\": \"2\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A document in INR, your reporting currency, needs no exchange rate"));

        send(post("/api/v1/document/{id}/issue", draft), "{\"exchangeRate\": \"83.25\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedDocument.state").value("issued"))
                .andExpect(jsonPath("$.updatedDocument.exchangeRate").value("83.25"))
                .andExpect(jsonPath("$.updatedDocument.exchangeBase").value("INR"));
        edit(draft, "{\"exchangeRate\": \"84\"}").andExpect(status().isConflict());
        edit(draft, "{\"additionalInfo\": \"Thanks\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedDocument.exchangeRate").value("83.25"));

        // Issuing keeps the draft's rate unless it is given; a blank one removes it.
        String kept = id(create("Invoice", "USD", "100", "83", true));
        send(post("/api/v1/document/{id}/issue", kept), "{}")
                .andExpect(jsonPath("$.updatedDocument.exchangeRate").value("83"));
        String removed = id(create("Invoice", "USD", "100", "83", true));
        send(post("/api/v1/document/{id}/issue", removed), "{\"exchangeRate\": \"\"}")
                .andExpect(jsonPath("$.updatedDocument.exchangeRate").doesNotExist());

        assertThat(body(send(get("/api/v1/document/csv"), "")))
                .contains("Tax withheld,Exchange rate,Exchange rate to")
                .containsPattern(",83.25,INR,,0.00,0.00\\r?\\n");
    }

    private void pay(String id, String amount) throws Exception {
        send(post("/api/v1/document/{id}/payment", id), "{\"amountPaid\": %s}".formatted(amount))
                .andExpect(status().isCreated());
    }

    private ResultActions report(String view) throws Exception {
        return send(get("/api/v1/report").param("period", "12m").param("view", view), "");
    }

    @Test
    @DisplayName("the report in the reporting currency converts each amount at its document's rate, and lists the rest")
    void convertedReport() throws Exception {
        pay(id(create("Invoice", "INR", "1000", null, false)), "400");
        pay(id(create("Invoice", "USD", "100", "83.333", false)), "50");
        create("Receipt", "USD", "10", "83.333", false).andExpect(status().isOk());
        create("Invoice", "EUR", "200", null, false).andExpect(status().isOk());
        create("Invoice", "EUR", "999", "90", true).andExpect(status().isOk()); // a draft counts nowhere

        report("base")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.view").value("base"))
                .andExpect(jsonPath("$.baseCurrency").value("INR"))
                .andExpect(jsonPath("$.currencies.length()").value(1))
                .andExpect(jsonPath("$.currencies[0].currency").value("INR"))
                // 1000 + 100 x 83.333 + 10 x 83.333, each rounded to the cent
                .andExpect(jsonPath("$.currencies[0].billed").value(10166.63))
                .andExpect(jsonPath("$.currencies[0].received").value(5399.98))
                .andExpect(jsonPath("$.currencies[0].outstanding").value(4766.65))
                .andExpect(jsonPath("$.currencies[0].clients[0].billed").value(10166.63))
                .andExpect(jsonPath("$.currencies[0].clients[0].paid").value(5399.98))
                .andExpect(jsonPath("$.notConverted.length()").value(1))
                .andExpect(jsonPath("$.notConverted[0].currency").value("EUR"))
                .andExpect(jsonPath("$.notConverted[0].documents").value(1))
                .andExpect(jsonPath("$.notConverted[0].amount").value(200.0));
        report("currency")
                .andExpect(jsonPath("$.view").value("currency"))
                .andExpect(jsonPath("$.currencies.length()").value(3))
                .andExpect(jsonPath("$.currencies[0].currency").value("USD"))
                .andExpect(jsonPath("$.currencies[0].billed").value(110.0))
                .andExpect(jsonPath("$.notConverted.length()").value(0));
        assertThat(body(send(
                        get("/api/v1/report/csv/income").param("period", "12m").param("view", "base"), "")))
                .contains("INR,")
                .doesNotContain("USD,");
        report("everything").andExpect(status().isBadRequest());

        // Rates typed against INR don't convert to another reporting currency.
        settings("EUR");
        report("base")
                .andExpect(jsonPath("$.currencies[0].currency").value("EUR"))
                .andExpect(jsonPath("$.currencies[0].billed").value(200.0))
                .andExpect(jsonPath("$.notConverted.length()").value(2))
                .andExpect(jsonPath("$.notConverted[0].currency").value("INR"))
                .andExpect(jsonPath("$.notConverted[0].amount").value(1000.0))
                .andExpect(jsonPath("$.notConverted[1].currency").value("USD"))
                .andExpect(jsonPath("$.notConverted[1].documents").value(2))
                .andExpect(jsonPath("$.notConverted[1].amount").value(110.0));
    }

    private ResultActions defaultCurrency(String currency) throws Exception {
        return send(put("/api/v1/settings"), """
                {"defaultCurrency": "%s", "paymentTermsDays": 15, "defaultTaxRate": 0,
                 "taxLabel": "Tax", "timeZone": "UTC"}
                """.formatted(currency));
    }

    @Test
    @DisplayName("the reporting currency follows the default currency until it is set to another one")
    void followsDefault() throws Exception {
        session = bearer(createUser());
        defaultCurrency("GBP").andExpect(jsonPath("$.settings.baseCurrency").value("GBP"));
        defaultCurrency("EUR").andExpect(jsonPath("$.settings.baseCurrency").value("EUR"));
        settings("INR");
        defaultCurrency("GBP")
                .andExpect(jsonPath("$.settings.defaultCurrency").value("GBP"))
                .andExpect(jsonPath("$.settings.baseCurrency").value("INR"));
    }
}
