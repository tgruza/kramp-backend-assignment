package com.tccc.aggregator.service.aggregator;

import com.tccc.aggregator.domain.*;
import com.tccc.aggregator.domain.AggregatedProductResponse.DataSourceStatus;
import com.tccc.aggregator.domain.AggregatedProductResponse.DataSourceStatus.Status;
import com.tccc.aggregator.domain.AggregatedProductResponse.ResponseMetadata;
import com.tccc.aggregator.service.upstream.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Supplier;

@Component
class ParallelAggregationOrchestrator implements AggregationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ParallelAggregationOrchestrator.class);

    private final CatalogService catalogService;
    private final PricingService pricingService;
    private final AvailabilityService availabilityService;
    private final CustomerService customerService;
    @Qualifier("virtualThreadExecutor")
    private final ExecutorService executor;

    ParallelAggregationOrchestrator(
            CatalogService catalogService,
            PricingService pricingService,
            AvailabilityService availabilityService,
            CustomerService customerService,
            @Qualifier("virtualThreadExecutor") ExecutorService executor
    ) {
        this.catalogService = catalogService;
        this.pricingService = pricingService;
        this.availabilityService = availabilityService;
        this.customerService = customerService;
        this.executor = executor;
    }

    @Value("${aggregator.timeout.catalog-ms:500}")
    private long catalogTimeoutMs;

    @Value("${aggregator.timeout.pricing-ms:500}")
    private long pricingTimeoutMs;

    @Value("${aggregator.timeout.availability-ms:500}")
    private long availabilityTimeoutMs;

    @Value("${aggregator.timeout.customer-ms:500}")
    private long customerTimeoutMs;

    @Override
    public AggregatedProductResponse orchestrate(ProductRequest request) {
        long startTime = System.currentTimeMillis();
        List<DataSourceStatus> statuses = Collections.synchronizedList(new ArrayList<>());
        List<String> warnings = Collections.synchronizedList(new ArrayList<>());

        log.info("Aggregating product info: productId={}, market={}, customerId={}",
                request.productId(), request.market(), request.customerId());

        CompletableFuture<CatalogInfo> catalogFuture = callRequired(
                "CatalogService",
                () -> catalogService.getProduct(request.productId(), request.market()),
                statuses
        );

        CompletableFuture<PricingInfo> pricingFuture = callOptional(
                "PricingService",
                () -> pricingService.getPricing(request.productId(), request.market(), request.customerId()),
                pricingTimeoutMs, statuses
        );

        CompletableFuture<AvailabilityInfo> availabilityFuture = callOptional(
                "AvailabilityService",
                () -> availabilityService.getAvailability(request.productId(), request.market()),
                availabilityTimeoutMs, statuses
        );

        CompletableFuture<CustomerInfo> customerFuture;
        if (request.hasCustomerId()) {
            customerFuture = callOptional(
                    "CustomerService",
                    () -> customerService.getCustomer(request.customerId(), request.market()),
                    customerTimeoutMs, statuses
            );
        } else {
            statuses.add(new DataSourceStatus("CustomerService", Status.SKIPPED, 0));
            customerFuture = CompletableFuture.completedFuture(null);
        }

        CompletableFuture.allOf(pricingFuture, availabilityFuture, customerFuture).join();

        CatalogInfo catalog = extractCatalog(catalogFuture, statuses);
        PricingInfo pricing = extractOptional(pricingFuture, "PricingService", warnings);
        AvailabilityInfo availability = extractOptional(availabilityFuture, "AvailabilityService", warnings);
        CustomerInfo customer = extractOptional(customerFuture, "CustomerService", warnings);

        long totalDuration = System.currentTimeMillis() - startTime;
        log.info("Aggregation complete in {}ms (warnings: {})", totalDuration, warnings.size());

        return buildResponse(catalog, pricing, availability, customer, request.market(),
                totalDuration, warnings, statuses);
    }

    private CatalogInfo extractCatalog(
            CompletableFuture<CatalogInfo> future,
            List<DataSourceStatus> statuses
    ) {
        try {
            return future.get(catalogTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            statuses.add(new DataSourceStatus("CatalogService", Status.TIMEOUT, catalogTimeoutMs));
            throw new CatalogUnavailableException(
                    "Cannot display product: catalog service timed out", e);
        } catch (Exception e) {
            throw new CatalogUnavailableException(
                    "Cannot display product: catalog service unavailable", unwrap(e));
        }
    }

    private AggregatedProductResponse buildResponse(
            CatalogInfo catalog, PricingInfo pricing, AvailabilityInfo availability,
            CustomerInfo customer, String market, long totalDuration,
            List<String> warnings, List<DataSourceStatus> statuses
    ) {
        var metadata = new ResponseMetadata(
                Instant.now(),
                totalDuration,
                warnings.isEmpty() ? null : List.copyOf(warnings),
                List.copyOf(statuses)
        );

        return new AggregatedProductResponse(
                catalog.productId(),
                catalog.name(),
                catalog.description(),
                catalog.category(),
                catalog.specifications(),
                catalog.imageUrls(),
                pricing,
                availability,
                customer,
                market,
                metadata
        );
    }

    private <T> CompletableFuture<T> callRequired(
            String serviceName,
            Callable<T> callable,
            List<DataSourceStatus> statuses
    ) {
        return CompletableFuture.supplyAsync(
                withMdc(() -> executeCall(serviceName, callable, statuses)), executor);
    }

    private <T> CompletableFuture<T> callOptional(
            String serviceName,
            Callable<T> callable,
            long timeoutMs,
            List<DataSourceStatus> statuses
    ) {
        return CompletableFuture.supplyAsync(
                withMdc(() -> executeCall(serviceName, callable, statuses)), executor)
                .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .exceptionally(e -> {
                    if (e instanceof TimeoutException || (e.getCause() instanceof TimeoutException)) {
                        statuses.add(new DataSourceStatus(serviceName, Status.TIMEOUT, timeoutMs));
                        log.warn("{} timed out after {}ms", serviceName, timeoutMs);
                    } else {
                        log.warn("{} failed: {}", serviceName, e.getMessage());
                    }
                    return null;
                });
    }

    private <T> T executeCall(
            String serviceName,
            Callable<T> callable,
            List<DataSourceStatus> statuses
    ) {
        long start = System.currentTimeMillis();
        try {
            T result = callable.call();
            long duration = System.currentTimeMillis() - start;
            statuses.add(new DataSourceStatus(serviceName, Status.SUCCESS, duration));
            log.debug("{} completed in {}ms", serviceName, duration);
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            statuses.add(new DataSourceStatus(serviceName, Status.FAILED, duration));
            log.warn("{} failed after {}ms: {}", serviceName, duration, e.getMessage());
            throw new CompletionException(e);
        }
    }

    private <T> Supplier<T> withMdc(Supplier<T> supplier) {
        Map<String, String> context = MDC.getCopyOfContextMap();
        return () -> {
            if (context != null) {
                MDC.setContextMap(context);
            }
            try {
                return supplier.get();
            } finally {
                MDC.clear();
            }
        };
    }

    private <T> T extractOptional(CompletableFuture<T> future, String serviceName, List<String> warnings) {
        try {
            T result = future.get();
            if (result == null) {
                warnings.add(serviceName + " data unavailable");
            }
            return result;
        } catch (Exception e) {
            log.warn("{} failed during extraction: {}", serviceName, e.getMessage());
            warnings.add(serviceName + " data unavailable");
            return null;
        }
    }

    private Throwable unwrap(Throwable t) {
        if (t instanceof ExecutionException && t.getCause() != null) {
            return t.getCause();
        }
        return t;
    }
}
