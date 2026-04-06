package com.profitflow.adapter.in.web;

import com.profitflow.adapter.in.web.dto.ActivityProductRuleDto;
import com.profitflow.adapter.in.web.dto.ResourceActivityRuleDto;
import com.profitflow.application.model.ActivityProductRuleRow;
import com.profitflow.application.model.ResourceActivityRuleRow;
import com.profitflow.application.port.in.AllocationRuleUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST adapter for managing the two-stage ABC allocation rule tables.
 *
 * <h2>Stage semantics</h2>
 * <ul>
 *   <li><strong>Resource → Activity</strong> (stage 1): each rule maps a GL resource to
 *       an activity pool with a proportional driver weight.  A resource can target multiple
 *       activities; the engine normalises the weights within each resource.</li>
 *   <li><strong>Activity → Product</strong> (stage 2): each rule maps an activity pool to
 *       a product or service with a proportional driver weight. An activity can target
 *       multiple products.</li>
 * </ul>
 *
 * <p>Both endpoints use {@code PUT} (full replacement) rather than {@code PATCH} because
 * the Angular client sends the entire validated table in a single request, making
 * row-by-row diffing unnecessary and reducing the API surface.
 */
@Validated
@RestController
@RequestMapping("/api/v1/rules")
public class AllocationRuleController {

    private final AllocationRuleUseCase rules;

    public AllocationRuleController(AllocationRuleUseCase rules) {
        this.rules = rules;
    }

    /** Returns all current resource → activity allocation rules. */
    @GetMapping("/resource-to-activity")
    public List<ResourceActivityRuleDto> listResourceToActivity() {
        return rules.listResourceToActivityRules().stream()
                .map(r -> new ResourceActivityRuleDto(r.resourceId(), r.activityId(), r.driverWeight()))
                .toList();
    }

    /**
     * Atomically replaces all resource → activity rules with the supplied list.
     *
     * @param body the new complete set of rules (replaces existing table in full); max 5,000 rows
     */
    @PutMapping("/resource-to-activity")
    public void replaceResourceToActivity(
            @Valid @Size(max = 5000, message = "Rule list must not exceed 5,000 rows")
            @RequestBody List<ResourceActivityRuleDto> body) {

        List<ResourceActivityRuleRow> rows = body.stream()
                .map(d -> new ResourceActivityRuleRow(d.resourceId(), d.activityId(), d.driverWeight()))
                .toList();
        rules.replaceResourceToActivityRules(rows);
    }

    /** Returns all current activity → product allocation rules. */
    @GetMapping("/activity-to-product")
    public List<ActivityProductRuleDto> listActivityToProduct() {
        return rules.listActivityToProductRules().stream()
                .map(r -> new ActivityProductRuleDto(r.activityId(), r.productId(), r.driverWeight()))
                .toList();
    }

    /**
     * Atomically replaces all activity → product rules with the supplied list.
     *
     * @param body the new complete set of rules (replaces existing table in full); max 5,000 rows
     */
    @PutMapping("/activity-to-product")
    public void replaceActivityToProduct(
            @Valid @Size(max = 5000, message = "Rule list must not exceed 5,000 rows")
            @RequestBody List<ActivityProductRuleDto> body) {

        List<ActivityProductRuleRow> rows = body.stream()
                .map(d -> new ActivityProductRuleRow(d.activityId(), d.productId(), d.driverWeight()))
                .toList();
        rules.replaceActivityToProductRules(rows);
    }
}
