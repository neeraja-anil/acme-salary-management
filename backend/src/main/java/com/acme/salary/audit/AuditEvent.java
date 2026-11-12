package com.acme.salary.audit;

import java.time.Instant;

/**
 * A change to record. {@code at} is passed in by the caller so the audit timestamp is exactly
 * the timestamp written on the changed record.
 */
public record AuditEvent(AuditAction action, AuditEntityType entityType, Long entityId, Long employeeId,
                         AuditChanges changes, Instant at) {
}
