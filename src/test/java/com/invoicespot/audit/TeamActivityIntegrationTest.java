package com.invoicespot.audit;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.business.Business;
import com.invoicespot.business.BusinessRole;
import com.invoicespot.business.Permission;
import com.invoicespot.user.User;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class TeamActivityIntegrationTest extends IntegrationTest {

    @Test
    @DisplayName("owners and admins see who changed role or left their business, without addresses or devices")
    void teamActivity() throws Exception {
        User owner = createUser();
        Business business = businessOf(owner);
        String ownerSession = bearer(owner, business);
        User admin = createMember(business, BusinessRole.ADMIN);
        User member = createMember(business, BusinessRole.MEMBER);
        mockMvc.perform(patch("/api/v1/business/members/{id}", member.getId())
                        .header(HttpHeaders.AUTHORIZATION, ownerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": \"VIEWER\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/business/members/{id}", member.getId())
                        .header(HttpHeaders.AUTHORIZATION, ownerSession))
                .andExpect(status().isOk());
        User stranger = createUser();
        Business elsewhere = businessOf(stranger);
        User theirMember = createMember(elsewhere, BusinessRole.MEMBER);
        mockMvc.perform(delete("/api/v1/business/members/{id}", theirMember.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(stranger, elsewhere)))
                .andExpect(status().isOk());

        for (String session : List.of(ownerSession, bearer(admin, business))) {
            mockMvc.perform(get("/api/v1/business/activity").header(HttpHeaders.AUTHORIZATION, session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(2))
                    .andExpect(jsonPath("$.events[*].type", contains("MEMBER_REMOVED", "MEMBER_ROLE_CHANGED")))
                    .andExpect(jsonPath("$.events[*].actor", contains("Test User", "Test User")))
                    .andExpect(jsonPath("$.events[*].actorEmail", contains(owner.getEmail(), owner.getEmail())))
                    .andExpect(jsonPath("$.events[*].email", contains(member.getEmail(), member.getEmail())))
                    .andExpect(jsonPath("$.events[1].detail").value("Member → Viewer"))
                    .andExpect(jsonPath("$.events[0].ip").doesNotExist())
                    .andExpect(jsonPath("$.events[0].device").doesNotExist());
        }
        mockMvc.perform(get("/api/v1/business/activity")
                        .header(HttpHeaders.AUTHORIZATION, bearer(createMember(business, BusinessRole.MEMBER), business)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(Permission.VIEW_TEAM_ACTIVITY.refusal()));
    }

    @Test
    @DisplayName("the admin log names the business each team change happened in")
    void adminLogNamesTheBusiness() throws Exception {
        User owner = createUser();
        Business business = businessOf(owner);
        User member = createMember(business, BusinessRole.MEMBER);
        mockMvc.perform(delete("/api/v1/business/members/{id}", member.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner, business)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/audit")
                        .param("q", member.getEmail())
                        .param("type", "MEMBER_REMOVED")
                        .header(HttpHeaders.AUTHORIZATION, bearer(createAdmin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[*].business", contains(business.getName())));
    }
}
