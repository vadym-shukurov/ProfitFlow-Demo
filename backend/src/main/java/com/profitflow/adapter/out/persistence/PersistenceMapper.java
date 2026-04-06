package com.profitflow.adapter.out.persistence;

import com.profitflow.adapter.out.persistence.entity.ActivityEntity;
import com.profitflow.adapter.out.persistence.entity.ProductEntity;
import com.profitflow.adapter.out.persistence.entity.ResourceCostEntity;
import com.profitflow.domain.Activity;
import com.profitflow.domain.Money;
import com.profitflow.domain.Product;
import com.profitflow.domain.ResourceCost;

import java.util.Currency;
import java.util.UUID;

/**
 * Stateless mapper between JPA persistence entities and domain value objects.
 *
 * <h2>Responsibility boundary</h2>
 * <p>The domain layer has no dependency on JPA — it does not know that {@link ResourceCost}
 * is stored in a relational table. This mapper is the only place in the codebase where
 * a JPA entity and a domain type appear in the same method, enforcing the port boundary.
 *
 * <h2>ID representation</h2>
 * <p>JPA entities use {@link java.util.UUID} as the primary key type (preferred by
 * Hibernate for distributed ID generation). Domain records expose the ID as a plain
 * {@code String} to remain framework-agnostic. The mapper converts between these two
 * representations using {@link UUID#fromString} and {@link UUID#toString}.
 */
public final class PersistenceMapper {

    private PersistenceMapper() {
        // utility class — no instances
    }

    // ── ResourceCost ──────────────────────────────────────────────────────────

    /**
     * Converts a {@link ResourceCostEntity} (JPA) to a domain {@link ResourceCost}.
     *
     * @throws IllegalArgumentException if the entity's currency code is not a valid ISO 4217 code
     */
    public static ResourceCost toResourceCost(ResourceCostEntity entity) {
        Currency currency = Currency.getInstance(entity.getCurrencyCode());
        Money money = new Money(entity.getAmount(), currency);
        return new ResourceCost(entity.getId().toString(), entity.getLabel(), money);
    }

    /** Converts a domain {@link ResourceCost} to a {@link ResourceCostEntity} ready for persistence. */
    public static ResourceCostEntity toResourceCostEntity(ResourceCost resourceCost) {
        return new ResourceCostEntity(
                UUID.fromString(resourceCost.id()),
                resourceCost.label(),
                resourceCost.amount().amount(),
                resourceCost.amount().currency().getCurrencyCode());
    }

    // ── Activity ──────────────────────────────────────────────────────────────

    /** Converts an {@link ActivityEntity} to a domain {@link Activity}. */
    public static Activity toActivity(ActivityEntity entity) {
        return new Activity(entity.getId().toString(), entity.getName());
    }

    /** Converts a domain {@link Activity} to an {@link ActivityEntity}. */
    public static ActivityEntity toActivityEntity(Activity activity) {
        return new ActivityEntity(UUID.fromString(activity.id()), activity.name());
    }

    // ── Product ───────────────────────────────────────────────────────────────

    /** Converts a {@link ProductEntity} to a domain {@link Product}. */
    public static Product toProduct(ProductEntity entity) {
        return new Product(entity.getId().toString(), entity.getName());
    }

    /** Converts a domain {@link Product} to a {@link ProductEntity}. */
    public static ProductEntity toProductEntity(Product product) {
        return new ProductEntity(UUID.fromString(product.id()), product.name());
    }
}
