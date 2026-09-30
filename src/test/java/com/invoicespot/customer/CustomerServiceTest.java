package com.invoicespot.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.invoicespot.business.Business;
import com.invoicespot.business.BusinessAccess;
import com.invoicespot.business.BusinessRepository;
import com.invoicespot.business.BusinessRole;
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
    @Mock private BusinessRepository businessRepository;
    @Mock private CustomerDirectory customerDirectory;
    @Mock private SettingsService settingsService;
    private CustomerService service;

    @BeforeEach
    void setUp() {
        service = new CustomerService(
                customerRepository,
                userRepository,
                businessRepository,
                customerDirectory,
                settingsService,
                Clock.systemUTC());
    }

    private static User userWithPkid(long pkid) {
        User user = new User();
        user.setPkid(pkid);
        user.setId(UUID.randomUUID());
        return user;
    }

    private static Business businessWithPkid(long pkid) {
        Business business = new Business();
        business.setPkid(pkid);
        return business;
    }

    private static BusinessAccess access(long businessPkid, long userPkid) {
        return new BusinessAccess(businessPkid, UUID.randomUUID(), BusinessRole.OWNER, userWithPkid(userPkid));
    }

    private static CreateCustomerRequest request(String name, String email, String phone) {
        return new CreateCustomerRequest(name, email, phone, "29ABCDE1234F1Z5", "addr", "city", "country");
    }

    @Test
    @DisplayName("a customer needs a name, email and phone number")
    void rejectsIncompleteCustomer() {
        assertThatThrownBy(() -> service.create(access(1L, 1L), request("Acme", "a@b.com", null)))
                .isInstanceOf(ApiException.class)
                .hasMessage("A Customer must have at least a name, email and phone number");
        verify(customerRepository, never()).save(any());
    }

    @Test
    @DisplayName("a customer email the business already has is refused")
    void rejectsDuplicateEmail() {
        when(customerRepository.existsByBusinessPkidAndEmailIgnoreCase(1L, "a@b.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create(access(1L, 1L), request("Acme", "A@B.com", "+15551112222")))
                .isInstanceOf(ApiException.class)
                .hasMessage("You already have a customer with that email address")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("a created customer gets a generated account number, its business and its author")
    void createsCustomerWithAccountNumber() {
        User author = userWithPkid(7L);
        Business business = businessWithPkid(3L);
        when(customerRepository.existsByBusinessPkidAndEmailIgnoreCase(3L, "a@b.com")).thenReturn(false);
        when(userRepository.getReferenceById(7L)).thenReturn(author);
        when(businessRepository.getReferenceById(3L)).thenReturn(business);

        service.create(access(3L, 7L), request("Acme", "A@B.com", "+15551112222"));

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(captor.capture());
        Customer saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("a@b.com");
        assertThat(saved.getAccountNo()).startsWith("CUS-");
        assertThat(saved.getCreatedBy()).isSameAs(author);
        assertThat(saved.getBusiness()).isSameAs(business);
        assertThat(saved.getVatTinNo()).isEqualTo("29ABCDE1234F1Z5");
    }

    @Test
    @DisplayName("viewing another business's customer is forbidden")
    void forbidsForeignCustomerAccess() {
        Customer customer = new Customer();
        customer.setBusiness(businessWithPkid(99L));
        UUID id = UUID.randomUUID();
        when(customerRepository.findByExternalId(id)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> service.getOne(access(1L, 1L), id.toString()))
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

        assertThatThrownBy(() -> service.getOne(access(1L, 1L), id.toString()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
