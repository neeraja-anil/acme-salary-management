package com.acme.salary.audit;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Builds the {@code changes} payload of an audit entry: {@code {"field": {"from": .., "to": ..}}}.
 * Keeps insertion order so entries read naturally.
 */
public final class AuditChanges {

    private final Map<String, FieldChange> changes = new LinkedHashMap<>();

    private AuditChanges() {
    }

    public static AuditChanges create() {
        return new AuditChanges();
    }

    /** Records the field only if the value actually changed. */
    public AuditChanges changed(String field, Object from, Object to) {
        if (!Objects.equals(from, to)) {
            changes.put(field, new FieldChange(from, to));
        }
        return this;
    }

    /** Records a value that had no previous state (creation, or a one-off attribute like a reason). */
    public AuditChanges set(String field, Object to) {
        if (to != null) {
            changes.put(field, new FieldChange(null, to));
        }
        return this;
    }

    public boolean isEmpty() {
        return changes.isEmpty();
    }

    public Map<String, FieldChange> asMap() {
        return Collections.unmodifiableMap(changes);
    }
}
