package com.tccc.aggregator.domain;

import java.util.List;

public record CatalogInfo(
        String productId,
        String name,
        String description,
        String category,
        List<String> specifications,
        List<String> imageUrls
) {
}
