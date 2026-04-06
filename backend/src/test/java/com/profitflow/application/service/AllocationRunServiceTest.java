package com.profitflow.application.service;

import com.profitflow.application.model.ActivityProductRuleRow;
import com.profitflow.application.model.AllocationRunResult;
import com.profitflow.application.model.ResourceActivityRuleRow;
import com.profitflow.application.exception.ResourceConflictException;
import com.profitflow.application.port.out.ActivityProductRuleRepositoryPort;
import com.profitflow.application.port.out.AllocationExecutionPolicyPort;
import com.profitflow.application.port.out.AllocationRunHistoryPort;
import com.profitflow.application.port.out.ResourceActivityRuleRepositoryPort;
import com.profitflow.application.port.out.ResourceCostRepositoryPort;
import com.profitflow.application.port.out.CurrentUserPort;
import com.profitflow.domain.Money;
import com.profitflow.domain.ResourceCost;
import com.profitflow.domain.exception.AllocationDomainException;
import com.profitflow.application.port.out.BusinessMetricsPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AllocationRunService}.
 *
 * <p>Verifies orchestration logic (coverage detection, unallocated tracking, DTO mapping)
 * without a Spring context.
 */
@ExtendWith(MockitoExtension.class)
class AllocationRunServiceTest {

    @Mock
    private ResourceCostRepositoryPort resourceCostRepo;
    @Mock
    private ResourceActivityRuleRepositoryPort raRuleRepo;
    @Mock
    private ActivityProductRuleRepositoryPort apRuleRepo;
    @Mock
    private BusinessMetricsPort metrics;
    @Mock
    private AllocationRunHistoryPort runHistory;
    @Mock
    private CurrentUserPort currentUser;
    @Mock
    private AllocationExecutionPolicyPort executionPolicy;

    private AllocationRunService service;

    @BeforeEach
    void setUp() {
        lenient().when(currentUser.currentUsernameOrSystem()).thenReturn("test-user");
        service = new AllocationRunService(
                resourceCostRepo, raRuleRepo, apRuleRepo, metrics, runHistory, currentUser,
                executionPolicy);
    }

    @Test
    void successfulRunReturnsMappedFlowsAndCosts() {
        ResourceCost res = new ResourceCost("r1", "IT Servers",
                Money.usd(new BigDecimal("10000.00")));
        when(resourceCostRepo.findAll()).thenReturn(List.of(res));
        when(raRuleRepo.findAllRows()).thenReturn(List.of(
                new ResourceActivityRuleRow("r1", "a1", new BigDecimal("1"))));
        when(apRuleRepo.findAllRows()).thenReturn(List.of(
                new ActivityProductRuleRow("a1", "p1", new BigDecimal("1"))));

        AllocationRunResult result = service.runAllocation();

        assertThat(result.activityCosts().get("a1"))
                .isEqualByComparingTo(new BigDecimal("10000.00"));
        assertThat(result.productCosts().get("p1"))
                .isEqualByComparingTo(new BigDecimal("10000.00"));
        assertThat(result.flows()).hasSize(2);
        assertThat(result.unallocatedResourceIds()).isEmpty();
        verify(runHistory).recordSuccessfulRun(anyString(), anyString(), any(AllocationRunResult.class));
        verify(executionPolicy).assertMayExecute("test-user");
    }

    @Test
    void resourceWithoutRuleAppearsInUnallocatedList() {
        ResourceCost rWithRule = new ResourceCost("r1", "With Rule",
                Money.usd(new BigDecimal("1000.00")));
        ResourceCost rNoRule = new ResourceCost("r2", "No Rule",
                Money.usd(new BigDecimal("500.00")));

        when(resourceCostRepo.findAll()).thenReturn(List.of(rWithRule, rNoRule));
        when(raRuleRepo.findAllRows()).thenReturn(List.of(
                new ResourceActivityRuleRow("r1", "a1", BigDecimal.ONE)));
        when(apRuleRepo.findAllRows()).thenReturn(List.of(
                new ActivityProductRuleRow("a1", "p1", BigDecimal.ONE)));

        AllocationRunResult result = service.runAllocation();

        assertThat(result.unallocatedResourceIds()).containsExactly("r2");
    }

