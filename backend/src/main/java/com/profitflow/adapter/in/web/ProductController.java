package com.profitflow.adapter.in.web;

import com.profitflow.adapter.in.web.dto.CreateNamedEntityRequest;
import com.profitflow.adapter.in.web.dto.ProductResponse;
import com.profitflow.application.port.in.ProductCatalogUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST adapter for the product / service catalogue.
 *
 * <p>Products are the final cost objects in the ABC model — the "sink" of the cost
 * flow graph. Their loaded costs represent the total spend attributable to delivering
 * that product or service.
 */
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductCatalogUseCase products;

    public ProductController(ProductCatalogUseCase products) {
        this.products = products;
    }

    /** Returns all registered products. */
    @GetMapping
    public List<ProductResponse> list() {
        return products.listProducts().stream()
                .map(WebMapper::toResponse)
                .toList();
    }

    /**
     * Creates a new product.
     *
     * @return {@code 201 Created} with the persisted product
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody CreateNamedEntityRequest request) {
        return WebMapper.toResponse(products.createProduct(request.name()));
    }
}
