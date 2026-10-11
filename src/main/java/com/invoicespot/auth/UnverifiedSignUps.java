package com.invoicespot.auth;

import com.invoicespot.business.BusinessService;
import com.invoicespot.user.User;
import com.invoicespot.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Sign-ups whose email was never verified. Whoever made one may not own the
// address, so it holds the email and username only for a while: it goes with
// its empty business when its email signs up again, so the owner is never
// locked out and only the newest link works, and in any case after a week.
@Service
public class UnverifiedSignUps {

    static final Duration KEPT = Duration.ofDays(7);

    private final UserRepository userRepository;
    private final BusinessService businessService;
    private final Clock clock;

    public UnverifiedSignUps(UserRepository userRepository, BusinessService businessService, Clock clock) {
        this.userRepository = userRepository;
        this.businessService = businessService;
        this.clock = clock;
    }

    @Transactional
    public void remove(User user) {
        businessService.deleteOwnedBy(user);
        userRepository.delete(user);
        userRepository.flush();
    }

    // For the daily jobs: how many were removed.
    @Transactional
    public int removeExpired() {
        List<User> expired = userRepository.findUnverifiedCreatedBefore(clock.instant().minus(KEPT));
        expired.forEach(this::remove);
        return expired.size();
    }
}
