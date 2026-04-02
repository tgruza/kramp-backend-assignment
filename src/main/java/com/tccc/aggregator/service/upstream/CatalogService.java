package com.tccc.aggregator.service.upstream;

import com.tccc.aggregator.domain.CatalogInfo;

public interface CatalogService {

    CatalogInfo getProduct(String productId, String market);
}
