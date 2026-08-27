package com.smartflow.backend.domain.entity;

import com.smartflow.backend.domain.enums.Priority;
import com.smartflow.backend.domain.enums.RequestStatus;
import com.smartflow.backend.domain.enums.SlaStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A request folder (§6.4 - Gestion des demandes).
 *
 * RG-01: reference is generated from a PostgreSQL sequence, never count(*) + 1 - enforced
 * in the service layer, guaranteed here only by the unique constraint.
 * RG-03: workflowDefinition is set once at submission and never reassigned afterwards.
 *
 * SLA source of truth: SlaEvent is authoritative (RG-07 - "modèle événementiel, pas de
 * compteur mutable"). slaDueAtFirstResponse, slaDueAtResolution, slaStatus,
 * slaSuspendedSince and slaSuspendedMinutes below are a materialized read model, entirely
 * recomputed from this request's SlaEvent rows plus the applicable Sla configuration by
 * the scheduled SLA sweep (infrastructure/scheduler). No other code path writes them, and
 * no calculation may read them as an input - only SlaEvent is replayed. If these columns
 * and SlaEvent ever disagree, SlaEvent wins and the columns must be recomputed, never the
 * reverse. They exist only so a dashboard read is a plain column read, per "Ce qu'il ne
 * faut jamais faire" - never compute an SLA status on the fly in a dashboard query.
 */
@Entity
@Table(name = "requests")
public class Request extends BaseEntity {

    @Column(nullable = false, unique = true, length = 32)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_type_id", nullable = false)
    private RequestType requestType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_definition_id")
    private WorkflowDefinition workflowDefinition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_step_id")
    private Step currentStep;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RequestStatus status = RequestStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Priority priority;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closure_reason", columnDefinition = "text")
    private String closureReason;

    @Column(name = "closure_solution", columnDefinition = "text")
    private String closureSolution;

    @Column(name = "satisfaction_rating")
    private Integer satisfactionRating;

    @Column(name = "reopen_deadline")
    private Instant reopenDeadline;

    @Column(name = "sla_due_at_first_response")
    private Instant slaDueAtFirstResponse;

    @Column(name = "sla_due_at_resolution")
    private Instant slaDueAtResolution;

    @Enumerated(EnumType.STRING)
    @Column(name = "sla_status", length = 16)
    private SlaStatus slaStatus;

    // Start of the CURRENT open suspension; null while not suspended.
    @Column(name = "sla_suspended_since")
    private Instant slaSuspendedSince;

    // Cumulative minutes already spent suspended across all CLOSED suspend/resume cycles
    // (RG-07). A single slaSuspendedSince timestamp cannot carry more than one suspension:
    // on resume, the elapsed interval (now - slaSuspendedSince) is folded in here and
    // slaSuspendedSince is cleared, so a second suspension does not overwrite and lose the
    // first one's elapsed time. Total suspended time at any instant = this value, plus
    // (now - slaSuspendedSince) when a suspension is currently open.
    @Column(name = "sla_suspended_minutes", nullable = false)
    private int slaSuspendedMinutes;

    // SlaEvent has no repository of its own (infrastructure/repository) - it is a pure
    // child entity with no meaning outside one Request, reached and appended to only
    // through this aggregate root. PERSIST + MERGE let addSlaEvent() below create a new
    // row without a dedicated repository, whether request is already managed in the
    // caller's transaction (PERSIST, cascades on flush) or was passed in detached (MERGE,
    // cascades when re-attached via requestRepository.save()). No REMOVE/orphanRemoval:
    // SlaEvent is an append-only log (RG-07) and must never be deletable by clearing this
    // collection. application/service/SlaSuspensionService is the only writer
    // (SUSPENDED/RESUMED, from a workflow transition); the scheduled SLA sweep only reads.
    @OneToMany(mappedBy = "request", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.LAZY)
    @OrderBy("occurredAt ASC")
    private List<SlaEvent> slaEvents = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    protected Request() {
    }

    public Request(String reference, RequestType requestType, User requester, String title) {
        this.reference = reference;
        this.requestType = requestType;
        this.requester = requester;
        this.title = title;
    }

    public String getReference() {
        return reference;
    }

    public RequestType getRequestType() {
        return requestType;
    }

    public WorkflowDefinition getWorkflowDefinition() {
        return workflowDefinition;
    }

    public void setWorkflowDefinition(WorkflowDefinition workflowDefinition) {
        this.workflowDefinition = workflowDefinition;
    }

    public Step getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(Step currentStep) {
        this.currentStep = currentStep;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public void setStatus(RequestStatus status) {
        this.status = status;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public User getRequester() {
        return requester;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    public String getClosureReason() {
        return closureReason;
    }

    public void setClosureReason(String closureReason) {
        this.closureReason = closureReason;
    }

    public String getClosureSolution() {
        return closureSolution;
    }

    public void setClosureSolution(String closureSolution) {
        this.closureSolution = closureSolution;
    }

    public Integer getSatisfactionRating() {
        return satisfactionRating;
    }

    public void setSatisfactionRating(Integer satisfactionRating) {
        this.satisfactionRating = satisfactionRating;
    }

    public Instant getReopenDeadline() {
        return reopenDeadline;
    }

    public void setReopenDeadline(Instant reopenDeadline) {
        this.reopenDeadline = reopenDeadline;
    }

    public Instant getSlaDueAtFirstResponse() {
        return slaDueAtFirstResponse;
    }

    public void setSlaDueAtFirstResponse(Instant slaDueAtFirstResponse) {
        this.slaDueAtFirstResponse = slaDueAtFirstResponse;
    }

    public Instant getSlaDueAtResolution() {
        return slaDueAtResolution;
    }

    public void setSlaDueAtResolution(Instant slaDueAtResolution) {
        this.slaDueAtResolution = slaDueAtResolution;
    }

    public SlaStatus getSlaStatus() {
        return slaStatus;
    }

    public void setSlaStatus(SlaStatus slaStatus) {
        this.slaStatus = slaStatus;
    }

    public Instant getSlaSuspendedSince() {
        return slaSuspendedSince;
    }

    public void setSlaSuspendedSince(Instant slaSuspendedSince) {
        this.slaSuspendedSince = slaSuspendedSince;
    }

    public int getSlaSuspendedMinutes() {
        return slaSuspendedMinutes;
    }

    public void setSlaSuspendedMinutes(int slaSuspendedMinutes) {
        this.slaSuspendedMinutes = slaSuspendedMinutes;
    }

    public List<SlaEvent> getSlaEvents() {
        return slaEvents;
    }

    /** Appends a new SlaEvent to this request, keeping both sides of the association consistent. */
    public void addSlaEvent(SlaEvent event) {
        slaEvents.add(event);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
