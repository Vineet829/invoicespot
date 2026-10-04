package com.invoicespot.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.audit.AuditEventRepository;
import com.invoicespot.audit.AuditEventType;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class TeamIntegrationTest extends IntegrationTest {

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private TeamService teamService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private User founder;
    private Business business;
    private String owner;

    @BeforeEach
    void setUp() {
        founder = createUser();
        business = businessOf(founder);
        owner = bearer(founder, business);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String session, String json) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private List<AuditEventType> businessEvents() {
        return auditEventRepository.findAll().stream()
                .filter(event -> event.getBusiness() != null && event.getBusiness().getPkid().equals(business.getPkid()))
                .map(event -> event.getType())
                .toList();
    }

    @Test
    @DisplayName("the team lists the owner first, then by role, marking the viewer")
    void lists() throws Exception {
        User viewer = createMember(business, BusinessRole.VIEWER);
        User admin = createMember(business, BusinessRole.ADMIN);

        send(get("/api/v1/business/members"), bearer(viewer, business), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[0]._id").value(founder.getId().toString()))
                .andExpect(jsonPath("$.members[0].role").value("OWNER"))
                .andExpect(jsonPath("$.members[1]._id").value(admin.getId().toString()))
                .andExpect(jsonPath("$.members[2].you").value(true))
                .andExpect(jsonPath("$.members[2].role").value("VIEWER"));
    }

    @Test
    @DisplayName("owners and admins change other members' roles, never their own or the owner's")
    void roles() throws Exception {
        User member = createMember(business, BusinessRole.MEMBER);
        User admin = createMember(business, BusinessRole.ADMIN);
        String adminSession = bearer(admin, business);

        send(patch("/api/v1/business/members/" + member.getId()), adminSession, "{\"role\": \"VIEWER\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("VIEWER"));
        send(patch("/api/v1/business/members/" + admin.getId()), adminSession, "{\"role\": \"MEMBER\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You can't change your own role"));
        send(patch("/api/v1/business/members/" + founder.getId()), adminSession, "{\"role\": \"MEMBER\"}")
                .andExpect(status().isConflict());
        send(patch("/api/v1/business/members/" + member.getId()), owner, "{\"role\": \"OWNER\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Hand the business over to make someone its owner"));
        send(patch("/api/v1/business/members/" + member.getId()), owner, "{\"role\": \"VIEWER\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("They already have that role"));
        send(patch("/api/v1/business/members/" + admin.getId()), bearer(member, business), "{\"role\": \"VIEWER\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(Permission.MANAGE_TEAM.refusal()));
        assertThat(businessEvents()).containsExactly(AuditEventType.MEMBER_ROLE_CHANGED);
    }

    @Test
    @DisplayName("a removed member loses the business at once, and a member can leave, but the owner can't")
    void removesAndLeaves() throws Exception {
        User viewer = createMember(business, BusinessRole.VIEWER);
        String viewerSession = bearer(viewer, business);
        User member = createMember(business, BusinessRole.MEMBER);
        String memberSession = bearer(member, business);

        send(delete("/api/v1/business/members/" + viewer.getId()), owner, "").andExpect(status().isOk());
        send(get("/api/v1/business"), viewerSession, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.business._id").value(businessOf(viewer).getId().toString()));

        send(post("/api/v1/business/leave"), memberSession, "").andExpect(status().isOk());
        send(get("/api/v1/business/members"), owner, "")
                .andExpect(jsonPath("$.members.length()").value(1));
        send(post("/api/v1/business/leave"), owner, "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Hand the business over to someone else, or delete it, before you leave"));
        send(delete("/api/v1/business/members/" + founder.getId()), owner, "")
                .andExpect(status().isConflict());
        send(delete("/api/v1/business/members/" + UUID.randomUUID()), owner, "")
                .andExpect(status().isNotFound());
        assertThat(businessEvents()).containsExactly(AuditEventType.MEMBER_REMOVED, AuditEventType.MEMBER_LEFT);
    }

    @Test
    @DisplayName("the owner hands the business over and stays on as an admin; the new owner is told")
    void transfers() throws Exception {
        User admin = createMember(business, BusinessRole.ADMIN);

        send(post("/api/v1/business/transfer"), bearer(admin, business), "{\"userId\": \"" + founder.getId() + "\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(Permission.OWN_BUSINESS.refusal()));
        send(post("/api/v1/business/transfer"), owner, "{\"userId\": \"" + admin.getId() + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("OWNER"));

        send(get("/api/v1/business/members"), owner, "")
                .andExpect(jsonPath("$.members[0]._id").value(admin.getId().toString()))
                .andExpect(jsonPath("$.members[1].role").value("ADMIN"));
        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(2000)).send(mail.capture());
        assertThat(mail.getValue().getTo()).containsExactly(admin.getEmail());
        assertThat(mail.getValue().getSubject()).isEqualTo("You now own " + business.getName());
        assertThat(businessEvents()).containsExactly(AuditEventType.OWNERSHIP_TRANSFERRED);
    }

    @Test
    @DisplayName("a hand-over that rolls back emails nobody, and the owner keeps the business")
    void transferRolledBack() {
        User admin = createMember(business, BusinessRole.ADMIN);
        BusinessAccess access = new BusinessAccess(business.getPkid(), business.getId(), BusinessRole.OWNER, founder);

        // As when saving it fails at the very end.
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            teamService.transfer(access, admin.getId().toString());
            transaction.setRollbackOnly();
        });

        verify(mailSender, after(1000).never()).send(any(SimpleMailMessage.class));
        assertThat(businessMemberRepository.findByBusinessPkidAndUserPkid(business.getPkid(), founder.getPkid())
                        .orElseThrow()
                        .getRole())
                .isEqualTo(BusinessRole.OWNER);
    }

    @Test
    @DisplayName("an inactive account can't take a business over")
    void transferNeedsAnActiveAccount() throws Exception {
        User admin = createMember(business, BusinessRole.ADMIN);
        admin.setActive(false);
        userRepository.save(admin);

        send(post("/api/v1/business/transfer"), owner, "{\"userId\": \"" + admin.getId() + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Only someone with an active, verified account can own a business"));
    }

    @Test
    @DisplayName("only the owner deletes a business, with everything in it, and its people carry on elsewhere")
    void deletes() throws Exception {
        User admin = createMember(business, BusinessRole.ADMIN);
        String adminSession = bearer(admin, business);
        String body = send(post("/api/v1/customer/create"), owner, """
                        {"name": "Acme", "email": "acme-%s@clients.test", "phoneNumber": "555 0100"}
                        """.formatted(UUID.randomUUID()))
                .andReturn().getResponse().getContentAsString();
        String client = JsonPath.read(body, "$.createdCustomer._id");

        send(delete("/api/v1/business"), adminSession, "")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(Permission.OWN_BUSINESS.refusal()));
        send(delete("/api/v1/business"), owner, "").andExpect(status().isOk());

        send(get("/api/v1/business"), owner, "")
                .andExpect(status().isConflict())
                .andExpect(header().string(BusinessChangedException.HEADER, "none"));
        send(get("/api/v1/business"), adminSession, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.business._id").value(businessOf(admin).getId().toString()));
        send(get("/api/v1/customer/" + client), adminSession, "").andExpect(status().isNotFound());
        // Other tests delete businesses with the same name, so only the
        // owner's own deletion counts.
        assertThat(auditEventRepository.findAll().stream()
                        .filter(event -> event.getType() == AuditEventType.BUSINESS_DELETED
                                && event.getActor() != null
                                && event.getActor().getPkid().equals(founder.getPkid()))
                        .map(event -> event.getDetail())
                        .toList())
                .containsExactly(business.getName());
    }

    @Test
    @DisplayName("the demo account can't delete its business")
    void demo() throws Exception {
        User demo = demoUser();
        send(delete("/api/v1/business"), bearer(demo, businessOf(demo)), "")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value("This is a shared demo account, so deleting the business is turned off"));
    }
}
