package com.invoicespot.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.invoicespot.common.ApiException;
import com.invoicespot.config.DemoProperties;
import com.invoicespot.user.dto.UpdateProfileRequest;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository, new DemoAccountGuard(new DemoProperties("demo@example.test")));
    }

    private static UpdateProfileRequest onlyAllowed() {
        return new UpdateProfileRequest(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                "Bengaluru", null);
    }

    private static UpdateProfileRequest with(
            String password, String email, java.util.Set<String> roles) {
        return new UpdateProfileRequest(
                password, null, email, null, null, roles, null, null, null, null, null, null, null,
                null, null, null);
    }

    @Test
    @DisplayName("updating a password on the profile route is rejected")
    void rejectsPasswordUpdate() {
        assertThatThrownBy(() -> service.updateProfile(1L, with("Secret1!", null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not for password updates");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("updating a protected field (email/roles) on the profile route is rejected")
    void rejectsProtectedFieldUpdate() {
        assertThatThrownBy(() -> service.updateProfile(1L, with(null, "new@x.com", null)))
                .isInstanceOf(ApiException.class)
                .hasMessage("You are not allowed to update that field on this route");
    }

    @Test
    @DisplayName("allowed fields are applied and persisted")
    void appliesAllowedFields() {
        User user = new User();
        user.setFirstName("Ada");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        var response = service.updateProfile(1L, onlyAllowed());

        assertThat(response.city()).isEqualTo("Bengaluru");
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("deleting an unknown user id yields a 404")
    void deleteUnknownUser() {
        UUID id = UUID.randomUUID();
        when(userRepository.findByExternalId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteUser(id.toString()))
                .isInstanceOf(ApiException.class)
                .hasMessage("User not found")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("deactivating a user flips the active flag and persists")
    void deactivatesUser() {
        User user = new User();
        user.setActive(true);
        UUID id = UUID.randomUUID();
        when(userRepository.findByExternalId(id)).thenReturn(Optional.of(user));

        var summary = service.deactivateUser(id.toString());

        assertThat(summary.active()).isFalse();
        assertThat(user.isActive()).isFalse();
        verify(userRepository).save(user);
    }
}
