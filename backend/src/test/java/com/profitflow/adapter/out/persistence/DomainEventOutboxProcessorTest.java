package com.profitflow.adapter.out.persistence;

import com.profitflow.adapter.out.persistence.entity.DomainEventOutboxEntity;
import com.profitflow.adapter.out.persistence.jpa.DomainEventOutboxEntityRepository;
import com.profitflow.application.port.out.DomainEventDispatchPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DomainEventOutboxProcessorTest {

    @Mock
    private DomainEventOutboxEntityRepository outboxRepository;
    @Mock
    private DomainEventDispatchPort dispatch;

    @InjectMocks
    private DomainEventOutboxProcessor processor;

    @Test
    void dispatchesEachRowAndSetsPublishedAt() {
        DomainEventOutboxEntity row = new DomainEventOutboxEntity();
        row.setEventType("ALLOCATION_RUN_COMPLETED");
        row.setPayloadJson("{\"runNumber\":1}");
        when(outboxRepository.findPendingLocked(any(Pageable.class))).thenReturn(List.of(row));

        processor.processPendingBatch();

        verify(dispatch).dispatch("ALLOCATION_RUN_COMPLETED", "{\"runNumber\":1}");
        assertThat(row.getPublishedAt()).isNotNull();
        verify(outboxRepository).save(row);
    }
}