    @Test
    void zeroAmountResourceNotFlaggedAsUnallocated() {
        ResourceCost zeroRes = new ResourceCost("r-zero", "Cancelled", Money.zeroUsd());
        when(resourceCostRepo.findAll()).thenReturn(List.of(zeroRes));
        when(raRuleRepo.findAllRows()).thenReturn(List.of());
        when(apRuleRepo.findAllRows()).thenReturn(List.of());

        AllocationRunResult result = service.runAllocation();

        assertThat(result.unallocatedResourceIds()).isEmpty();
    }

    @Test
    void activityWithoutProductRuleThrowsAllocationDomainException() {
        ResourceCost res = new ResourceCost("r1", "GL", Money.usd(new BigDecimal("100.00")));
        when(resourceCostRepo.findAll()).thenReturn(List.of(res));
        when(raRuleRepo.findAllRows()).thenReturn(List.of(
                new ResourceActivityRuleRow("r1", "act-orphan", BigDecimal.ONE)));
        when(apRuleRepo.findAllRows()).thenReturn(List.of()); // no product rules!

        assertThatThrownBy(() -> service.runAllocation())
                .isInstanceOf(AllocationDomainException.class)
                .hasMessageContaining("act-orphan");
    }

    @Test
    void successfulRunRecordsSuccessMetric() {
        ResourceCost res = new ResourceCost("r1", "GL", Money.usd(new BigDecimal("100.00")));
        when(resourceCostRepo.findAll()).thenReturn(List.of(res));
        when(raRuleRepo.findAllRows()).thenReturn(List.of(
                new ResourceActivityRuleRow("r1", "a1", BigDecimal.ONE)));
        when(apRuleRepo.findAllRows()).thenReturn(List.of(
                new ActivityProductRuleRow("a1", "p1", BigDecimal.ONE)));

        service.runAllocation();

        verify(metrics).recordAllocationSuccess(anyLong());
    }

    @Test
    void executionPolicyRejectionRecordsFailureAndSkipsHistory() {
        doThrow(new ResourceConflictException("approval required"))
                .when(executionPolicy).assertMayExecute(anyString());

        assertThatThrownBy(() -> service.runAllocation())
                .isInstanceOf(ResourceConflictException.class);

        verify(metrics).recordAllocationFailure();
        verifyNoInteractions(runHistory);
        verifyNoInteractions(resourceCostRepo);
    }

    @Test
    void failedRunRecordsFailureMetric() {
        ResourceCost res = new ResourceCost("r1", "GL", Money.usd(new BigDecimal("100.00")));
        when(resourceCostRepo.findAll()).thenReturn(List.of(res));
        when(raRuleRepo.findAllRows()).thenReturn(List.of(
                new ResourceActivityRuleRow("r1", "act-orphan", BigDecimal.ONE)));
        when(apRuleRepo.findAllRows()).thenReturn(List.of());

        assertThatThrownBy(() -> service.runAllocation())
                .isInstanceOf(AllocationDomainException.class);

        verify(metrics).recordAllocationFailure();
        verifyNoInteractions(runHistory);
    }

    @Test
    void flowDtoKindFieldsAreUpperCaseStrings() {
        ResourceCost res = new ResourceCost("r1", "GL", Money.usd(new BigDecimal("100.00")));
        when(resourceCostRepo.findAll()).thenReturn(List.of(res));
        when(raRuleRepo.findAllRows()).thenReturn(List.of(
                new ResourceActivityRuleRow("r1", "a1", BigDecimal.ONE)));
        when(apRuleRepo.findAllRows()).thenReturn(List.of(
                new ActivityProductRuleRow("a1", "p1", BigDecimal.ONE)));

        AllocationRunResult result = service.runAllocation();

        var resourceFlow = result.flows().stream()
                .filter(f -> "RESOURCE".equals(f.fromKind())).findFirst().orElseThrow();
        assertThat(resourceFlow.fromKind()).isEqualTo("RESOURCE");
        assertThat(resourceFlow.toKind()).isEqualTo("ACTIVITY");

        var productFlow = result.flows().stream()
                .filter(f -> "PRODUCT".equals(f.toKind())).findFirst().orElseThrow();
        assertThat(productFlow.fromKind()).isEqualTo("ACTIVITY");
        assertThat(productFlow.toKind()).isEqualTo("PRODUCT");
    }
}
