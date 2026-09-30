package com.invoicespot.user;

import com.invoicespot.common.ApiException;
import com.invoicespot.user.dto.UpdateProfileRequest;
import com.invoicespot.user.dto.UserPageResponse;
import com.invoicespot.user.dto.UserProfileResponse;
import com.invoicespot.user.dto.UserSummaryResponse;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final int PAGE_SIZE = 10;

    private final UserRepository userRepository;
    private final DemoAccountGuard demoAccountGuard;

    public UserService(UserRepository userRepository, DemoAccountGuard demoAccountGuard) {
        this.userRepository = userRepository;
        this.demoAccountGuard = demoAccountGuard;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long pkid) {
        User user = require(pkid);
        return UserProfileResponse.from(user, demoAccountGuard.isDemo(user));
    }

    @Transactional
    public UserProfileResponse updateProfile(Long pkid, UpdateProfileRequest request) {
        if (request.password() != null || request.passwordConfirm() != null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "This route is not for password updates. Please use the password reset"
                            + " functionality instead");
        }
        if (request.email() != null
                || request.isEmailVerified() != null
                || request.provider() != null
                || request.roles() != null
                || request.googleId() != null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "You are not allowed to update that field on this route");
        }

        User user = require(pkid);
        if (request.username() != null) {
            user.setUsername(request.username());
        }
        if (request.firstName() != null) {
            user.setFirstName(request.firstName());
        }
        if (request.lastName() != null) {
            user.setLastName(request.lastName());
        }
        if (request.avatar() != null) {
            user.setAvatar(request.avatar());
        }
        if (request.businessName() != null) {
            user.setBusinessName(request.businessName());
        }
        if (request.phoneNumber() != null) {
            user.setPhoneNumber(request.phoneNumber());
        }
        if (request.address() != null) {
            user.setAddress(request.address());
        }
        if (request.city() != null) {
            user.setCity(request.city());
        }
        if (request.country() != null) {
            user.setCountry(request.country());
        }
        userRepository.save(user);
        return UserProfileResponse.from(user, demoAccountGuard.isDemo(user));
    }

    @Transactional
    public void deleteOwnAccount(Long pkid) {
        User user = require(pkid);
        demoAccountGuard.requireNotDemo(user, "deleting it");
        userRepository.delete(user);
    }

    @Transactional(readOnly = true)
    public UserPageResponse listUsers(int pageNumber) {
        int page = Math.max(pageNumber, 1);
        Page<User> result = userRepository.findAll(
                PageRequest.of(page - 1, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new UserPageResponse(
                true,
                result.getTotalElements(),
                result.getTotalPages(),
                result.getContent().stream()
                        .map(user -> UserSummaryResponse.from(user, demoAccountGuard.isDemo(user)))
                        .toList());
    }

    @Transactional
    public String deleteUser(String id) {
        User user = externalId(id)
                .flatMap(userRepository::findByExternalId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        demoAccountGuard.requireNotDemoTarget(user, "deleted");
        String firstName = user.getFirstName();
        userRepository.delete(user);
        return firstName;
    }

    @Transactional
    public UserSummaryResponse deactivateUser(String id) {
        User user = externalId(id)
                .flatMap(userRepository::findByExternalId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "user was not found"));
        demoAccountGuard.requireNotDemoTarget(user, "deactivated");
        user.setActive(false);
        user.getRefreshTokens().clear();
        userRepository.save(user);
        return UserSummaryResponse.from(user, false);
    }

    private User require(Long pkid) {
        return userRepository
                .findById(pkid)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST, "That user does not exist in our system"));
    }

    private static Optional<UUID> externalId(String id) {
        try {
            return Optional.of(UUID.fromString(id));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
