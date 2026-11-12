package com.acme.salary.audit;

import java.time.Instant;
import java.util.Map;

/** One audit entry as returned by {@code GET /api/employees/{id}/audit}. */
public record AuditEntryResponse(
        Long id,
        AuditAction action,
        AuditEntityType entityType,
        Long entityId,
        Map<String, Object> changes,
        String performedBy,
        Instant performedAt) {
}
