package com.invoicespot.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.invoicespot.common.ApiException;
import com.invoicespot.customer.dto.CreateCustomerRequest;
import com.invoicespot.settings.SettingsService;
import com.invoicespot.user.User;
import com.invoicespot.user.UserRepository;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private UserRepository userRepository;
    @Mock private CustomerDirectory customerDirectory;
    @Mock private SettingsService settingsService;
    private CustomerService service;

    @BeforeEach
    void setUp() {
        service = new CustomerService(
                customerRepository, userRepository, customerDirectory, settingsService, Clock.systemUTC());
    }

    private static User userWithPkid(long pkid) {
        User user = new User();
        user.setPkid(pkid);
        user.setId(UUID.randomUUID());
        return user;
    }

    private static CreateCustomerRequest request(String name, String email, String phone) {
        return new CreateCustomerRequest(name, email, phone, "29ABCDE1234F1Z5", "addr", "city", "country");
    }

    @Test
    @DisplayName("a customer needs a name, email and phone number")
    void rejectsIncompleteCustomer() {
        assertThatThrownBy(() -> service.create(1L, request("Acme", "a@b.com", null)))
                .isInstanceOf(ApiException.class)
                .hasMessage("A Customer must have at least a name, email and phone number");
        verify(customerRepository, never()).save(any());
    }

    @Test
    @DisplayName("a customer email the user already has is refused")
    void rejectsDuplicateEmail() {
        when(customerRepository.existsByCreatedByPkidAndEmailIgnoreCase(1L, "a@b.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create(1L, request("Acme", "A@B.com", "+15551112222")))
                .isInstanceOf(ApiException.class)
                .hasMessage("You already have a customer with that email address")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("a created customer gets a generated account number and the owner attached")
    void createsCustomerWithAccountNumber() {
        User owner = userWithPkid(7L);
        when(customerRepository.existsByCreatedByPkidAndEmailIgnoreCase(7L, "a@b.com")).thenReturn(false);
        when(userRepository.findById(7L)).thenReturn(Optional.of(owner));

        service.create(7L, request("Acme", "A@B.com", "+15551112222"));

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(captor.capture());
        Customer saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("a@b.com");
        assertThat(saved.getAccountNo()).startsWith("CUS-");
        assertThat(saved.getCreatedBy()).isSameAs(owner);
        assertThat(saved.getVatTinNo()).isEqualTo("29ABCDE1234F1Z5");
    }

    @Test
    @DisplayName("viewing another user's customer is forbidden")
    void forbidsForeignCustomerAccess() {
        Customer customer = new Customer();
        customer.setCreatedBy(userWithPkid(99L));
        UUID id = UUID.randomUUID();
        when(customerRepository.findByExternalId(id)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> service.getOne(1L, id.toString()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not authorized to view this customer")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("an unknown customer id yields a 404")
    void unknownCustomerIsNotFound() {
        UUID id = UUID.randomUUID();
        when(customerRepository.findByExternalId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOne(1L, id.toString()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
