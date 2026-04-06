package com.profitflow.application.service;

import com.profitflow.application.port.out.AllocationRunReadPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AllocationRunQueryServiceTest {

    @Mock
    private AllocationRunReadPort readPort;

    private AllocationRunQueryService service;

    @BeforeEach
    void setUp() {
        service = new AllocationRunQueryService(readPort);
    }

    @Test
    void listRecentRunsClampsHighLimitTo100() {
        service.listRecentRuns(500);
        verify(readPort).findRecent(100);
    }

    @Test
    void listRecentRunsClampsNonPositiveTo1() {
        service.listRecentRuns(0);
        verify(readPort).findRecent(1);
    }

    @Test
    void listRecentRunsPassesBoundedValueThrough() {
        service.listRecentRuns(25);
        verify(readPort).findRecent(25);
    }

    @Test
    void getRunDelegatesToReadPort() {
        service.getRun(42L);
        verify(readPort).findResultByRunNumber(42L);
    }
}
