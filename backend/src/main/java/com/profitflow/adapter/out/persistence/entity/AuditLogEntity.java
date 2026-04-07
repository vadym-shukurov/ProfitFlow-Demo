package com.profitflow.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable audit log entry — once written it is never updated or deleted.
 *
 * <p>Captures every state-changing API call with enough context to satisfy
 * SOX §302/§404 traceability requirements: who did what, when, from where,
 * and what changed.
 *
 * <p>The {@code details} column stores a freeform JSON-serialisable string
 * (e.g. the entity IDs affected by a bulk-replace operation) so that the
 * table schema never needs to change when new entity types are added.
 */
@Entity
@Table(name = "audit_log")
public class AuditLogEntity {

    @Id
    private UUID id;

    /** UTC timestamp of the event. */
    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    /** Username of the authenticated actor, or {@code "ANONYMOUS"} when no security context is present. */
    @Column(nullable = false, length = 100)
    private String username;

    /** IPv4 or IPv6 address of the originating request. */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /**
     * Action identifier, e.g. {@code "RESOURCE_COST_CREATED"},
     * {@code "ALLOCATION_RULES_REPLACED"}, {@code "ALLOCATION_RUN_EXECUTED"}.
     */
    @Column(nullable = false, length = 100)
    private String action;

    /** The entity type affected, e.g. {@code "ResourceCost"}, {@code "AllocationRule"}. */
    @Column(name = "entity_type", length = 100)
    private String entityType;

    /** The entity's primary key (UUID string), or {@code null} for collection operations. */
    @Column(name = "entity_id", length = 255)
    private String entityId;

    /** Free-form detail string (short JSON or human-readable summary). Max 2000 chars. */
    @Column(length = 2000)
    private String details;

    /** Per-request correlation ID for joining audit entries to application logs. */
    @Column(name = "correlation_id", length = 36)
    private String correlationId;

    protected AuditLogEntity() {
        // JPA / persistence adapter
    }

    @SuppressWarnings({"checkstyle:ParameterNumber", "java:S107"})
    public AuditLogEntity(UUID id, Instant timestamp, String username, String ipAddress,
                          String action, String entityType, String entityId,
                          String details, String correlationId) {
        this.id = id;
        this.timestamp = timestamp;
        this.username = username;
        this.ipAddress = ipAddress;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.details = details;
        this.correlationId = correlationId;
    }

    public UUID getId() {
        return id;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getUsername() {
        return username;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getDetails() {
        return details;
    }

    public String getCorrelationId() {
        return correlationId;
    }
}
