package com.profitflow.application.service;

import com.profitflow.application.audit.AuditedOperation;
import com.profitflow.application.model.ActivityProductRuleRow;
import com.profitflow.application.model.AllocationFlowDto;
import com.profitflow.application.model.AllocationRunResult;
import com.profitflow.application.model.ResourceActivityRuleRow;
import com.profitflow.application.port.in.AllocationRunUseCase;
import com.profitflow.application.port.out.ActivityProductRuleRepositoryPort;
import com.profitflow.application.port.out.AllocationExecutionPolicyPort;
import com.profitflow.application.port.out.AllocationRunHistoryPort;
import com.profitflow.application.port.out.BusinessMetricsPort;
import com.profitflow.application.port.out.CurrentUserPort;
import com.profitflow.application.port.out.ResourceActivityRuleRepositoryPort;
import com.profitflow.application.port.out.ResourceCostRepositoryPort;
import com.profitflow.domain.ActivityStageInput;
import com.profitflow.domain.AllocationResult;
import com.profitflow.domain.DriverShare;
import com.profitflow.domain.Money;
import com.profitflow.domain.ResourceCost;
import com.profitflow.domain.ResourceStageInput;
import com.profitflow.domain.allocation.AllocationEngine;
import com.profitflow.domain.exception.AllocationDomainException;
import com.profitflow.domain.flow.CostFlow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Orchestrates a full ABC allocation run: loads persisted rules and costs, delegates
 * the two-stage math to {@link AllocationEngine}, maps to DTOs, then persists a
 * versioned snapshot and outbox event for audit / downstream integration.
 */
@Service
public class AllocationRunService implements AllocationRunUseCase {

    private final ResourceCostRepositoryPort         resourceCosts;
    private final ResourceActivityRuleRepositoryPort resourceActivityRules;
    private final ActivityProductRuleRepositoryPort  activityProductRules;
    private final BusinessMetricsPort                metrics;
    private final AllocationRunHistoryPort         runHistory;
    private final CurrentUserPort                  currentUser;
    private final AllocationExecutionPolicyPort    executionPolicy;

    public AllocationRunService(
            ResourceCostRepositoryPort resourceCosts,
            ResourceActivityRuleRepositoryPort resourceActivityRules,
            ActivityProductRuleRepositoryPort activityProductRules,
            BusinessMetricsPort metrics,
            AllocationRunHistoryPort runHistory,
            CurrentUserPort currentUser,
            AllocationExecutionPolicyPort executionPolicy) {
        this.resourceCosts         = resourceCosts;
        this.resourceActivityRules = resourceActivityRules;
        this.activityProductRules  = activityProductRules;
        this.metrics               = metrics;
        this.runHistory            = runHistory;
        this.currentUser           = currentUser;
        this.executionPolicy       = executionPolicy;
    }

    @Override
    @Transactional
    @AuditedOperation(action = "ALLOCATION_RUN_EXECUTED", entityType = "AllocationRun",
                      critical = true)
    public AllocationRunResult runAllocation() {
        try {
            executionPolicy.assertMayExecute(currentUser.currentUsernameOrSystem());
            RunComputation computed = computeAllocation();
            AllocationRunResult result = computed.result();
            String hash = sha256Hex(canonicalInputPayload(
                    computed.resources(), computed.raRows(), computed.apRows()));
            runHistory.recordSuccessfulRun(
                    currentUser.currentUsernameOrSystem(), hash, result);
            metrics.recordAllocationSuccess(computed.durationNanos());
            return result;
        } catch (Exception e) {
            metrics.recordAllocationFailure();
            throw e;
        }
    }

    private RunComputation computeAllocation() {
        long startNanos = System.nanoTime();
        List<ResourceCost> allResources = resourceCosts.findAll();
        Map<String, ResourceCost> resourceById = allResources.stream()
                .collect(Collectors.toMap(ResourceCost::id, r -> r));

        List<ResourceActivityRuleRow> raRows = resourceActivityRules.findAllRows();
        List<ActivityProductRuleRow> apRows = activityProductRules.findAllRows();

        validateActivityCoverage(raRows, apRows);

        List<ResourceStageInput> resourceStage = buildResourceStage(raRows, resourceById);
        List<ActivityStageInput> productStage = buildProductStage(apRows);

        Set<String> resourcesWithRules = raRows.stream()
                .map(ResourceActivityRuleRow::resourceId)
                .collect(Collectors.toSet());

        List<String> unallocated = allResources.stream()
                .filter(r -> !r.amount().isZero())
                .filter(r -> !resourcesWithRules.contains(r.id()))
                .map(ResourceCost::id)
                .sorted()
                .toList();

        AllocationResult result = AllocationEngine.allocate(resourceStage, productStage);

        AllocationRunResult runResult = new AllocationRunResult(
                toAmountMap(result.activityCosts()),
                toAmountMap(result.productCosts()),
                toFlowDtos(result.flows()),
                unallocated);

        return new RunComputation(
                runResult, allResources, raRows, apRows, System.nanoTime() - startNanos);
    }

