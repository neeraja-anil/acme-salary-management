package com.acme.salary.audit;

import java.util.Map;

import com.acme.salary.auth.CurrentUser;
import com.acme.salary.common.web.PageResponse;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Writes and reads the audit trail. */
@Service
public class AuditService {

    private static final TypeReference<Map<String, Object>> CHANGES_TYPE = new TypeReference<>() {
    };

    private final AuditLogRepository auditLogs;
    private final CurrentUser currentUser;
    private final JsonMapper jsonMapper;

    public AuditService(AuditLogRepository auditLogs, CurrentUser currentUser, JsonMapper jsonMapper) {
        this.auditLogs = auditLogs;
        this.currentUser = currentUser;
        this.jsonMapper = jsonMapper;
    }

    /**
     * Records a change. {@link Propagation#MANDATORY} makes this fail if called outside a transaction,
     * so an audit entry is always committed or rolled back together with the change it describes.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditEvent event) {
        auditLogs.save(new AuditLog(
                event.entityType(),
                event.entityId(),
                event.employeeId(),
                event.action(),
                jsonMapper.writeValueAsString(event.changes().asMap()),
                currentUser.username(),
                event.at()));
    }

    /** Newest first. The caller is responsible for checking the employee exists. */
    @Transactional(readOnly = true)
    public PageResponse<AuditEntryResponse> findForEmployee(Long employeeId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("performedAt"), Sort.Order.desc("id")));
        return PageResponse.from(auditLogs.findByEmployeeId(employeeId, pageRequest), this::toResponse);
    }

    private AuditEntryResponse toResponse(AuditLog log) {
        return new AuditEntryResponse(
                log.getId(),
                log.getAction(),
                log.getEntityType(),
                log.getEntityId(),
                jsonMapper.readValue(log.getChanges(), CHANGES_TYPE),
                log.getPerformedBy(),
                log.getPerformedAt());
    }
}
