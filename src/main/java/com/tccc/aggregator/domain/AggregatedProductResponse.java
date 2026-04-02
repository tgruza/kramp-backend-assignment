package com.tccc.aggregator.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AggregatedProductResponse(
        // Catalog (required)
        String productId,
        String name,
        String description,
        String category,
        List<String> specifications,
        List<String> imageUrls,

        // Pricing (optional)
        PricingInfo pricing,

        // Availability (optional)
        AvailabilityInfo availability,

        // Customer (optional)
        CustomerInfo customer,

        // Metadata
        String market,
        ResponseMetadata metadata
) {

    public record ResponseMetadata(
            Instant timestamp,
            long totalDurationMs,
            List<String> warnings,
            List<DataSourceStatus> dataSourceStatuses
    ) {
    }

    public record DataSourceStatus(
            String serviceName,
            Status status,
            long durationMs
    ) {
        public enum Status {
            SUCCESS, FAILED, SKIPPED, TIMEOUT
        }
    }
}
