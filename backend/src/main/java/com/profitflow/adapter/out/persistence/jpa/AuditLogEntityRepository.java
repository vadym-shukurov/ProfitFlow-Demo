package com.profitflow.adapter.out.persistence.jpa;

import com.profitflow.adapter.out.persistence.entity.AuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data repository for {@link AuditLogEntity}.
 *
 * <p>Only {@code save} and {@code findAll} operations should be used —
 * never {@code delete} or {@code deleteAll}. Audit records are immutable.
 */
public interface AuditLogEntityRepository extends JpaRepository<AuditLogEntity, UUID> {
}
