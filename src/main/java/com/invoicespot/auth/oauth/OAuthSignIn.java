package com.invoicespot.auth.oauth;

import com.invoicespot.audit.AuditLog;
import com.invoicespot.auth.AuthService;
import com.invoicespot.auth.JwtService;
import com.invoicespot.auth.LoginOutcome;
import com.invoicespot.user.DemoAccountGuard;
import com.invoicespot.user.User;
import com.invoicespot.user.UserIdentity;
import com.invoicespot.user.UserIdentityRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// Signs in with a provider's account, by these rules in order. An account at
// the provider already linked opens its account here, whatever its email is
// now. Otherwise the provider has to vouch for the email: it links the
// account with that email if the provider hosts the email, or makes a new
// one (OAuthAccounts). The demo account is never linked or made. Then it is
// a sign-in like any other: a deactivated account is refused, and two-factor
// sign-in still asks for its code. The account is found or made, and
// committed, before the sign-in's own transaction opens, so a sign-in holds
// at most two connections at once: its own and the log's.
@Service
public class OAuthSignIn {

    private final OAuthAccounts accounts;
    private final UserIdentityRepository identities;
    private final AuthService authService;
    private final JwtService jwtService;
    private final DemoAccountGuard demoAccountGuard;
    private final AuditLog auditLog;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public OAuthSignIn(
            OAuthAccounts accounts,
            UserIdentityRepository identities,
            AuthService authService,
            JwtService jwtService,
            DemoAccountGuard demoAccountGuard,
            AuditLog auditLog,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.accounts = accounts;
        this.identities = identities;
        this.authService = authService;
        this.jwtService = jwtService;
        this.demoAccountGuard = demoAccountGuard;
        this.auditLog = auditLog;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public LoginOutcome signIn(ProviderProfile profile, String presentedRefreshToken) {
        String via = "With " + profile.provider().label();
        Long identityKey = accounts.identityFor(profile, via);
        return transaction.execute(status -> {
            UserIdentity identity = identities.findById(identityKey).orElseThrow();
            User user = identity.getUser();
            if (!user.isActive()) {
                auditLog.signInFailed(user.getEmail(), user, via + ": account deactivated");
                throw new OAuthSignInException("deactivated");
            }
            if (demoAccountGuard.isDemo(user)) {
                auditLog.signInFailed(user.getEmail(), user, via + ": demo account");
                throw new OAuthSignInException("demo_account");
            }
            identity.setLastUsedAt(clock.instant());
            if (user.isTwoFactorEnabled()) {
                return new LoginOutcome.TwoFactorChallenge(jwtService.issueTwoFactorChallenge(user, via));
            }
            return authService.startSession(user, presentedRefreshToken, via);
        });
    }
}
