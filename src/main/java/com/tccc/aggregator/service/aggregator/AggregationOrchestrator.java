package com.tccc.aggregator.service.aggregator;

import com.tccc.aggregator.domain.AggregatedProductResponse;
import com.tccc.aggregator.domain.ProductRequest;

interface AggregationOrchestrator {

    AggregatedProductResponse orchestrate(ProductRequest request);
}
