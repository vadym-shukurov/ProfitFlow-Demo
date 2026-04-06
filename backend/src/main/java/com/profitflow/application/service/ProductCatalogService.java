package com.profitflow.application.service;

import com.profitflow.application.exception.InvalidInputException;
import com.profitflow.application.port.in.ProductCatalogUseCase;
import com.profitflow.application.port.out.ProductRepositoryPort;
import com.profitflow.domain.Product;
import com.profitflow.application.cache.CacheNames;
import com.profitflow.application.audit.AuditedOperation;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Application service for the Product reference catalogue.
 *
 * Products are reference data that rarely change; reads are cached under
 * {@value CacheNames#PRODUCTS} with a 60-second TTL.
 */
@Service
public class ProductCatalogService implements ProductCatalogUseCase {

    private final ProductRepositoryPort products;

    public ProductCatalogService(ProductRepositoryPort products) {
        this.products = products;
    }

    /**
     * Returns all products, served from cache when possible.
     * The cache is invalidated whenever a new product is created.
     */
    @Override
    @Cacheable(CacheNames.PRODUCTS)
    public List<Product> listProducts() {
        return products.findAll();
    }

    /**
     * Creates and persists a new product. Evicts the products cache.
     *
     * @param name non-blank product name
     * @return the saved {@link Product} with its generated ID
     * @throws InvalidInputException if {@code name} is blank
     */
    @Override
    @AuditedOperation(action = "PRODUCT_CREATED", entityType = "Product",
                      entityIdSpEL = "#result.id")
    @CacheEvict(value = CacheNames.PRODUCTS, allEntries = true)
    public Product createProduct(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidInputException("Product name is required");
        }
        Product product = new Product(UUID.randomUUID().toString(), name.strip());
        return products.save(product);
    }
}
