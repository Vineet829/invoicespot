package com.invoicespot.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.invoicespot.IntegrationTest;
import com.invoicespot.user.User;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class InvitationIntegrationTest extends IntegrationTest {

    private static final Pattern LINK = Pattern.compile("/invite/([A-Za-z0-9_-]{43})");

    @Autowired
    private InvitationRepository invitationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User founder;
    private Business business;
    private String owner;

    @BeforeEach
    void setUp() {
        founder = createUser();
        business = businessOf(founder);
        owner = bearer(founder, business);
        clearInvocations(mailSender);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String session, String json) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions invite(String session, String email, String role) throws Exception {
        return send(post("/api/v1/business/invitations"), session, """
                {"email": "%s", "role": "%s"}
                """.formatted(email, role));
    }

    // The link in the last invitation email.
    private String sentToken(String to) {
        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(2000).atLeastOnce()).send(mail.capture());
        SimpleMailMessage last = mail.getValue();
        assertThat(last.getTo()).containsExactly(to);
        Matcher link = LINK.matcher(last.getText());
        assertThat(link.find()).isTrue();
        return link.group(1);
    }

    private ResultActions preview(String token) throws Exception {
        return mockMvc.perform(get("/api/v1/public/invitations/" + token)
                .with(request -> {
                    request.setRemoteAddr("10.40." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250));
                    return request;
                }));
    }

    @Test
    @DisplayName("someone invited by email joins with the role they were given, and works in the business at once")
    void invitesAndAccepts() throws Exception {
        User invitee = createUser();
        invite(owner, invitee.getEmail().toUpperCase(), "MEMBER")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(invitee.getEmail()))
                .andExpect(jsonPath("$.role").value("MEMBER"));
        String token = sentToken(invitee.getEmail());

        preview(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName").value(business.getName()))
                .andExpect(jsonPath("$.invitedBy").value("Test"))
                .andExpect(jsonPath("$.status").value("PENDING"));
        send(get("/api/v1/business/invitations"), owner, "")
                .andExpect(jsonPath("$.invitations.length()").value(1));

        String session = bearer(invitee);
        send(post("/api/v1/invitations/" + token + "/accept"), session, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._id").value(business.getId().toString()))
                .andExpect(jsonPath("$.role").value("MEMBER"));
        send(get("/api/v1/business"), session, "")
                .andExpect(jsonPath("$.business._id").value(business.getId().toString()));
        preview(token).andExpect(jsonPath("$.status").value("ACCEPTED"));
        send(post("/api/v1/invitations/" + token + "/accept"), session, "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This invitation was already used or withdrawn"));
        send(get("/api/v1/business/invitations"), owner, "")
                .andExpect(jsonPath("$.invitations.length()").value(0));
        assertThat(jdbcTemplate.queryForList(
                        "select type || ': ' || detail from audit_events where business_pkid = ? order by pkid",
                        String.class,
                        business.getPkid()))
                .containsExactly("MEMBER_INVITED: Member", "MEMBER_JOINED: Member");
    }

    @Test
    @DisplayName("only the verified account the invitation was sent to can accept it")
    void onlyTheInvitee() throws Exception {
        invite(owner, "someone-" + UUID.randomUUID() + "@example.test", "VIEWER").andExpect(status().isCreated());
        String token = sentToken(invitationRepository.findWaiting(business.getPkid()).get(0).getEmail());

        send(post("/api/v1/invitations/" + token + "/accept"), bearer(createUser()), "")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("This invitation is for ")));
    }

    @Test
    @DisplayName("resending replaces the link; revoking or expiry closes it")
    void resendRevokeExpire() throws Exception {
        User invitee = createUser();
        String id = JsonPath.read(invite(owner, invitee.getEmail(), "ADMIN")
                .andReturn().getResponse().getContentAsString(), "$._id");
        String first = sentToken(invitee.getEmail());
        clearInvocations(mailSender);

        send(post("/api/v1/business/invitations/" + id + "/resend"), owner, "").andExpect(status().isOk());
        String second = sentToken(invitee.getEmail());
        preview(first).andExpect(status().isNotFound());
        preview(second).andExpect(jsonPath("$.status").value("PENDING"));

        send(delete("/api/v1/business/invitations/" + id), owner, "").andExpect(status().isOk());
        preview(second).andExpect(jsonPath("$.status").value("REVOKED"));
        send(post("/api/v1/invitations/" + second + "/accept"), bearer(invitee), "")
                .andExpect(status().isConflict());

        clearInvocations(mailSender);
        invite(owner, invitee.getEmail(), "VIEWER").andExpect(status().isCreated());
        String third = sentToken(invitee.getEmail());
        Invitation waiting = invitationRepository.findWaiting(business.getPkid()).get(0);
        waiting.setExpiresAt(Instant.now().minusSeconds(60));
        invitationRepository.save(waiting);
        preview(third).andExpect(jsonPath("$.status").value("EXPIRED"));
        send(post("/api/v1/invitations/" + third + "/accept"), bearer(invitee), "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This invitation has expired. Ask for a new one"));
        invite(owner, invitee.getEmail(), "VIEWER").andExpect(status().isCreated());
    }

    @Test
    @DisplayName("invitations are refused for members, the demo account, owners, and people who can't manage the team")
    void refusals() throws Exception {
        User member = createMember(business, BusinessRole.MEMBER);
        String email = "twice-" + UUID.randomUUID() + "@example.test";
        invite(owner, email, "MEMBER").andExpect(status().isCreated());

        invite(owner, email, "VIEWER")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("They already have an invitation. Resend it instead"));
        invite(owner, member.getEmail(), "VIEWER")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("They're already part of this business"));
        invite(owner, DEMO_EMAIL, "VIEWER")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("The shared demo account can't be invited"));
        invite(owner, "boss-" + UUID.randomUUID() + "@example.test", "OWNER")
                .andExpect(status().isBadRequest());
        invite(bearer(member, business), "other@example.test", "VIEWER")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(Permission.MANAGE_TEAM.refusal()));
        User demo = demoUser();
        invite(bearer(demo, businessOf(demo)), "friend@example.test", "VIEWER")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value("This is a shared demo account, so inviting people is turned off"));
    }

    @Test
    @DisplayName("a business sends at most twenty invitations an hour")
    void rateLimited() throws Exception {
        for (int sent = 0; sent < 20; sent++) {
            invite(owner, "many-" + UUID.randomUUID() + "@example.test", "VIEWER").andExpect(status().isCreated());
        }
        invite(owner, "one-more-" + UUID.randomUUID() + "@example.test", "VIEWER")
                .andExpect(status().isTooManyRequests());
    }
}
