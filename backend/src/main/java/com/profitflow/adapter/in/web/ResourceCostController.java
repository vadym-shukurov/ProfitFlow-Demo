package com.profitflow.adapter.in.web;

import com.profitflow.application.exception.InvalidInputException;
import com.profitflow.adapter.in.web.dto.CreateResourceCostRequest;
import com.profitflow.adapter.in.web.dto.ResourceCostResponse;
import com.profitflow.application.port.in.ResourceCostUseCase;
import com.profitflow.domain.ResourceCost;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST adapter for the {@link ResourceCostUseCase}.
 *
 * <p>Routes:
 * <ul>
 *   <li>{@code GET /api/v1/resource-costs} — list all GL cost lines</li>
 *   <li>{@code POST /api/v1/resource-costs} — create a single line (JSON body)</li>
 *   <li>{@code POST /api/v1/resource-costs/import} — bulk-import from CSV text</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/resource-costs")
public class ResourceCostController {

    private final ResourceCostUseCase resourceCosts;

    public ResourceCostController(ResourceCostUseCase resourceCosts) {
        this.resourceCosts = resourceCosts;
    }

    /** Returns every persisted resource cost. */
    @GetMapping
    public List<ResourceCostResponse> list() {
        return resourceCosts.listCosts().stream()
                .map(WebMapper::toResponse)
                .toList();
    }

    /**
     * Creates a new resource cost.
     *
     * @param request validated JSON body
     * @return {@code 201 Created} with the persisted resource cost
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResourceCostResponse create(@Valid @RequestBody CreateResourceCostRequest request) {
        String currency = (request.currencyCode() == null || request.currencyCode().isBlank())
                ? "USD"
                : request.currencyCode();
        ResourceCost created = resourceCosts.createCost(request.label(), request.amount(), currency);
        return WebMapper.toResponse(created);
    }

    /** Maximum allowed CSV body size (bytes) — prevents memory-exhaustion from huge uploads. */
    private static final int MAX_CSV_BYTES = 1_048_576; // 1 MiB

    /**
     * Bulk-imports resource costs from a plain-text CSV body.
     *
     * <p>Expected format: {@code label,amount[,currencyCode]} (one row per line,
     * optional header row auto-detected).
     *
     * @param csvBody raw CSV text (Content-Type: text/plain), max 1 MiB
     * @return {@code 201 Created} with the list of imported resource costs
     * @throws IllegalArgumentException if the body exceeds the 1 MiB size limit
     */
    @PostMapping(path = "/import", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<List<ResourceCostResponse>> importCsv(@RequestBody String csvBody) {
        if (csvBody != null && csvBody.length() > MAX_CSV_BYTES) {
            throw new InvalidInputException(
                    "CSV body exceeds the maximum allowed size of 1 MiB ("
                    + MAX_CSV_BYTES + " bytes).");
        }
        List<ResourceCostResponse> created = resourceCosts.importCostsFromCsv(csvBody)
                .stream()
                .map(WebMapper::toResponse)
                .toList();
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
