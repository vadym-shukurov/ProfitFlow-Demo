package com.profitflow.domain.allocation;

import com.profitflow.domain.ActivityStageInput;
import com.profitflow.domain.AllocationResult;
import com.profitflow.domain.Money;
import com.profitflow.domain.ResourceCost;
import com.profitflow.domain.ResourceStageInput;
import com.profitflow.domain.exception.AllocationDomainException;
import com.profitflow.domain.flow.ActivityNode;
import com.profitflow.domain.flow.CostFlow;
import com.profitflow.domain.flow.ProductNode;
import com.profitflow.domain.flow.ResourceNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Stateless, two-stage Activity-Based Costing (ABC) allocation engine.
 *
 * <h2>Stage 1 — Resources → Activities</h2>
 * Each {@link ResourceStageInput} is proportionally split across one or more activities
 * using {@link ProportionalAllocator}. Activity totals are accumulated before
 * stage 2 runs, so a single activity can receive cost from multiple resource lines.
 *
 * <h2>Stage 2 — Activities → Products</h2>
 * Every activity that received a non-zero allocation in stage 1 must have a matching
 * {@link ActivityStageInput} in {@code productStage}; otherwise an
 * {@link AllocationDomainException} is thrown. The loaded activity cost is then split
 * across the configured products.
 *
 * <h2>Cash conservation</h2>
 * By delegating to {@link ProportionalAllocator#split} (which guarantees the output
 * sums to the input), the total product cost equals the total resource cost.
 *
 * <h2>Thread safety</h2>
 * The engine is fully stateless. All input lists must be non-null and non-empty where
 * required; the outputs are unmodifiable.
 */
public final class AllocationEngine {

    private AllocationEngine() {
        // utility class — no instances
    }

    /**
     * Executes the two-stage allocation.
     *
     * @param resourceStage stage-1 inputs: one entry per resource line, each carrying
     *                      a non-empty list of activity driver shares
     * @param productStage  stage-2 inputs: one entry per activity, each carrying a
     *                      non-empty list of product driver shares; must cover every
     *                      activity that receives non-zero cost in stage 1
     * @return immutable {@link AllocationResult} containing activity totals, product
     *         totals, and the full directed cost-flow graph (for Sankey visualisation)
     * @throws AllocationDomainException if a funded activity has no product-stage entry,
     *                                   or if duplicate activity IDs exist in
     *                                   {@code productStage}
     * @throws NullPointerException      if either argument is null
     */
    public static AllocationResult allocate(
            List<ResourceStageInput> resourceStage,
            List<ActivityStageInput> productStage) {

        Objects.requireNonNull(resourceStage, "resourceStage must not be null");
        Objects.requireNonNull(productStage, "productStage must not be null");

        Map<String, ActivityStageInput> stage2ByActivityId = indexProductStage(productStage);

        Map<String, Money> activityTotals = new HashMap<>();
        List<CostFlow> flows = new ArrayList<>();

        // Stage 1: spread each resource cost into activities
        for (ResourceStageInput rs : resourceStage) {
            ResourceCost rc = rs.resource();
            Map<String, Money> toActivities =
                    ProportionalAllocator.split(rc.amount(), rs.toActivities());

            for (var entry : toActivities.entrySet()) {
                String activityId = entry.getKey();
                Money allocated = entry.getValue();
                activityTotals.merge(activityId, allocated, Money::add);
                flows.add(new CostFlow(
                        new ResourceNode(rc.id()),
                        new ActivityNode(activityId),
                        allocated));
            }
        }

        Map<String, Money> productTotals = new HashMap<>();

        // Stage 2: spread each activity's loaded cost into products
        for (var entry : activityTotals.entrySet()) {
            String activityId = entry.getKey();
            Money activityCost = entry.getValue();

            if (activityCost.isZero()) {
                // Zero-cost activities produce no product flows
                continue;
            }

            ActivityStageInput spec = stage2ByActivityId.get(activityId);
            if (spec == null) {
                throw new AllocationDomainException(
                        "Activity '" + activityId + "' received cost in stage 1 but has no "
                        + "product allocation rule in stage 2. "
                        + "Add an activity→product rule to continue.");
            }

            Map<String, Money> toProducts =
                    ProportionalAllocator.split(activityCost, spec.toProducts());

            for (var productEntry : toProducts.entrySet()) {
                String productId = productEntry.getKey();
                Money productAmt = productEntry.getValue();
                productTotals.merge(productId, productAmt, Money::add);
                flows.add(new CostFlow(
                        new ActivityNode(activityId),
                        new ProductNode(productId),
                        productAmt));
            }
        }

        return new AllocationResult(
                Map.copyOf(activityTotals),
                Map.copyOf(productTotals),
                List.copyOf(flows));
    }

    /**
     * Indexes {@code productStage} by activity ID and detects duplicates.
     */
    private static Map<String, ActivityStageInput> indexProductStage(
            List<ActivityStageInput> productStage) {

        Map<String, ActivityStageInput> index = HashMap.newHashMap(productStage.size());
        for (ActivityStageInput input : productStage) {
            ActivityStageInput previous = index.putIfAbsent(input.activityId(), input);
            if (previous != null) {
                throw new AllocationDomainException(
                        "Duplicate product-stage entry for activity: '" + input.activityId() + "'");
            }
        }
        return index;
    }
}
