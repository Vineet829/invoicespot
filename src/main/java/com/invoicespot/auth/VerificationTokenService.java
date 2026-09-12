package com.invoicespot.auth;

import com.invoicespot.config.AppProperties;
import com.invoicespot.user.User;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VerificationTokenService {

    private final VerifyResetTokenRepository tokenRepository;
    private final Duration tokenTtl;
    private final SecureRandom random = new SecureRandom();

    public VerificationTokenService(
            VerifyResetTokenRepository tokenRepository, AppProperties properties) {
        this.tokenRepository = tokenRepository;
        this.tokenTtl = properties.verification().tokenTtl();
    }

    @Transactional
    public String issue(User user) {
        tokenRepository.deleteByUserPkid(user.getPkid());
        byte[] material = new byte[32];
        random.nextBytes(material);
        String token = HexFormat.of().formatHex(material);

        VerifyResetToken entity = new VerifyResetToken();
        entity.setUser(user);
        entity.setToken(token);
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setExpiresAt(now.plus(tokenTtl));
        tokenRepository.save(entity);
        return token;
    }

    @Transactional(readOnly = true)
    public Optional<VerifyResetToken> findUsable(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return tokenRepository
                .findByToken(token)
                .filter(entity -> entity.getExpiresAt().isAfter(Instant.now()));
    }

    @Transactional
    public void consume(VerifyResetToken token) {
        tokenRepository.delete(token);
    }
}
