package com.invoicespot.auth.oauth;

import com.invoicespot.audit.AuditEventType;
import com.invoicespot.audit.AuditLog;
import com.invoicespot.auth.VerificationTokenService;
import com.invoicespot.auth.session.SessionService;
import com.invoicespot.business.BusinessService;
import com.invoicespot.config.AppProperties;
import com.invoicespot.mail.MailService;
import com.invoicespot.user.DemoAccountGuard;
import com.invoicespot.user.User;
import com.invoicespot.user.UserIdentity;
import com.invoicespot.user.UserIdentityRepository;
import com.invoicespot.user.UserRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// The account a provider's account signs in to: the one it is linked to, or
// else one the provider has vouched for the email of, linked now or made new.
@Service
public class OAuthAccounts {

    private static final int USERNAME_LENGTH = 60;
    private static final int NAME_LENGTH = 60;
    private static final String SUFFIX_LETTERS = "abcdefghijkmnpqrstuvwxyz23456789";

    private final UserRepository userRepository;
    private final UserIdentityRepository identities;
    private final BusinessService businessService;
    private final SessionService sessionService;
    private final VerificationTokenService tokenService;
    private final DemoAccountGuard demoAccountGuard;
    private final AuditLog auditLog;
    private final MailService mailService;
    private final AppProperties.Site site;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public OAuthAccounts(
            UserRepository userRepository,
            UserIdentityRepository identities,
            BusinessService businessService,
            SessionService sessionService,
            VerificationTokenService tokenService,
            DemoAccountGuard demoAccountGuard,
            AuditLog auditLog,
            MailService mailService,
            AppProperties properties,
            Clock clock) {
        this.userRepository = userRepository;
        this.identities = identities;
        this.businessService = businessService;
        this.sessionService = sessionService;
        this.tokenService = tokenService;
        this.demoAccountGuard = demoAccountGuard;
        this.auditLog = auditLog;
        this.mailService = mailService;
        this.site = properties.site();
        this.clock = clock;
    }

    // The identity's key. It commits on its own, before the sign-in: the log
    // writes on its own too, and has to find an account made here.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long identityFor(ProviderProfile profile, String via) {
        return identities.findByProviderAndSubject(profile.provider().id(), profile.subject())
                .orElseGet(() -> linkOrCreate(profile, via))
                .getPkid();
    }

    private UserIdentity linkOrCreate(ProviderProfile profile, String via) {
        if (profile.email() == null || !profile.emailVerified()) {
            throw new OAuthSignInException("unverified_email");
        }
        String email = profile.email().trim().toLowerCase(Locale.ROOT);
        if (demoAccountGuard.isDemoEmail(email)) {
            throw new OAuthSignInException("demo_account");
        }
        return userRepository.findByEmailIgnoreCase(email)
                .map(user -> link(user, profile, email, via))
                .orElseGet(() -> create(profile, email));
    }

    // The account with the email the provider vouched for. One nobody ever
    // verified may have been registered by someone else, with a password of
    // their own: that password, its sessions and its email links stop
    // working, and only the provider's account opens it from now on.
    private UserIdentity link(User user, ProviderProfile profile, String email, String via) {
        if (!user.isActive()) {
            auditLog.signInFailed(email, user, via + ": account deactivated");
            throw new OAuthSignInException("deactivated");
        }
        if (identities.existsByUserAndProvider(user, profile.provider().id())) {
            throw new OAuthSignInException("already_linked");
        }
        if (!user.isEmailVerified()) {
            user.setEmailVerified(true);
            user.setPassword(null);
            user.setPasswordChangedAt(clock.instant());
            sessionService.revokeAll(user);
            tokenService.revokeAll(user);
        }
        UserIdentity identity = identities.save(identity(user, profile, email));
        String provider = profile.provider().label();
        auditLog.record(AuditEventType.PROVIDER_LINKED, user, user, null, provider);
        // A new password wouldn't remove the link, and the app has no way to,
        // so the advice is to reply: the mail comes from the support address.
        mailService.send(
                user.getEmail(),
                provider + " sign-in was added to your " + site.name() + " account",
                "Hello " + user.getFirstName() + ",\n\n"
                        + "Your " + site.name() + " account can now be signed in to with the " + provider
                        + " account " + email + ". Whoever holds that " + provider
                        + " account can sign in to yours.\n\n"
                        + "If that wasn't you, reply to this email right away.");
        return identity;
    }

    // A new account, verified by the provider, with no password and a
    // business of its own.
    private UserIdentity create(ProviderProfile profile, String email) {
        String handle = profile.handle() == null ? "" : profile.handle();
        User user = new User();
        user.setEmail(email);
        user.setUsername(username(handle));
        user.setFirstName(name(profile.firstName() == null ? handle : profile.firstName()));
        user.setLastName(name(profile.lastName()));
        user.setEmailVerified(true);
        user.setProvider(profile.provider().id());
        userRepository.save(user);
        businessService.createPersonal(user);
        return identities.save(identity(user, profile, email));
    }

    private UserIdentity identity(User user, ProviderProfile profile, String email) {
        UserIdentity identity = new UserIdentity();
        identity.setUser(user);
        identity.setProvider(profile.provider().id());
        identity.setSubject(profile.subject());
        identity.setEmail(email);
        identity.setLinkedAt(clock.instant());
        return identity;
    }

    // The handle with only letters, digits, dots, dashes and underscores,
    // made unique with a short suffix when it is taken.
    private String username(String handle) {
        String base = handle.replaceAll("[^A-Za-z0-9._-]", "");
        base = base.isEmpty() ? "user" : base.substring(0, Math.min(base.length(), USERNAME_LENGTH - 5));
        String candidate = base;
        while (userRepository.existsByUsername(candidate)) {
            candidate = base + "-" + suffix();
        }
        return candidate;
    }

    private String suffix() {
        StringBuilder suffix = new StringBuilder();
        for (int letter = 0; letter < 4; letter++) {
            suffix.append(SUFFIX_LETTERS.charAt(random.nextInt(SUFFIX_LETTERS.length())));
        }
        return suffix.toString();
    }

    private static String name(String value) {
        String name = value == null ? "" : value.trim();
        return name.length() > NAME_LENGTH ? name.substring(0, NAME_LENGTH) : name;
    }
}