    private record RunComputation(
            AllocationRunResult result,
            List<ResourceCost> resources,
            List<ResourceActivityRuleRow> raRows,
            List<ActivityProductRuleRow> apRows,
            long durationNanos) {
    }

    private static String canonicalInputPayload(
            List<ResourceCost> resources,
            List<ResourceActivityRuleRow> raRows,
            List<ActivityProductRuleRow> apRows) {

        StringBuilder sb = new StringBuilder();
        resources.stream()
                .sorted(Comparator.comparing(ResourceCost::id))
                .forEach(r -> sb.append(r.id()).append('|')
                        .append(r.amount().amount()).append('|')
                        .append(r.amount().currency().getCurrencyCode()).append(';'));
        sb.append('#');
        raRows.stream()
                .sorted(Comparator
                        .comparing(ResourceActivityRuleRow::resourceId)
                        .thenComparing(ResourceActivityRuleRow::activityId))
                .forEach(row -> sb.append(row.resourceId()).append('|')
                        .append(row.activityId()).append('|')
                        .append(row.driverWeight()).append(';'));
        sb.append('#');
        apRows.stream()
                .sorted(Comparator
                        .comparing(ActivityProductRuleRow::activityId)
                        .thenComparing(ActivityProductRuleRow::productId))
                .forEach(row -> sb.append(row.activityId()).append('|')
                        .append(row.productId()).append('|')
                        .append(row.driverWeight()).append(';'));
        return sb.toString();
    }

    private static String sha256Hex(String payload) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static void validateActivityCoverage(
            List<ResourceActivityRuleRow> raRows,
            List<ActivityProductRuleRow> apRows) {

        Set<String> coveredActivities = apRows.stream()
                .map(ActivityProductRuleRow::activityId)
                .collect(Collectors.toSet());

        List<String> missing = raRows.stream()
                .map(ResourceActivityRuleRow::activityId)
                .distinct()
                .filter(id -> !coveredActivities.contains(id))
                .sorted()
                .toList();

        if (!missing.isEmpty()) {
            throw new AllocationDomainException(
                    "The following activities are targeted by resource rules but have no "
                    + "product allocation rules: " + missing);
        }
    }

    private static List<ResourceStageInput> buildResourceStage(
            List<ResourceActivityRuleRow> raRows,
            Map<String, ResourceCost> resourceById) {

        return raRows.stream()
                .collect(Collectors.groupingBy(ResourceActivityRuleRow::resourceId))
                .entrySet().stream()
                .map(e -> {
                    ResourceCost rc = resourceById.get(e.getKey());
                    if (rc == null) {
                        throw new AllocationDomainException(
                                "Stage-1 rule references unknown resource id: " + e.getKey());
                    }
                    List<DriverShare> shares = e.getValue().stream()
                            .map(r -> new DriverShare(r.activityId(), r.driverWeight()))
                            .toList();
                    return new ResourceStageInput(rc, shares);
                })
                .toList();
    }

    private static List<ActivityStageInput> buildProductStage(
            List<ActivityProductRuleRow> apRows) {

        return apRows.stream()
                .collect(Collectors.groupingBy(ActivityProductRuleRow::activityId))
                .entrySet().stream()
                .map(e -> {
                    List<DriverShare> shares = e.getValue().stream()
                            .map(r -> new DriverShare(r.productId(), r.driverWeight()))
                            .toList();
                    return new ActivityStageInput(e.getKey(), shares);
                })
                .toList();
    }

    private static Map<String, BigDecimal> toAmountMap(Map<String, Money> money) {
        return money.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().amount()));
    }

    private static List<AllocationFlowDto> toFlowDtos(List<CostFlow> flows) {
        return flows.stream().map(AllocationRunService::toFlowDto).toList();
    }

    private static AllocationFlowDto toFlowDto(CostFlow flow) {
        Money amount = flow.amount();
        return new AllocationFlowDto(
                flow.from().kind().name(),
                flow.from().id(),
                flow.to().kind().name(),
                flow.to().id(),
                amount.amount(),
                amount.currency().getCurrencyCode());
    }
}
