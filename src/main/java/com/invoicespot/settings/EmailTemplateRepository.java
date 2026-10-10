package com.invoicespot.settings;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, Long> {

    List<EmailTemplate> findByBusinessPkid(Long businessPkid);

    Optional<EmailTemplate> findByBusinessPkidAndKind(Long businessPkid, EmailTemplateKind kind);
}
