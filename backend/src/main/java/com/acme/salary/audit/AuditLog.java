package com.acme.salary.audit;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

/**
 * Append-only record of a change to employee or salary data (US-10).
 * Never updated by Hibernate ({@link Immutable}); database triggers reject UPDATE/DELETE.
 */
@Entity
@Immutable
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false)
    private AuditEntityType entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private AuditAction action;

    /** JSON object of {@link FieldChange}s, keyed by field name. */
    @Column(name = "changes", nullable = false)
    private String changes;

    @Column(name = "performed_by", nullable = false)
    private String performedBy;

    @Column(name = "performed_at", nullable = false)
    private Instant performedAt;

    protected AuditLog() {
    }

    AuditLog(AuditEntityType entityType, Long entityId, Long employeeId, AuditAction action, String changes,
             String performedBy, Instant performedAt) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.employeeId = employeeId;
        this.action = action;
        this.changes = changes;
        this.performedBy = performedBy;
        this.performedAt = performedAt;
    }

    public Long getId() {
        return id;
    }

    public AuditEntityType getEntityType() {
        return entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getChanges() {
        return changes;
    }

    public String getPerformedBy() {
        return performedBy;
    }

    public Instant getPerformedAt() {
        return performedAt;
    }
}
