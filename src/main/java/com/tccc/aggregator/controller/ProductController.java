package com.tccc.aggregator.controller;

import com.tccc.aggregator.domain.AggregatedProductResponse;
import com.tccc.aggregator.domain.ProductRequest;
import com.tccc.aggregator.service.aggregator.ProductAggregatorService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products")
@Validated
public class ProductController {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);

    private final ProductAggregatorService aggregatorService;

    public ProductController(ProductAggregatorService aggregatorService) {
        this.aggregatorService = aggregatorService;
    }

    @GetMapping("/{productId}")
    public ResponseEntity<AggregatedProductResponse> getProduct(
            @PathVariable
            @NotBlank(message = "Product ID is required")
            String productId,

            @RequestParam
            @NotBlank(message = "Market code is required")
            @Pattern(regexp = "^[a-z]{2}-[A-Z]{2}$", message = "Market code must follow format 'xx-XX', e.g. 'nl-NL'")
            String market,

            @RequestParam(required = false)
            String customerId
    ) {

        log.info("GET /api/v1/products/{} market={} customerId={}", productId, market, customerId);

        ProductRequest request = new ProductRequest(productId, market, customerId);
        AggregatedProductResponse response = aggregatorService.aggregate(request);

        return ResponseEntity.ok(response);
    }
}
