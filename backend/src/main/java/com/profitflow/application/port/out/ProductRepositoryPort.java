package com.profitflow.application.port.out;

import com.profitflow.domain.Product;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for persisting and querying {@link Product} reference data.
 *
 * <p>Implementations are provided by the {@code adapter.out.persistence} package
 * and wired into the application services via Spring's dependency injection.
 */
public interface ProductRepositoryPort {

    /** Returns all persisted products ordered by the repository's default sort. */
    List<Product> findAll();

    /**
     * Looks up a single product by its string ID.
     *
     * <p>Used by persistence adapters to resolve foreign-key references in
     * allocation rule rows. Not exposed at the HTTP API level.
     *
     * @param id UUID string of the product
     * @return the product, or {@link Optional#empty()} if not found
     */
    Optional<Product> findById(String id);

    /**
     * Persists a new or updated {@link Product}.
     *
     * @param product domain object to save
     * @return the saved product (may be a new instance with a generated ID)
     */
    Product save(Product product);
}
