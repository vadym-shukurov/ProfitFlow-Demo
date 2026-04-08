package com.profitflow.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.profitflow.adapter.out.persistence.entity.AllocationRunEntity;
import com.profitflow.adapter.out.persistence.entity.DomainEventOutboxEntity;
import com.profitflow.adapter.out.persistence.jpa.AllocationRunEntityRepository;
import com.profitflow.adapter.out.persistence.jpa.DomainEventOutboxEntityRepository;
import com.profitflow.application.model.AllocationRunResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AllocationRunHistoryAdapterTest {

    @Mock
    private AllocationRunEntityRepository runRepository;

    @Mock
    private DomainEventOutboxEntityRepository outboxRepository;

    @Mock
    private ObjectMapper objectMapper;

    private AllocationRunHistoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new AllocationRunHistoryAdapter(runRepository, outboxRepository, objectMapper);
    }

    @Test
    void recordSuccessfulRun_persistsSnapshotAndOutboxRow() throws Exception {
        AllocationRunResult result = new AllocationRunResult(
                Map.of(),
                Map.of(),
                List.of(),
                List.of());

        when(objectMapper.writeValueAsString(result)).thenReturn("{\"schemaVersion\":1}");

        AllocationRunEntity saved = new AllocationRunEntity();
        setField(saved, "runNumber", 123L);

        when(runRepository.saveAndFlush(any())).thenReturn(saved);

        adapter.recordSuccessfulRun("alice", "hash-1", result);

        ArgumentCaptor<AllocationRunEntity> runCaptor = ArgumentCaptor.forClass(AllocationRunEntity.class);
        verify(runRepository).saveAndFlush(runCaptor.capture());
        AllocationRunEntity persisted = runCaptor.getValue();
        assertThat(persisted.getExecutedBy()).isEqualTo("alice");
        assertThat(persisted.getInputSnapshotHash()).isEqualTo("hash-1");
        assertThat(persisted.getResultJson()).isEqualTo("{\"schemaVersion\":1}");

        ArgumentCaptor<DomainEventOutboxEntity> evtCaptor = ArgumentCaptor.forClass(DomainEventOutboxEntity.class);
        verify(outboxRepository).save(evtCaptor.capture());
        DomainEventOutboxEntity evt = evtCaptor.getValue();
        assertThat(evt.getEventType()).isEqualTo("ALLOCATION_RUN_COMPLETED");
        assertThat(evt.getPayloadJson()).isEqualTo("{\"runNumber\":123}");
    }

    @Test
    void recordSuccessfulRun_throwsWhenResultCannotBeSerialised() throws Exception {
        AllocationRunResult result = new AllocationRunResult(
                Map.of(),
                Map.of(),
                List.of(),
                List.of());

        when(objectMapper.writeValueAsString(result))
                .thenThrow(new JsonProcessingException("nope") { });

        assertThatThrownBy(() -> adapter.recordSuccessfulRun("alice", "hash", result))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to serialise allocation run result");
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

