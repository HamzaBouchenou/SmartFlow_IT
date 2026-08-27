package com.smartflow.backend.domain.entity;

import com.smartflow.backend.domain.enums.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A user's e-mail preference for one NotificationType (§6.8 - "Préférences de notification
 * limitées pour éviter la désactivation des alertes obligatoires"). Which types are
 * mandatory and therefore ignore this preference is a domain/rule decision, not a column
 * here.
 */
@Entity
@Table(name = "notification_preferences")
public class NotificationPreference extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 16)
    private NotificationType notificationType;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled = true;

    protected NotificationPreference() {
    }

    public NotificationPreference(User user, NotificationType notificationType) {
        this.user = user;
        this.notificationType = notificationType;
    }

    public User getUser() {
        return user;
    }

    public NotificationType getNotificationType() {
        return notificationType;
    }

    public boolean isEmailEnabled() {
        return emailEnabled;
    }

    public void setEmailEnabled(boolean emailEnabled) {
        this.emailEnabled = emailEnabled;
    }
}
