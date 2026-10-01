package com.invoicespot.settings;

import com.invoicespot.business.BusinessAccess;
import com.invoicespot.business.BusinessRepository;
import com.invoicespot.common.ApiException;
import com.invoicespot.settings.dto.EmailTemplateRequest;
import com.invoicespot.settings.dto.EmailTemplateResponse;
import com.invoicespot.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailTemplateService {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z_]+)\\}");

    private final EmailTemplateRepository repository;
    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public EmailTemplateService(
            EmailTemplateRepository repository,
            BusinessRepository businessRepository,
            UserRepository userRepository,
            Clock clock) {
        this.repository = repository;
        this.businessRepository = businessRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<EmailTemplateResponse> list(BusinessAccess access) {
        Map<EmailTemplateKind, EmailTemplate> written = repository.findByBusinessPkid(access.businessPkid()).stream()
                .collect(Collectors.toMap(EmailTemplate::getKind, Function.identity()));
        return Arrays.stream(EmailTemplateKind.values())
                .map(kind -> EmailTemplateResponse.of(kind, written.get(kind)))
                .toList();
    }

    @Transactional
    public EmailTemplateResponse save(BusinessAccess access, String kindName, EmailTemplateRequest request) {
        EmailTemplateKind kind = kind(kindName);
        EmailText text = checked(kind, request);
        EmailTemplate template = repository.findByBusinessPkidAndKind(access.businessPkid(), kind).orElseGet(() -> {
            EmailTemplate created = new EmailTemplate();
            created.setBusiness(businessRepository.getReferenceById(access.businessPkid()));
            created.setKind(kind);
            return created;
        });
        template.setSubject(text.subject());
        template.setBody(text.body());
        template.setUpdatedAt(Instant.now(clock));
        template.setUpdatedBy(userRepository.getReferenceById(access.userPkid()));
        repository.save(template);
        return EmailTemplateResponse.of(kind, template);
    }

    // Back to the standard subject and message.
    @Transactional
    public EmailTemplateResponse reset(BusinessAccess access, String kindName) {
        EmailTemplateKind kind = kind(kindName);
        repository.findByBusinessPkidAndKind(access.businessPkid(), kind).ifPresent(repository::delete);
        return EmailTemplateResponse.of(kind, null);
    }

    // What a business's emails of a kind say: its own words, or the standard
    // ones.
    @Transactional(readOnly = true)
    public EmailText text(Long businessPkid, EmailTemplateKind kind) {
        return repository.findByBusinessPkidAndKind(businessPkid, kind)
                .map(template -> new EmailText(template.getSubject(), template.getBody()))
                .orElseGet(() -> new EmailText(kind.standardSubject(), kind.standardBody()));
    }

    // A subject on one line and a message, each using only the placeholders
    // its kind of email fills.
    public static EmailText checked(EmailTemplateKind kind, EmailTemplateRequest request) {
        EmailText text = new EmailText(request.subject().replaceAll("[\\r\\n]+", " ").trim(), request.body());
        for (String part : List.of(text.subject(), text.body())) {
            Matcher placeholder = PLACEHOLDER.matcher(part);
            while (placeholder.find()) {
                if (!kind.fills(placeholder.group(1))) {
                    throw new ApiException(
                            HttpStatus.BAD_REQUEST,
                            placeholder.group() + " can't be used in " + kind.noun() + " emails. Use "
                                    + choices(kind));
                }
            }
        }
        return text;
    }

    public static EmailTemplateKind kind(String value) {
        try {
            return EmailTemplateKind.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "There is no such kind of email");
        }
    }

    // "{client}, {document} or {business}".
    private static String choices(EmailTemplateKind kind) {
        List<String> names = kind.placeholders().stream().map(known -> "{" + known.name() + "}").toList();
        return String.join(", ", names.subList(0, names.size() - 1)) + " or " + names.get(names.size() - 1);
    }
}
