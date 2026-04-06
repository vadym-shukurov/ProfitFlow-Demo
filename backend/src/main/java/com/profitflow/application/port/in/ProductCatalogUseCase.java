package com.profitflow.application.port.in;

import com.profitflow.domain.Product;

import java.util.List;

/**
 * Inbound port for managing the product catalogue.
 *
 * <p>Products are the final cost objects in Activity-Based Costing: activity
 * costs are allocated to products to produce the per-product ABC cost breakdown.
 */
public interface ProductCatalogUseCase {

    /** Returns all products ordered by the repository's default sort. */
    List<Product> listProducts();

    /**
     * Creates a new product.
     *
     * @param name human-readable product name, must not be blank
     * @return the persisted product with a generated ID
     */
    Product createProduct(String name);
}
