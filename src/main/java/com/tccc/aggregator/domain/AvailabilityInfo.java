package com.tccc.aggregator.domain;

import java.time.LocalDate;

public record AvailabilityInfo(
        int stockLevel,
        String warehouseLocation,
        LocalDate expectedDelivery,
        boolean inStock
) {
}
