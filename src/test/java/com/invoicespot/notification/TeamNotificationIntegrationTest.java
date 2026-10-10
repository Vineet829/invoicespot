package com.invoicespot.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.business.Business;
import com.invoicespot.business.BusinessRole;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

// News about a document in a business with a team: who hears of it, and how
// much waits unread in each business.
class TeamNotificationIntegrationTest extends IntegrationTest {

    @Autowired
    private NotificationRepository notificationRepository;

    private User owner;
    private Business business;

    @BeforeEach
    void setUp() {
        owner = createUser();
        business = businessOf(owner);
    }

    private String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    // A quotation made in the session's business and shared by link. The
    // link's token is returned.
    private String sharedQuotation(String session) throws Exception {
        String customer = body(mockMvc.perform(post("/api/v1/customer/create")
                .header(HttpHeaders.AUTHORIZATION, session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Beta LLC\", \"email\": \"%s\", \"phoneNumber\": \"+1\"}"
                        .formatted("beta-" + UUID.randomUUID() + "@clients.test"))));
        String quotation = JsonPath.read(
                body(mockMvc.perform(post("/api/v1/document/create")
                        .header(HttpHeaders.AUTHORIZATION, session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "%s", "documentType": "Quotation",
                                 "billingItems": [{"itemName": "Work", "unitPrice": 100, "quantity": 1}]}
                                """.formatted((String) JsonPath.read(customer, "$.createdCustomer._id"))))),
                "$.newDocument._id");
        String url = JsonPath.read(
                body(mockMvc.perform(post("/api/v1/document/{id}/share", quotation)
                        .header(HttpHeaders.AUTHORIZATION, session))),
                "$.url");
        return url.substring(url.lastIndexOf('/') + 1);
    }

    private void clientViews(String token) throws Exception {
        mockMvc.perform(get("/api/v1/public/share/{token}", token)).andExpect(status().isOk());
    }

    private ResultActions latest(User user) throws Exception {
        return mockMvc.perform(get("/api/v1/notification").header(HttpHeaders.AUTHORIZATION, bearer(user, business)));
    }

    @Test
    @DisplayName("news of a document reaches the owner, the admins and its author, each with a copy of their own")
    void recipients() throws Exception {
        User admin = createMember(business, BusinessRole.ADMIN);
        User author = createMember(business, BusinessRole.MEMBER);
        User colleague = createMember(business, BusinessRole.MEMBER);
        User viewer = createMember(business, BusinessRole.VIEWER);

        clientViews(sharedQuotation(bearer(author, business)));

        for (User told : List.of(owner, admin, author)) {
            latest(told)
                    .andExpect(jsonPath("$.unread").value(1))
                    .andExpect(jsonPath("$.notifications[*].type", contains("DOCUMENT_VIEWED")));
        }
        for (User untold : List.of(colleague, viewer)) {
            latest(untold).andExpect(jsonPath("$.unread").value(0));
        }
        mockMvc.perform(post("/api/v1/notification/read")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner, business))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        latest(owner).andExpect(jsonPath("$.unread").value(0));
        latest(admin).andExpect(jsonPath("$.unread").value(1));
    }

    @Test
    @DisplayName("an author who has left the business hears no more of their documents")
    void formerAuthor() throws Exception {
        User author = createMember(business, BusinessRole.MEMBER);
        String token = sharedQuotation(bearer(author, business));
        mockMvc.perform(delete("/api/v1/business/members/{id}", author.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner, business)))
                .andExpect(status().isOk());

        clientViews(token);

        latest(owner).andExpect(jsonPath("$.unread").value(1));
        assertThat(notificationRepository.countByUserPkidAndBusinessPkidAndReadAtIsNull(
                        author.getPkid(), business.getPkid()))
                .isZero();
    }

    @Test
    @DisplayName("the list of businesses says how many notifications wait unread in each")
    void unreadPerBusiness() throws Exception {
        User member = createMember(business, BusinessRole.MEMBER);
        clientViews(sharedQuotation(bearer(member, business)));

        mockMvc.perform(get("/api/v1/businesses")
                        .header(HttpHeaders.AUTHORIZATION, bearer(member, businessOf(member))))
                .andExpect(jsonPath("$.businesses[?(@.active == true)].unread", contains(0)))
                .andExpect(jsonPath("$.businesses[?(@._id == '%s')].unread".formatted(business.getId()), contains(1)));
        mockMvc.perform(put("/api/v1/businesses/active")
                        .header(HttpHeaders.AUTHORIZATION, bearer(member, businessOf(member)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"businessId\": \"%s\"}".formatted(business.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(1));
    }
}
