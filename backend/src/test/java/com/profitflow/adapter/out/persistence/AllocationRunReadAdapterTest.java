package com.profitflow.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.profitflow.adapter.out.persistence.entity.AllocationRunEntity;
import com.profitflow.adapter.out.persistence.jpa.AllocationRunEntityRepository;
import com.profitflow.application.model.AllocationRunResult;
import com.profitflow.application.model.AllocationRunSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AllocationRunReadAdapterTest {

    @Mock
    private AllocationRunEntityRepository repository;

    @Mock
    private ObjectMapper objectMapper;

    private AllocationRunReadAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new AllocationRunReadAdapter(repository, objectMapper);
    }

    @Test
    void findRecent_mapsEntitiesToSummariesInDescendingRunOrder() {
        AllocationRunEntity e1 = new AllocationRunEntity();
        e1.setExecutedBy("alice");
        e1.setInputSnapshotHash("hash-10");
        setField(e1, "runNumber", 10L);
        setField(e1, "executedAt", Instant.parse("2026-04-08T10:00:00Z"));

        AllocationRunEntity e2 = new AllocationRunEntity();
        e2.setExecutedBy("bob");
        e2.setInputSnapshotHash("hash-9");
        setField(e2, "runNumber", 9L);
        setField(e2, "executedAt", Instant.parse("2026-04-08T09:00:00Z"));

        when(repository.findAllByOrderByRunNumberDesc(PageRequest.of(0, 2)))
                .thenReturn(new PageImpl<>(List.of(e1, e2)));

        List<AllocationRunSummary> summaries = adapter.findRecent(2);

        assertThat(summaries).containsExactly(
                new AllocationRunSummary(10L, e1.getExecutedAt(), "alice", "hash-10"),
                new AllocationRunSummary(9L, e2.getExecutedAt(), "bob", "hash-9")
        );
    }

    @Test
    void findResultByRunNumber_returnsEmptyWhenRunMissing() {
        when(repository.findById(123L)).thenReturn(Optional.empty());

        assertThat(adapter.findResultByRunNumber(123L)).isEmpty();
    }

    @Test
    void findResultByRunNumber_parsesJsonIntoAllocationRunResult() throws Exception {
        AllocationRunEntity row = new AllocationRunEntity();
        row.setResultJson("{\"schemaVersion\":1}");
        setField(row, "runNumber", 7L);

        AllocationRunResult parsed = new AllocationRunResult(
                java.util.Map.of(),
                java.util.Map.of(),
                List.of(),
                List.of());

        when(repository.findById(7L)).thenReturn(Optional.of(row));
        when(objectMapper.readValue(row.getResultJson(), AllocationRunResult.class)).thenReturn(parsed);

        assertThat(adapter.findResultByRunNumber(7L)).containsSame(parsed);
    }

    @Test
    void findResultByRunNumber_throwsIllegalStateWhenJsonIsCorrupt() throws Exception {
        AllocationRunEntity row = new AllocationRunEntity();
        row.setResultJson("{not-json}");
        setField(row, "runNumber", 42L);

        when(repository.findById(42L)).thenReturn(Optional.of(row));
        when(objectMapper.readValue(row.getResultJson(), AllocationRunResult.class))
                .thenThrow(new JsonProcessingException("bad") { });

        assertThatThrownBy(() -> adapter.findResultByRunNumber(42L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Corrupt allocation_run result_json for run 42");
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Test setup failed: could not set field " + fieldName, e);
        }
    }
}

