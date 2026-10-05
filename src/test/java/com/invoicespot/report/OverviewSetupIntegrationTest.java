package com.invoicespot.report;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.business.Business;
import com.invoicespot.business.BusinessRole;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// What is left to set up, on the overview of those who may change the
// business's details and settings: an address and a tax ID, a logo, a way for
// clients to pay, a first issued invoice, and someone else in the team.
class OverviewSetupIntegrationTest extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User owner;
    private Business business;
    private String client;

    @BeforeEach
    void owner() throws Exception {
        owner = createUser();
        business = businessOf(owner);
        client = JsonPath.read(
                body(send(post("/api/v1/customer/create"), """
                        {"name": "Acme", "email": "%s@clients.test", "phoneNumber": "+1"}
                        """.formatted(UUID.randomUUID()))),
                "$.createdCustomer._id");
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    // The overview as someone working in the business sees it.
    private ResultActions overview(User user) throws Exception {
        return mockMvc.perform(get("/api/v1/report/overview").header(HttpHeaders.AUTHORIZATION, bearer(user, business)))
                .andExpect(status().isOk());
    }

    private void details(String json) throws Exception {
        send(patch("/api/v1/business"), json).andExpect(status().isOk());
    }

    private void paymentDetails(String json) throws Exception {
        send(put("/api/v1/settings/payment-details"), json).andExpect(status().isOk());
    }

    // A document of the kind for the client, dated today; a draft is only
    // planned for it.
    private String document(String type, boolean draft) throws Exception {
        return JsonPath.read(
                body(send(post("/api/v1/document/create"), """
                                {"customerId": "%s", "documentType": "%s", "currency": "USD", "draft": %s,
                                 "billingItems": [{"itemName": "Work", "unitPrice": 100, "quantity": 1}]}
                                """.formatted(client, type, draft))
                        .andExpect(status().isOk())),
                "$.newDocument._id");
    }

    // The steps in order, as the owner sees them, each done or not.
    private void steps(Boolean... done) throws Exception {
        overview(owner)
                .andExpect(jsonPath(
                        "$.setup.steps[*].key",
                        contains("businessDetails", "logo", "paymentDetails", "firstInvoice", "team")))
                .andExpect(jsonPath("$.setup.steps[*].done", contains(done)));
    }

    @Test
    @DisplayName("an owner sees each step done once the business has it")
    void eachStep() throws Exception {
        steps(false, false, false, false, false);

        // An address alone isn't enough, nor a tax ID of nothing but spaces.
        details("{\"address\": \"1 Main Street\"}");
        jdbcTemplate.update("update businesses set tax_id = '  ' where pkid = ?", business.getPkid());
        steps(false, false, false, false, false);
        details("{\"taxId\": \"TX-1234\"}");
        steps(true, false, false, false, false);

        details("{\"logo\": \"/uploads/logo.png\"}");
        steps(true, true, false, false, false);

        // A bank's name isn't a way to pay; an account number is.
        paymentDetails("{\"accountHolder\": \"Studio\", \"bankName\": \"First Bank\"}");
        steps(true, true, false, false, false);
        paymentDetails("""
                {"accountHolder": "Studio", "bankName": "First Bank", "accountNumber": "1234 5678"}
                """);
        steps(true, true, true, false, false);

        // Neither a draft, a quotation, a receipt nor a void invoice is a first invoice.
        document("Invoice", true);
        document("Quotation", false);
        document("Receipt", false);
        send(post("/api/v1/document/{id}/void", document("Invoice", false)), "{\"reason\": \"Issued twice\"}")
                .andExpect(status().isOk());
        steps(true, true, true, false, false);
        document("Invoice", false);
        steps(true, true, true, true, false);

        createMember(business, BusinessRole.VIEWER);
        steps(true, true, true, true, true);
    }

    @Test
    @DisplayName("clients have a way to pay with a bank account's number or IBAN, a UPI ID or a payment link")
    void waysToPay() throws Exception {
        Map<String, Boolean> details = Map.of(
                "{\"upiId\": \"studio@okhdfcbank\"}", true,
                "{\"payLink\": \"https://pay.example.com/studio\"}", true,
                "{\"bankCodeType\": \"IBAN\", \"bankCode\": \"GB82 WEST 1234 5698 7654 32\"}", true,
                "{\"bankCodeType\": \"SWIFT\", \"bankCode\": \"HDFCINBB\"}", false,
                "{\"instructions\": \"Pay by cheque to the studio\"}", false,
                "{}", false);
        for (Map.Entry<String, Boolean> way : details.entrySet()) {
            paymentDetails(way.getKey());
            overview(owner)
                    .andExpect(jsonPath("$.setup.steps[2].key").value("paymentDetails"))
                    .andExpect(jsonPath("$.setup.steps[2].done").value(way.getValue()));
        }
    }

    @Test
    @DisplayName("admins see what is left to set up as the owner does; members and viewers see none")
    void whoSees() throws Exception {
        User admin = createMember(business, BusinessRole.ADMIN);
        User member = createMember(business, BusinessRole.MEMBER);
        User viewer = createMember(business, BusinessRole.VIEWER);

        overview(admin).andExpect(jsonPath("$.setup.steps[*].done", contains(false, false, false, false, true)));
        overview(member).andExpect(content().string(containsString("\"setup\":null")));
        overview(viewer).andExpect(content().string(containsString("\"setup\":null")));
    }
}
