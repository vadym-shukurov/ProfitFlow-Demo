package com.profitflow.adapter.in.web;

import com.profitflow.adapter.in.web.dto.ActivityResponse;
import com.profitflow.adapter.in.web.dto.ProductResponse;
import com.profitflow.adapter.in.web.dto.ResourceCostResponse;
import com.profitflow.domain.Activity;
import com.profitflow.domain.Product;
import com.profitflow.domain.ResourceCost;

/**
 * Stateless mapper from domain objects to REST response DTOs.
 *
 * <p>Keeping the mapping logic here (rather than in the controller or the DTO itself)
 * means controllers stay thin and the mapping code is easy to test in isolation.
 *
 * <p>This class is scoped to the {@code adapter.in.web} package — it is an implementation
 * detail of the web adapter and should not be referenced from the application or domain
 * layers.
 */
public final class WebMapper {

    private WebMapper() {
        // utility class — no instances
    }

    /**
     * Maps a domain {@link ResourceCost} to its REST representation.
     *
     * <p>The amount is exposed as a raw {@link java.math.BigDecimal} (serialised to a
     * JSON number) and the currency as an ISO 4217 code string, matching the OpenAPI
     * schema for {@code ResourceCostResponse}.
     */
    public static ResourceCostResponse toResponse(ResourceCost rc) {
        return new ResourceCostResponse(
                rc.id(),
                rc.label(),
                rc.amount().amount(),
                rc.amount().currency().getCurrencyCode());
    }

    /** Maps a domain {@link Activity} to its REST representation. */
    public static ActivityResponse toResponse(Activity activity) {
        return new ActivityResponse(activity.id(), activity.name());
    }

    /** Maps a domain {@link Product} to its REST representation. */
    public static ProductResponse toResponse(Product product) {
        return new ProductResponse(product.id(), product.name());
    }
}
