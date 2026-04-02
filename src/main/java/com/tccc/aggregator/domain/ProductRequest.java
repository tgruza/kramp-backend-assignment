package com.tccc.aggregator.domain;

public record ProductRequest(
        String productId,
        String market,
        String customerId
) {
    public boolean hasCustomerId() {
        return customerId != null && !customerId.isBlank();
    }
}
