package com.smartflow.backend.infrastructure.repository;

import com.smartflow.backend.domain.entity.EmailTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, Long> {

    // infrastructure/mail résout le gabarit à envoyer par son code (ex. "REQUEST_SUBMITTED").
    Optional<EmailTemplate> findByCode(String code);
}
