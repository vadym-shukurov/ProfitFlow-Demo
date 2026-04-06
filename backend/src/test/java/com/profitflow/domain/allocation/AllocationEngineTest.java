package com.profitflow.domain.allocation;

import com.profitflow.domain.ActivityStageInput;
import com.profitflow.domain.AllocationResult;
import com.profitflow.domain.DriverShare;
import com.profitflow.domain.Money;
import com.profitflow.domain.ResourceCost;
import com.profitflow.domain.ResourceStageInput;
import com.profitflow.domain.exception.AllocationDomainException;
import com.profitflow.domain.flow.ActivityNode;
import com.profitflow.domain.flow.CostFlow;
import com.profitflow.domain.flow.FlowNodeKind;
import com.profitflow.domain.flow.ProductNode;
import com.profitflow.domain.flow.ResourceNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AllocationEngine}.
 *
 * <p>Each test covers a specific cash-conservation or error-path scenario.
 */
class AllocationEngineTest {

    // -------------------------------------------------------------------------
    // Happy-path scenarios
    // -------------------------------------------------------------------------

    @Test
    void endToEndConservesCashAndBuildsFlows() {
        // IT Servers $10,000 split 1:3 between Support and Sales
        ResourceCost it = new ResourceCost("res-it", "IT servers",
                Money.usd(new BigDecimal("10000.00")));

        List<ResourceStageInput> rStage = List.of(
                new ResourceStageInput(it, List.of(
                        new DriverShare("act-support", BigDecimal.ONE),
                        new DriverShare("act-sales", new BigDecimal("3")))));

        List<ActivityStageInput> pStage = List.of(
                new ActivityStageInput("act-support", List.of(
                        new DriverShare("prod-a", BigDecimal.ONE),
                        new DriverShare("prod-b", BigDecimal.ONE))),
                new ActivityStageInput("act-sales", List.of(
                        new DriverShare("prod-a", BigDecimal.ONE),
                        new DriverShare("prod-b", new BigDecimal("4")))));

        AllocationResult result = AllocationEngine.allocate(rStage, pStage);

        // Stage-1 totals
        assertThat(result.activityCosts().get("act-support").amount())
                .isEqualByComparingTo(new BigDecimal("2500.00"));
        assertThat(result.activityCosts().get("act-sales").amount())
                .isEqualByComparingTo(new BigDecimal("7500.00"));

        // Stage-2 totals
        assertThat(result.productCosts().get("prod-a").amount())
                .isEqualByComparingTo(new BigDecimal("2750.00")); // 1250 + 1500
        assertThat(result.productCosts().get("prod-b").amount())
                .isEqualByComparingTo(new BigDecimal("7250.00")); // 1250 + 6000

        // Cash conservation: sum(products) == total resource cost
        BigDecimal productSum = result.productCosts().values().stream()
                .map(Money::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(productSum).isEqualByComparingTo(new BigDecimal("10000.00"));

        // Flow graph shape: 2 resource→activity + 4 activity→product = 6 total
        assertThat(result.flows()).hasSize(6);
        assertThat(result.flows().stream().filter(f -> f.from() instanceof ResourceNode)).hasSize(2);
        assertThat(result.flows().stream().filter(f -> f.to() instanceof ProductNode)).hasSize(4);
    }

    @Test
    void multipleResourcesAggregateIntoSameActivity() {
        // Two different cost lines both point to the same activity
        ResourceCost gl1 = new ResourceCost("r1", "AWS", Money.usd(new BigDecimal("3000.00")));
        ResourceCost gl2 = new ResourceCost("r2", "Azure", Money.usd(new BigDecimal("2000.00")));

        List<ResourceStageInput> rStage = List.of(
                new ResourceStageInput(gl1, List.of(new DriverShare("infra", BigDecimal.ONE))),
                new ResourceStageInput(gl2, List.of(new DriverShare("infra", BigDecimal.ONE))));

        List<ActivityStageInput> pStage = List.of(
                new ActivityStageInput("infra", List.of(new DriverShare("p1", BigDecimal.ONE))));

        AllocationResult result = AllocationEngine.allocate(rStage, pStage);

        // Activity total = sum of both resources
        assertThat(result.activityCosts().get("infra").amount())
                .isEqualByComparingTo(new BigDecimal("5000.00"));

        // Product receives the full aggregated amount
        assertThat(result.productCosts().get("p1").amount())
                .isEqualByComparingTo(new BigDecimal("5000.00"));
    }

    @Test
    void zeroAmountResourceProducesNoProductFlow() {
        ResourceCost zero = new ResourceCost("r-zero", "Cancelled contract", Money.zeroUsd());
        List<ResourceStageInput> rStage = List.of(
                new ResourceStageInput(zero, List.of(new DriverShare("act", BigDecimal.ONE))));
        // act has no product stage — should not throw because it received zero
        List<ActivityStageInput> pStage = List.of();

        AllocationResult result = AllocationEngine.allocate(rStage, pStage);

        assertThat(result.productCosts()).isEmpty();
        // There is a resource→activity flow but it carries zero
        assertThat(result.flows()).hasSize(1);
        assertThat(result.flows().getFirst().amount().isZero()).isTrue();
    }

    @Test
    void flowNodesHaveCorrectKinds() {
        ResourceCost r = new ResourceCost("gl-1", "Zendesk", Money.usd(new BigDecimal("5000.00")));
        List<ResourceStageInput> rStage = List.of(
                new ResourceStageInput(r, List.of(new DriverShare("cust-support", BigDecimal.ONE))));
        List<ActivityStageInput> pStage = List.of(
                new ActivityStageInput("cust-support", List.of(
                        new DriverShare("svc-a", BigDecimal.ONE),
                        new DriverShare("svc-b", BigDecimal.ONE))));

        AllocationResult result = AllocationEngine.allocate(rStage, pStage);

        List<CostFlow> resourceFlows = result.flows().stream()
                .filter(f -> f.from() instanceof ResourceNode).toList();
        assertThat(resourceFlows).hasSize(1);
        assertThat(resourceFlows.getFirst().from().kind()).isEqualTo(FlowNodeKind.RESOURCE);
        assertThat(resourceFlows.getFirst().to().kind()).isEqualTo(FlowNodeKind.ACTIVITY);

        List<CostFlow> productFlows = result.flows().stream()
                .filter(f -> f.to() instanceof ProductNode).toList();
        assertThat(productFlows).hasSize(2);
        productFlows.forEach(f -> assertThat(f.to().kind()).isEqualTo(FlowNodeKind.PRODUCT));

        // Stage-1 flow conserves cash
        assertThat(resourceFlows.getFirst().amount().amount())
                .isEqualByComparingTo(new BigDecimal("5000.00"));

        // Stage-2 flows conserve cash
        BigDecimal toProducts = productFlows.stream()
                .map(CostFlow::amount)
                .map(Money::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(toProducts).isEqualByComparingTo(new BigDecimal("5000.00"));
    }

    // -------------------------------------------------------------------------
    // Error-path scenarios
    // -------------------------------------------------------------------------

    @Test
    void missingProductStageForFundedActivityFails() {
        ResourceCost cost = new ResourceCost("r1", "x", Money.usd(new BigDecimal("100.00")));
        List<ResourceStageInput> rStage = List.of(
                new ResourceStageInput(cost, List.of(new DriverShare("act-1", BigDecimal.ONE))));

        assertThatThrownBy(() -> AllocationEngine.allocate(rStage, List.of()))
                .isInstanceOf(AllocationDomainException.class)
                .hasMessageContaining("act-1");
    }

    @Test
    void duplicateActivityInProductStageRejected() {
        ResourceCost cost = new ResourceCost("r1", "x", Money.usd(BigDecimal.ONE));
        List<ResourceStageInput> rStage = List.of(
                new ResourceStageInput(cost, List.of(new DriverShare("a1", BigDecimal.ONE))));
        List<ActivityStageInput> pStage = List.of(
                new ActivityStageInput("a1", List.of(new DriverShare("p1", BigDecimal.ONE))),
                new ActivityStageInput("a1", List.of(new DriverShare("p2", BigDecimal.ONE))));

        assertThatThrownBy(() -> AllocationEngine.allocate(rStage, pStage))
                .isInstanceOf(AllocationDomainException.class)
                .hasMessageContaining("a1");
    }

    @Test
    void nullResourceStageRejected() {
        assertThatThrownBy(() -> AllocationEngine.allocate(null, List.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullProductStageRejected() {
        assertThatThrownBy(() -> AllocationEngine.allocate(List.of(), null))
                .isInstanceOf(NullPointerException.class);
    }
}
