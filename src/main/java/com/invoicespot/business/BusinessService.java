package com.invoicespot.business;

import com.invoicespot.audit.AuditEventType;
import com.invoicespot.audit.AuditLog;
import com.invoicespot.business.dto.BusinessProfileResponse;
import com.invoicespot.business.dto.UpdateBusinessRequest;
import com.invoicespot.common.ApiException;
import com.invoicespot.settings.BusinessSettingsRepository;
import com.invoicespot.tax.Gstin;
import com.invoicespot.tax.TaxRegime;
import com.invoicespot.user.DemoAccountGuard;
import com.invoicespot.user.User;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusinessService {

    private static final int MOST_OWNED = 10;

    private final BusinessRepository businessRepository;
    private final BusinessMemberRepository memberRepository;
    private final DemoAccountGuard demoAccountGuard;
    private final AuditLog auditLog;
    private final BusinessSettingsRepository settingsRepository;
    private final Clock clock;

    public BusinessService(
            BusinessRepository businessRepository,
            BusinessMemberRepository memberRepository,
            DemoAccountGuard demoAccountGuard,
            AuditLog auditLog,
            BusinessSettingsRepository settingsRepository,
            Clock clock) {
        this.businessRepository = businessRepository;
        this.memberRepository = memberRepository;
        this.demoAccountGuard = demoAccountGuard;
        this.auditLog = auditLog;
        this.settingsRepository = settingsRepository;
        this.clock = clock;
    }

    // Everyone starts with a business of their own, named after them, which
    // they can rename.
    @Transactional
    public Business createPersonal(User user) {
        return found(user, personalName(user));
    }

    // Another business, which its founder owns.
    @Transactional
    public Business create(User user, String name) {
        demoAccountGuard.requireNotDemo(user, "starting a business");
        if (name == null || name.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A business needs a name");
        }
        if (businessRepository.findOwnedBy(user.getPkid()).size() >= MOST_OWNED) {
            throw new ApiException(HttpStatus.CONFLICT, "You can own at most " + MOST_OWNED + " businesses");
        }
        Business business = found(user, name.trim());
        // Named rather than linked: the log writes on its own, before this
        // business exists for anyone else to see.
        auditLog.record(AuditEventType.BUSINESS_CREATED, user, user, null, business.getName());
        return business;
    }

    private Business found(User user, String name) {
        Business business = new Business();
        business.setName(name);
        business.setEmail(user.getEmail());
        businessRepository.save(business);
        BusinessMember owner = new BusinessMember();
        owner.setBusiness(business);
        owner.setUser(user);
        owner.setRole(BusinessRole.OWNER);
        owner.setJoinedAt(clock.instant());
        memberRepository.save(owner);
        return business;
    }

    static String personalName(User user) {
        String fullName = (value(user.getFirstName()) + " " + value(user.getLastName())).trim();
        return fullName.isEmpty() ? user.getUsername() : fullName;
    }

    // The business an account owns first.
    @Transactional(readOnly = true)
    public Business ownedBy(Long userPkid) {
        return businessRepository.findOwnedBy(userPkid).stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found"));
    }

    @Transactional(readOnly = true)
    public Business require(Long businessPkid) {
        return businessRepository
                .findById(businessPkid)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found"));
    }

    // The account that owns a business, which work done for the business
    // without anyone signed in, such as the daily jobs, acts as.
    @Transactional(readOnly = true)
    public User owner(Long businessPkid) {
        return memberRepository.findOwner(businessPkid)
                .map(BusinessMember::getUser)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "This business has no owner"));
    }

    // Everything a job needs to act for a business, as its owner.
    @Transactional(readOnly = true)
    public BusinessAccess ownerAccess(Long businessPkid) {
        BusinessMember owner = memberRepository.findOwner(businessPkid)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "This business has no owner"));
        return new BusinessAccess(businessPkid, require(businessPkid).getId(), BusinessRole.OWNER, owner.getUser());
    }

    @Transactional(readOnly = true)
    public BusinessProfileResponse profile(BusinessAccess access) {
        return BusinessProfileResponse.from(require(access), access.role());
    }

    // The shared demo business's details and logo are on every visitor's
    // documents, share links and PDFs, so they stay as its sample data has
    // them.
    @Transactional
    public BusinessProfileResponse updateProfile(BusinessAccess access, UpdateBusinessRequest request) {
        demoAccountGuard.requireNotDemo(access.user(), "changing the business's details");
        // Locked, so a save that waits for another member's starts from what
        // that one saved, instead of putting it back.
        Business business = businessRepository.findForUpdate(access.businessPkid())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Business not found"));
        if (request.name() != null) {
            if (request.name().isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "A business needs a name");
            }
            business.setName(request.name().trim());
        }
        update(request.legalName(), business::setLegalName);
        update(request.email(), business::setEmail);
        update(request.phoneNumber(), business::setPhoneNumber);
        update(request.address(), business::setAddress);
        update(request.city(), business::setCity);
        update(request.state(), business::setState);
        update(request.postalCode(), business::setPostalCode);
        update(request.country(), business::setCountry);
        update(request.logo(), business::setLogo);
        if (request.taxId() != null) {
            business.setTaxId(taxId(access.businessPkid(), request.taxId()));
        }
        return BusinessProfileResponse.from(business, access.role());
    }

    // The businesses an account owns that others work in, which keep the
    // account from going until they are handed over.
    @Transactional(readOnly = true)
    public List<String> sharedOwnedBy(Long userPkid) {
        return businessRepository.findSharedNamesOwnedBy(userPkid);
    }

    // The businesses an account owns are deleted with it.
    @Transactional
    public void deleteOwnedBy(User user) {
        businessRepository.deleteAll(businessRepository.findOwnedBy(user.getPkid()));
    }

    private Business require(BusinessAccess access) {
        return require(access.businessPkid());
    }

    // In India GST mode the tax ID is the business's GSTIN, and must stay one.
    private String taxId(Long businessPkid, String value) {
        boolean gst = settingsRepository.findByBusinessPkid(businessPkid)
                .map(settings -> settings.getTaxRegime() == TaxRegime.GST_INDIA)
                .orElse(false);
        if (!gst) {
            return value.isBlank() ? null : value.trim();
        }
        String gstin = value.trim().toUpperCase(Locale.ROOT);
        if (!Gstin.isValid(gstin)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "In GST mode your tax ID must be a valid GSTIN");
        }
        return gstin;
    }

    private static void update(String value, Consumer<String> setter) {
        if (value != null) {
            setter.accept(value.isBlank() ? null : value.trim());
        }
    }

    private static String value(String text) {
        return text == null ? "" : text;
    }
}
