package com.smartflow.backend.domain.entity;

import com.smartflow.backend.domain.enums.Priority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * SLA targets for a (RequestType, Priority) pair (§6.7 - "Définition d'un délai de prise
 * en charge et d'un délai de résolution par type de demande et priorité").
 */
@Entity
@Table(name = "sla")
public class Sla extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_type_id", nullable = false)
    private RequestType requestType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Priority priority;

    @Column(name = "first_response_minutes", nullable = false)
    private int firstResponseMinutes;

    @Column(name = "resolution_minutes", nullable = false)
    private int resolutionMinutes;

    // ADR-09 : le calendrier ouvré est une extension hors socle (§6.7) - défaut à false
    // tant qu'il n'est pas implémenté.
    @Column(name = "use_business_calendar", nullable = false)
    private boolean useBusinessCalendar = false;

    protected Sla() {
    }

    public Sla(RequestType requestType, Priority priority, int firstResponseMinutes, int resolutionMinutes) {
        this.requestType = requestType;
        this.priority = priority;
        this.firstResponseMinutes = firstResponseMinutes;
        this.resolutionMinutes = resolutionMinutes;
    }

    public RequestType getRequestType() {
        return requestType;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public int getFirstResponseMinutes() {
        return firstResponseMinutes;
    }

    public void setFirstResponseMinutes(int firstResponseMinutes) {
        this.firstResponseMinutes = firstResponseMinutes;
    }

    public int getResolutionMinutes() {
        return resolutionMinutes;
    }

    public void setResolutionMinutes(int resolutionMinutes) {
        this.resolutionMinutes = resolutionMinutes;
    }

    public boolean isUseBusinessCalendar() {
        return useBusinessCalendar;
    }

    public void setUseBusinessCalendar(boolean useBusinessCalendar) {
        this.useBusinessCalendar = useBusinessCalendar;
    }
}
