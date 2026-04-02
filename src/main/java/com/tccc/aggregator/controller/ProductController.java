package com.tccc.aggregator.controller;

import com.tccc.aggregator.domain.AggregatedProductResponse;
import com.tccc.aggregator.domain.ProductRequest;
import com.tccc.aggregator.service.aggregator.ProductAggregatorService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/products")
@Validated
@RequiredArgsConstructor
public class ProductController {

    private final ProductAggregatorService aggregatorService;

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
