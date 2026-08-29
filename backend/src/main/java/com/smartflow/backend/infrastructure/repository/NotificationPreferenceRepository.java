package com.smartflow.backend.infrastructure.repository;

import com.smartflow.backend.domain.entity.NotificationPreference;
import com.smartflow.backend.domain.enums.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    // §6.8 - préférence d'e-mail par utilisateur et par type ; ADR-12 - l'absence de ligne
    // n'est pas un opt-out (le défaut d'émission reste actif).
    Optional<NotificationPreference> findByUserIdAndNotificationType(Long userId, NotificationType notificationType);
}
