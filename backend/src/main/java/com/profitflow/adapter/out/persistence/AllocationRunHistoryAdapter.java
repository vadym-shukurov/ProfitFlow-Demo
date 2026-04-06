package com.profitflow.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.profitflow.adapter.out.persistence.entity.AllocationRunEntity;
import com.profitflow.adapter.out.persistence.entity.DomainEventOutboxEntity;
import com.profitflow.adapter.out.persistence.jpa.AllocationRunEntityRepository;
import com.profitflow.adapter.out.persistence.jpa.DomainEventOutboxEntityRepository;
import com.profitflow.application.model.AllocationRunResult;
import com.profitflow.application.port.out.AllocationRunHistoryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists allocation run snapshots and outbox rows in the same transaction.
 */
@Component
public class AllocationRunHistoryAdapter implements AllocationRunHistoryPort {

    private final AllocationRunEntityRepository      runRepository;
    private final DomainEventOutboxEntityRepository  outboxRepository;
    private final ObjectMapper                       objectMapper;

    public AllocationRunHistoryAdapter(
            AllocationRunEntityRepository runRepository,
            DomainEventOutboxEntityRepository outboxRepository,
            ObjectMapper objectMapper) {
        this.runRepository     = runRepository;
        this.outboxRepository  = outboxRepository;
        this.objectMapper      = objectMapper;
    }

    @Override
    @Transactional
    public void recordSuccessfulRun(
            String executedBy,
            String inputSnapshotHash,
            AllocationRunResult result) {

        AllocationRunEntity row = new AllocationRunEntity();
        row.setExecutedBy(executedBy);
        row.setInputSnapshotHash(inputSnapshotHash);
        try {
            row.setResultJson(objectMapper.writeValueAsString(result));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise allocation run result", e);
        }

        AllocationRunEntity saved = runRepository.saveAndFlush(row);

        DomainEventOutboxEntity evt = new DomainEventOutboxEntity();
        evt.setEventType("ALLOCATION_RUN_COMPLETED");
        evt.setPayloadJson("{\"runNumber\":" + saved.getRunNumber() + "}");
        outboxRepository.save(evt);
    }
}
