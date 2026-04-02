package com.tccc.aggregator.service.aggregator;

import com.tccc.aggregator.domain.AggregatedProductResponse;
import com.tccc.aggregator.domain.ProductRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductAggregatorService {

    private final AggregationOrchestrator orchestrator;

    @CircuitBreaker(name = "productAggregator", fallbackMethod = "aggregateFallback")
    public AggregatedProductResponse aggregate(ProductRequest request) {
        return orchestrator.orchestrate(request);
    }

    @SuppressWarnings("unused")
    private AggregatedProductResponse aggregateFallback(ProductRequest request, Throwable t) {
        log.error("Aggregator circuit breaker open for request: {}", request, t);
        throw new CatalogUnavailableException(
                "Service temporarily unavailable, please retry later", t);
    }
}
