package com.tccc.aggregator.service.aggregator;

import com.tccc.aggregator.domain.AggregatedProductResponse;
import com.tccc.aggregator.domain.ProductRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ProductAggregatorService {

    private static final Logger log = LoggerFactory.getLogger(ProductAggregatorService.class);

    private final AggregationOrchestrator orchestrator;

    public ProductAggregatorService(AggregationOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

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
