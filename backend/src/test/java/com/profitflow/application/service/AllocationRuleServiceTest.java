package com.profitflow.application.service;

import com.profitflow.application.model.ActivityProductRuleRow;
import com.profitflow.application.model.ResourceActivityRuleRow;
import com.profitflow.application.port.out.ActivityProductRuleRepositoryPort;
import com.profitflow.application.port.out.ResourceActivityRuleRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AllocationRuleService}.
 */
@ExtendWith(MockitoExtension.class)
class AllocationRuleServiceTest {

    @Mock
    private ResourceActivityRuleRepositoryPort resourceActivityRuleRepo;

    @Mock
    private ActivityProductRuleRepositoryPort activityProductRuleRepo;

    private AllocationRuleService service;

    @BeforeEach
    void setUp() {
        service = new AllocationRuleService(resourceActivityRuleRepo, activityProductRuleRepo);
    }

    // ── Resource → Activity rules ────────────────────────────────────────────

    @Test
    void listResourceToActivityRulesDelegatesToRepository() {
        List<ResourceActivityRuleRow> expected = List.of(
                new ResourceActivityRuleRow("r1", "a1", BigDecimal.ONE));
        when(resourceActivityRuleRepo.findAllRows()).thenReturn(expected);

        assertThat(service.listResourceToActivityRules()).isEqualTo(expected);
    }

    @Test
    void replaceResourceToActivityRulesDelegatesToRepository() {
        List<ResourceActivityRuleRow> rules = List.of(
                new ResourceActivityRuleRow("r1", "a1", new BigDecimal("0.6")),
                new ResourceActivityRuleRow("r1", "a2", new BigDecimal("0.4")));

        service.replaceResourceToActivityRules(rules);

        ArgumentCaptor<List<ResourceActivityRuleRow>> captor = ArgumentCaptor.captor();
        verify(resourceActivityRuleRepo).replaceAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
    }

    @Test
    void replaceResourceToActivityRulesWithNullUsesEmptyList() {
        service.replaceResourceToActivityRules(null);

        ArgumentCaptor<List<ResourceActivityRuleRow>> captor = ArgumentCaptor.captor();
        verify(resourceActivityRuleRepo).replaceAll(captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }

    // ── Activity → Product rules ─────────────────────────────────────────────

    @Test
    void listActivityToProductRulesDelegatesToRepository() {
        List<ActivityProductRuleRow> expected = List.of(
                new ActivityProductRuleRow("a1", "p1", BigDecimal.ONE));
        when(activityProductRuleRepo.findAllRows()).thenReturn(expected);

        assertThat(service.listActivityToProductRules()).isEqualTo(expected);
    }

    @Test
    void replaceActivityToProductRulesDelegatesToRepository() {
        List<ActivityProductRuleRow> rules = List.of(
                new ActivityProductRuleRow("a1", "p1", new BigDecimal("0.5")),
                new ActivityProductRuleRow("a1", "p2", new BigDecimal("0.5")));

        service.replaceActivityToProductRules(rules);

        ArgumentCaptor<List<ActivityProductRuleRow>> captor = ArgumentCaptor.captor();
        verify(activityProductRuleRepo).replaceAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
    }

    @Test
    void replaceActivityToProductRulesWithNullUsesEmptyList() {
        service.replaceActivityToProductRules(null);

        ArgumentCaptor<List<ActivityProductRuleRow>> captor = ArgumentCaptor.captor();
        verify(activityProductRuleRepo).replaceAll(captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }
}
