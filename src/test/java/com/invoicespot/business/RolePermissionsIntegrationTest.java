package com.invoicespot.business;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class RolePermissionsIntegrationTest extends IntegrationTest {

    private static final String SETTINGS = """
            {"defaultCurrency": "INR", "paymentTermsDays": 15, "defaultTaxRate": 18, "taxLabel": "GST",
             "timeZone": "Asia/Kolkata"}
            """;

    private Business business;
    private String owner;
    private String clientId;

    private ResultActions send(MockHttpServletRequestBuilder request, String session, String json) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private String as(BusinessRole role) {
        User member = createMember(business, role);
        return bearer(member, business);
    }

    private String client(String session) throws Exception {
        String body = send(post("/api/v1/customer/create"), session, """
                        {"name": "Acme", "email": "acme-%s@clients.test", "phoneNumber": "555 0100"}
                        """.formatted(UUID.randomUUID()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.createdCustomer._id");
    }

    private String document() throws Exception {
        return document(false);
    }

    private String document(boolean draft) throws Exception {
        String body = send(post("/api/v1/document/create"), owner, """
                        {"customerId": "%s", "draft": %s,
                         "billingItems": [{"itemName": "Design", "unitPrice": 100, "quantity": 1}]}
                        """.formatted(clientId, draft))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.newDocument._id");
    }

    @BeforeEach
    void setUp() throws Exception {
        User founder = createUser();
        business = businessOf(founder);
        owner = bearer(founder, business);
        clientId = client(owner);
    }

    private static ResultActions refused(ResultActions result, Permission permission) throws Exception {
        return result.andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value(permission.refusal()));
    }

    @Test
    @DisplayName("a viewer reads and downloads the business's records, and changes nothing")
    void viewer() throws Exception {
        String viewer = as(BusinessRole.VIEWER);
        String document = document();

        send(get("/api/v1/document/" + document), viewer, "").andExpect(status().isOk());
        send(get("/api/v1/document/csv"), viewer, "").andExpect(status().isOk());
        send(get("/api/v1/report"), viewer, "").andExpect(status().isOk());
        send(post("/api/v1/notification/read"), viewer, "{}").andExpect(status().isOk());
        refused(send(post("/api/v1/customer/create"), viewer, """
                {"name": "Beta", "email": "beta@clients.test", "phoneNumber": "555"}
                """), Permission.EDIT_RECORDS);
        refused(send(patch("/api/v1/document/" + document), viewer, "{\"additionalInfo\": \"Hi\"}"),
                Permission.EDIT_RECORDS);
        refused(send(post("/api/v1/document/" + document + "/payment"), viewer, "{\"amountPaid\": 10}"),
                Permission.EDIT_RECORDS);
        refused(send(delete("/api/v1/document/" + document), viewer, ""), Permission.DELETE_RECORDS);
        refused(send(put("/api/v1/settings"), viewer, SETTINGS), Permission.MANAGE_BUSINESS);
        refused(send(patch("/api/v1/business"), viewer, "{\"name\": \"Renamed\"}"), Permission.MANAGE_BUSINESS);
        refused(send(get("/api/v1/export/all"), viewer, ""), Permission.EXPORT_ARCHIVE);
    }

    @Test
    @DisplayName("a viewer sees that a share link is on and how it is used, but not its address or an email with it")
    void viewerAndLinks() throws Exception {
        String viewer = as(BusinessRole.VIEWER);
        String member = as(BusinessRole.MEMBER);
        String document = document();
        send(post("/api/v1/document/" + document + "/share"), member, "").andExpect(status().isCreated());

        send(get("/api/v1/document/" + document + "/share"), viewer, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.url").doesNotExist());
        send(get("/api/v1/document/" + document + "/share"), member, "")
                .andExpect(jsonPath("$.url").isString());
        refused(send(get("/api/v1/document/" + document + "/email"), viewer, ""), Permission.EDIT_RECORDS);
        send(get("/api/v1/document/" + document + "/email"), member, "").andExpect(status().isOk());
    }

    @Test
    @DisplayName("a member works on records, but can't delete them, change settings or download everything")
    void member() throws Exception {
        String member = as(BusinessRole.MEMBER);
        String document = document();

        client(member);
        send(patch("/api/v1/document/" + document), member, "{\"additionalInfo\": \"Hi\"}")
                .andExpect(status().isOk());
        send(post("/api/v1/document/" + document + "/payment"), member, "{\"amountPaid\": 10}")
                .andExpect(status().isCreated());
        refused(send(delete("/api/v1/document/" + document), member, ""), Permission.DELETE_RECORDS);
        refused(send(delete("/api/v1/customer/" + clientId), member, ""), Permission.DELETE_RECORDS);
        // Nor payments, refunds, expenses, items or recurring schedules: the role is
        // refused before anything is looked up.
        for (String path : List.of(
                "/api/v1/document/" + document + "/payment/" + UUID.randomUUID(),
                "/api/v1/document/" + document + "/refund/" + UUID.randomUUID(),
                "/api/v1/expense/" + UUID.randomUUID(),
                "/api/v1/item/" + UUID.randomUUID(),
                "/api/v1/recurring/" + UUID.randomUUID())) {
            refused(send(delete(path), member, ""), Permission.DELETE_RECORDS);
            send(delete(path), as(BusinessRole.ADMIN), "").andExpect(status().isNotFound());
        }
        refused(send(put("/api/v1/settings"), member, SETTINGS), Permission.MANAGE_BUSINESS);
        refused(send(patch("/api/v1/business"), member, "{\"name\": \"Renamed\"}"), Permission.MANAGE_BUSINESS);
        refused(send(get("/api/v1/export/all"), member, ""), Permission.EXPORT_ARCHIVE);
    }

    @Test
    @DisplayName("an admin deletes drafts and clients, voids documents, changes settings and downloads everything")
    void admin() throws Exception {
        String admin = as(BusinessRole.ADMIN);

        send(delete("/api/v1/document/" + document(true)), admin, "").andExpect(status().isOk());
        send(post("/api/v1/document/" + document() + "/void"), admin, "{\"reason\": \"Wrong client\"}")
                .andExpect(status().isOk());
        send(put("/api/v1/settings"), admin, SETTINGS).andExpect(status().isOk());
        send(patch("/api/v1/business"), admin, "{\"name\": \"Renamed\"}").andExpect(status().isOk());
        send(get("/api/v1/export/all"), admin, "").andExpect(status().isOk());
        send(delete("/api/v1/customer/" + clientId), admin, "").andExpect(status().isOk());
    }

    @Test
    @DisplayName("each business lists what the member's role allows there")
    void permissionsListed() throws Exception {
        send(get("/api/v1/businesses"), as(BusinessRole.VIEWER), "")
                .andExpect(jsonPath("$.businesses[?(@.role == 'VIEWER')].permissions[*]")
                        .value(containsInAnyOrder("VIEW_RECORDS", "EXPORT_LISTS")));
    }
}
