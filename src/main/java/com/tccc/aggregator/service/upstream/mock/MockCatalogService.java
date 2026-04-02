package com.tccc.aggregator.service.upstream.mock;

import com.tccc.aggregator.domain.CatalogInfo;
import com.tccc.aggregator.service.upstream.CatalogService;
import com.tccc.aggregator.service.upstream.UpstreamServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
class MockCatalogService implements CatalogService {

    private static final Logger log = LoggerFactory.getLogger(MockCatalogService.class);

    private static final long TYPICAL_LATENCY_MS = 50;
    private static final double RELIABILITY_PCT = 99.9;

    private static final Map<String, Map<String, String>> PRODUCT_NAMES = Map.of(
            "nl-NL", Map.of(
                    "PART-001", "Hydraulische Cilinder 50mm",
                    "PART-002", "Luchtfilter voor Tractor",
                    "PART-003", "Aandrijfriem V-profiel"
            ),
            "de-DE", Map.of(
                    "PART-001", "Hydraulikzylinder 50mm",
                    "PART-002", "Luftfilter für Traktor",
                    "PART-003", "Keilriemen V-Profil"
            ),
            "pl-PL", Map.of(
                    "PART-001", "Siłownik hydrauliczny 50mm",
                    "PART-002", "Filtr powietrza do ciągnika",
                    "PART-003", "Pasek klinowy V-profil"
            )
        );

    private static final Map<String, Map<String, String>> PRODUCT_DESCRIPTIONS = Map.of(
            "nl-NL", Map.of(
                    "PART-001", "Dubbelwerkende hydraulische cilinder, 50mm boring, 300mm slag. Geschikt voor landbouwmachines.",
                    "PART-002", "Hoogwaardige luchtfilter. Past op de meeste Europese tractormodellen.",
                    "PART-003", "Industriële V-snaar, lengte 1200mm. Hittebestendig en duurzaam."
            ),
            "de-DE", Map.of(
                    "PART-001", "Doppeltwirkender Hydraulikzylinder, 50mm Bohrung, 300mm Hub. Geeignet für Landmaschinen.",
                    "PART-002", "Hochwertiger Luftfilter. Passend für die meisten europäischen Traktormodelle.",
                    "PART-003", "Industrieller Keilriemen, Länge 1200mm. Hitzebeständig und langlebig."
            ),
            "pl-PL", Map.of(
                    "PART-001", "Siłownik hydrauliczny dwustronnego działania, średnica 50mm, skok 300mm. Nadaje się do maszyn rolniczych.",
                    "PART-002", "Wysokiej jakości filtr powietrza. Pasuje do większości europejskich modeli ciągników.",
                    "PART-003", "Przemysłowy pasek klinowy, długość 1200mm. Odporny na ciepło i trwały."
            )
    );

    private static final String DEFAULT_MARKET = "nl-NL";

    @Override
    public CatalogInfo getProduct(String productId, String market) {
        log.info("Fetching catalog info for product={} market={}", productId, market);
        MockLatencySimulator.simulate("CatalogService", TYPICAL_LATENCY_MS, RELIABILITY_PCT);

        String effectiveMarket = PRODUCT_NAMES.containsKey(market) ? market : DEFAULT_MARKET;

        Map<String, String> names = PRODUCT_NAMES.get(effectiveMarket);
        Map<String, String> descriptions = PRODUCT_DESCRIPTIONS.get(effectiveMarket);

        if (!names.containsKey(productId)) {
            throw new UpstreamServiceException("CatalogService",
                    "Product not found: " + productId);
        }

        return new CatalogInfo(
                productId,
                names.get(productId),
                descriptions.get(productId),
                "Agricultural Parts",
                List.of("Material: Steel", "Weight: 2.5kg", "Warranty: 24 months"),
                List.of(
                        "https://cdn.example.com/images/" + productId + "/main.jpg",
                        "https://cdn.example.com/images/" + productId + "/detail.jpg"
                )
        );
    }
}
