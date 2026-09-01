package com.invoicespot.auth;

import com.invoicespot.common.ApiException;
import com.invoicespot.user.User;
import com.invoicespot.user.UserRepository;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public TokenBundle login(String email, String password, String presentedRefreshToken) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please provide an email and password");
        }

        User user = userRepository.findByEmailIgnoreCase(email.trim())
                .filter(candidate -> candidate.getPassword() != null
                        && passwordEncoder.matches(password, candidate.getPassword()))
                .orElseThrow(
                        () -> new ApiException(HttpStatus.UNAUTHORIZED, "Incorrect email or password"));

        if (!user.isEmailVerified()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "You are not verified. Check your email, a verification email link was"
                            + " sent when you registered");
        }
        if (!user.isActive()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "You have been deactivated by the admin and login is impossible. Contact us"
                            + " for enquiries");
        }

        Set<String> retained = new HashSet<>(user.getRefreshTokens());
        if (presentedRefreshToken != null && !presentedRefreshToken.isBlank()) {
            retained.remove(presentedRefreshToken);
            if (userRepository.findByRefreshToken(presentedRefreshToken).isEmpty()) {
                retained.clear();
            }
        }
        return rotate(user, retained);
    }

    @Transactional
    public Optional<TokenBundle> refresh(String presentedRefreshToken) {
        Optional<User> owner = userRepository.findByRefreshToken(presentedRefreshToken);
        if (owner.isEmpty()) {
            revokeCompromisedSessions(presentedRefreshToken);
            return Optional.empty();
        }

        User user = owner.get();
        Set<String> retained = new HashSet<>(user.getRefreshTokens());
        retained.remove(presentedRefreshToken);

        Optional<UUID> subject = jwtService.readRefreshSubject(presentedRefreshToken);
        if (subject.isEmpty()) {
            user.setRefreshTokens(retained);
            userRepository.save(user);
            return Optional.empty();
        }
        if (!user.getId().equals(subject.get())) {
            return Optional.empty();
        }
        return Optional.of(rotate(user, retained));
    }

    @Transactional
    public Optional<String> logout(String presentedRefreshToken) {
        Optional<User> owner = userRepository.findByRefreshToken(presentedRefreshToken);
        if (owner.isEmpty()) {
            return Optional.empty();
        }
        User user = owner.get();
        user.getRefreshTokens().remove(presentedRefreshToken);
        userRepository.save(user);
        return Optional.of(user.getFirstName() + ",you have been logged out successfully");
    }

    private void revokeCompromisedSessions(String presentedRefreshToken) {
        jwtService
                .readRefreshSubject(presentedRefreshToken)
                .flatMap(userRepository::findByExternalId)
                .ifPresent(compromised -> {
                    compromised.getRefreshTokens().clear();
                    userRepository.save(compromised);
                });
    }

    private TokenBundle rotate(User user, Set<String> retained) {
        String accessToken = jwtService.issueAccessToken(user);
        String refreshToken = jwtService.issueRefreshToken(user);
        retained.add(refreshToken);
        user.setRefreshTokens(retained);
        userRepository.save(user);
        return new TokenBundle(accessToken, refreshToken, user);
    }
}
