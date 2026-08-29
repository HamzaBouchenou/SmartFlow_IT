package com.smartflow.backend.infrastructure.repository;

import com.smartflow.backend.domain.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    // §6.4/§9.4 - pièces jointes d'une demande, par ordre chronologique.
    List<Attachment> findByRequestIdOrderByUploadedAtAsc(Long requestId);
}
