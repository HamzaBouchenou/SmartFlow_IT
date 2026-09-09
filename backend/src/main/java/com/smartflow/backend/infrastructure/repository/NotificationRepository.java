package com.smartflow.backend.infrastructure.repository;

import com.smartflow.backend.domain.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // §6.8 - centre de notifications avec statut lu/non lu.
    Page<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId, Pageable pageable);

    // §6.8 - l'onglet "Non lues" du centre de notifications : le même tri que la liste
    // complète, pour que passer d'un onglet à l'autre ne réordonne pas ce qui est commun.
    Page<Notification> findByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(Long recipientId, Pageable pageable);

    // §6.8 - "tout marquer comme lu" : la totalité des non-lues, sans pagination, parce que
    // l'action porte justement sur ce que l'utilisateur n'a pas encore fait défiler.
    List<Notification> findByRecipientIdAndReadAtIsNull(Long recipientId);

    long countByRecipientIdAndReadAtIsNull(Long recipientId);
}
