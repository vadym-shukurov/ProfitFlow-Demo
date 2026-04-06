package com.profitflow.adapter.out.persistence;

import com.profitflow.adapter.out.persistence.entity.ActivityEntity;
import com.profitflow.adapter.out.persistence.entity.ProductEntity;
import com.profitflow.adapter.out.persistence.entity.ResourceCostEntity;
import com.profitflow.adapter.out.persistence.jpa.ActivityEntityRepository;
import com.profitflow.adapter.out.persistence.jpa.ActivityProductRuleEntityRepository;
import com.profitflow.adapter.out.persistence.jpa.ProductEntityRepository;
import com.profitflow.adapter.out.persistence.jpa.ResourceActivityRuleEntityRepository;
import com.profitflow.adapter.out.persistence.jpa.ResourceCostEntityRepository;
import com.profitflow.application.model.ActivityProductRuleRow;
import com.profitflow.application.model.ResourceActivityRuleRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ActivityProductRulePersistenceAdapter} and
 * {@link ResourceActivityRulePersistenceAdapter}.
 *
 * <p>Exercises the mapping logic and the "entity not found" error paths.
 */
@ExtendWith(MockitoExtension.class)
class AllocationRulePersistenceAdapterTest {

    @Mock private ActivityProductRuleEntityRepository apRuleRepo;
    @Mock private ActivityEntityRepository            activityRepo;
    @Mock private ProductEntityRepository             productRepo;

    @Mock private ResourceActivityRuleEntityRepository raRuleRepo;
    @Mock private ResourceCostEntityRepository         costRepo;

    private ActivityProductRulePersistenceAdapter apAdapter;
    private ResourceActivityRulePersistenceAdapter raAdapter;

    @BeforeEach
    void setUp() {
        apAdapter = new ActivityProductRulePersistenceAdapter(apRuleRepo, activityRepo, productRepo);
        raAdapter = new ResourceActivityRulePersistenceAdapter(raRuleRepo, costRepo, activityRepo);
    }

    // ── ActivityProductRulePersistenceAdapter ─────────────────────────────────

    @Test
    void apFindAllRowsReturnsEmptyList() {
        when(apRuleRepo.findAllWithAssociations()).thenReturn(List.of());
        assertThat(apAdapter.findAllRows()).isEmpty();
    }

    @Test
    void apReplaceAllPersistsRulesWhenEntitiesFound() {
        UUID activityId = UUID.randomUUID();
        UUID productId  = UUID.randomUUID();
        var activityEntity = new ActivityEntity(activityId, "Support");
        var productEntity  = new ProductEntity(productId, "ProductX");

        when(activityRepo.findById(activityId)).thenReturn(Optional.of(activityEntity));
        when(productRepo.findById(productId)).thenReturn(Optional.of(productEntity));
        when(apRuleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        apAdapter.replaceAll(List.of(
                new ActivityProductRuleRow(activityId.toString(), productId.toString(),
                        new BigDecimal("0.75"))));

        verify(apRuleRepo).deleteAllInBatch();
        verify(apRuleRepo).save(any());
    }

    @Test
    void apReplaceAllThrowsWhenActivityNotFound() {
        UUID activityId = UUID.randomUUID();
        UUID productId  = UUID.randomUUID();

        when(activityRepo.findById(activityId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apAdapter.replaceAll(List.of(
                new ActivityProductRuleRow(activityId.toString(), productId.toString(),
                        BigDecimal.ONE))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(activityId.toString());
    }

    @Test
    void apReplaceAllThrowsWhenProductNotFound() {
        UUID activityId = UUID.randomUUID();
        UUID productId  = UUID.randomUUID();
        var activityEntity = new ActivityEntity(activityId, "Support");

        when(activityRepo.findById(activityId)).thenReturn(Optional.of(activityEntity));
        when(productRepo.findById(productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apAdapter.replaceAll(List.of(
                new ActivityProductRuleRow(activityId.toString(), productId.toString(),
                        BigDecimal.ONE))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(productId.toString());
    }

    // ── ResourceActivityRulePersistenceAdapter ────────────────────────────────

    @Test
    void raFindAllRowsReturnsEmptyList() {
        when(raRuleRepo.findAllWithAssociations()).thenReturn(List.of());
        assertThat(raAdapter.findAllRows()).isEmpty();
    }

    @Test
    void raReplaceAllPersistsRulesWhenEntitiesFound() {
        UUID costId     = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();
        var costEntity     = new ResourceCostEntity(costId, "IT Infra", new BigDecimal("1000"), "USD");
        var activityEntity = new ActivityEntity(activityId, "Infra");

        when(costRepo.findById(costId)).thenReturn(Optional.of(costEntity));
        when(activityRepo.findById(activityId)).thenReturn(Optional.of(activityEntity));
        when(raRuleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        raAdapter.replaceAll(List.of(
                new ResourceActivityRuleRow(costId.toString(), activityId.toString(),
                        new BigDecimal("0.5"))));

        verify(raRuleRepo).deleteAllInBatch();
        verify(raRuleRepo).save(any());
    }

    @Test
    void raReplaceAllThrowsWhenCostNotFound() {
        UUID costId     = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();

        when(costRepo.findById(costId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> raAdapter.replaceAll(List.of(
                new ResourceActivityRuleRow(costId.toString(), activityId.toString(),
                        BigDecimal.ONE))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(costId.toString());
    }

    @Test
    void raReplaceAllThrowsWhenActivityNotFound() {
        UUID costId     = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();
        var costEntity = new ResourceCostEntity(costId, "IT Infra", new BigDecimal("1000"), "USD");

        when(costRepo.findById(costId)).thenReturn(Optional.of(costEntity));
        when(activityRepo.findById(activityId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> raAdapter.replaceAll(List.of(
                new ResourceActivityRuleRow(costId.toString(), activityId.toString(),
                        BigDecimal.ONE))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(activityId.toString());
    }
}
