package com.profitflow.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.profitflow.adapter.out.persistence.entity.AllocationRunEntity;
import com.profitflow.adapter.out.persistence.jpa.AllocationRunEntityRepository;
import com.profitflow.application.model.AllocationRunResult;
import com.profitflow.application.model.AllocationRunSummary;
import com.profitflow.application.port.out.AllocationRunReadPort;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * JPA-backed read model for allocation run history.
 */
@Component
public class AllocationRunReadAdapter implements AllocationRunReadPort {

    private final AllocationRunEntityRepository runRepository;
    private final ObjectMapper                  objectMapper;

    public AllocationRunReadAdapter(
            AllocationRunEntityRepository runRepository,
            ObjectMapper objectMapper) {
        this.runRepository = runRepository;
        this.objectMapper  = objectMapper;
    }

    @Override
    public List<AllocationRunSummary> findRecent(int limit) {
        return runRepository.findAllByOrderByRunNumberDesc(PageRequest.of(0, limit))
                .getContent()
                .stream()
                .map(AllocationRunReadAdapter::toSummary)
                .toList();
    }

    @Override
    public Optional<AllocationRunResult> findResultByRunNumber(long runNumber) {
        return runRepository.findById(runNumber).map(this::parseResult);
    }

    private static AllocationRunSummary toSummary(AllocationRunEntity e) {
        return new AllocationRunSummary(
                e.getRunNumber(),
                e.getExecutedAt(),
                e.getExecutedBy(),
                e.getInputSnapshotHash());
    }

    private AllocationRunResult parseResult(AllocationRunEntity e) {
        try {
            return objectMapper.readValue(e.getResultJson(), AllocationRunResult.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(
                    "Corrupt allocation_run result_json for run " + e.getRunNumber(), ex);
        }
    }
}
