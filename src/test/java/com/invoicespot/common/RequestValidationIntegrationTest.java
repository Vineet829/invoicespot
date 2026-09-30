package com.invoicespot.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class RequestValidationIntegrationTest extends IntegrationTest {

    private ResultActions send(
            MockHttpServletRequestBuilder request, User user, String json)
            throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private String customerId(User owner) throws Exception {
        String body = send(post("/api/v1/customer/create"), owner, """
                        {"name": "Valid", "email": "valid-%s@clients.test", "phoneNumber": "+1 555 0100"}
                        """.formatted(UUID.randomUUID()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.createdCustomer._id");
    }

    @Test
    @DisplayName("client fields longer than their columns, or malformed emails, are refused")
    void customerFields() throws Exception {
        User owner = createUser();

        send(post("/api/v1/customer/create"), owner, """
                        {"name": "%s", "email": "long@clients.test", "phoneNumber": "+1"}
                        """.formatted("n".repeat(256)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Customer names can be at most 255 characters"));
        send(post("/api/v1/customer/create"), owner, """
                        {"name": "Bad", "email": "not-an-email", "phoneNumber": "+1"}
                        """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Enter a valid email address"));
    }

    @Test
    @DisplayName("document lines can't have negative prices or quantities, and currencies are short codes")
    void documentFields() throws Exception {
        User owner = createUser();
        String customerId = customerId(owner);

        send(post("/api/v1/document/create"), owner, """
                        {"customerId": "%s", "billingItems": [{"itemName": "X", "unitPrice": -1, "quantity": 1}]}
                        """.formatted(customerId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unit prices can't be negative"));
        send(post("/api/v1/document/create"), owner, """
                        {"customerId": "%s", "billingItems": [{"itemName": "X", "unitPrice": 1, "quantity": -2}]}
                        """.formatted(customerId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Quantities can't be negative"));
        send(post("/api/v1/document/create"), owner, """
                        {"customerId": "%s", "currency": "US DOLLARS", "billingItems": []}
                        """.formatted(customerId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Currency must be a three-letter code such as USD"));
    }

    @Test
    @DisplayName("profile fields longer than their columns are refused")
    void profileFields() throws Exception {
        send(patch("/api/v1/user/profile"), createUser(), """
                        {"phoneNumber": "%s"}
                        """.formatted("9".repeat(31)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Phone numbers can be at most 30 characters"));
    }
}
