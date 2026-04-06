package com.profitflow.application.cache;

/**
 * Spring cache region names shared by application services and {@code CacheConfig}.
 */
public final class CacheNames {

    public static final String RESOURCE_COSTS = "resource-costs";
    public static final String ACTIVITIES      = "activities";
    public static final String PRODUCTS        = "products";

    private CacheNames() {
    }
}
