package com.profitflow.adapter.out.persistence.jpa;

import com.profitflow.adapter.out.persistence.entity.DomainEventOutboxEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface DomainEventOutboxEntityRepository extends JpaRepository<DomainEventOutboxEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM DomainEventOutboxEntity e WHERE e.publishedAt IS NULL ORDER BY e.createdAt ASC")
    List<DomainEventOutboxEntity> findPendingLocked(Pageable pageable);
}
