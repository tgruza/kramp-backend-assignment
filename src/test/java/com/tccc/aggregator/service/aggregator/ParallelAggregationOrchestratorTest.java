package com.tccc.aggregator.service.aggregator;

import com.tccc.aggregator.domain.*;
import com.tccc.aggregator.service.upstream.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ParallelAggregationOrchestratorTest {

    @Mock
    private CatalogService catalogService;

    @Mock
    private PricingService pricingService;

    @Mock
    private AvailabilityService availabilityService;

    @Mock
    private CustomerService customerService;

    private ParallelAggregationOrchestrator orchestrator;

    private static final String PRODUCT_ID = "PART-001";
    private static final String MARKET = "nl-NL";
    private static final String CUSTOMER_ID = "CUST-GOLD";

    @BeforeEach
    void setUp() {
        orchestrator = new ParallelAggregationOrchestrator(
                catalogService, pricingService, availabilityService, customerService,
                Executors.newVirtualThreadPerTaskExecutor());

        ReflectionTestUtils.setField(orchestrator, "catalogTimeoutMs", 500L);
        ReflectionTestUtils.setField(orchestrator, "pricingTimeoutMs", 500L);
        ReflectionTestUtils.setField(orchestrator, "availabilityTimeoutMs", 500L);
        ReflectionTestUtils.setField(orchestrator, "customerTimeoutMs", 500L);
    }

    @Test
    void shouldAggregateAllServicesSuccessfully() {
        when(catalogService.getProduct(PRODUCT_ID, MARKET)).thenReturn(sampleCatalog());
        when(pricingService.getPricing(PRODUCT_ID, MARKET, CUSTOMER_ID)).thenReturn(samplePricing());
        when(availabilityService.getAvailability(PRODUCT_ID, MARKET)).thenReturn(sampleAvailability());
        when(customerService.getCustomer(CUSTOMER_ID, MARKET)).thenReturn(sampleCustomer());

        var request = new ProductRequest(PRODUCT_ID, MARKET, CUSTOMER_ID);
        var response = orchestrator.orchestrate(request);

        assertThat(response.productId()).isEqualTo(PRODUCT_ID);
        assertThat(response.name()).isEqualTo("Hydraulische Cilinder 50mm");
        assertThat(response.pricing()).isNotNull();
        assertThat(response.pricing().finalPrice()).isEqualByComparingTo(new BigDecimal("208.25"));
        assertThat(response.availability()).isNotNull();
        assertThat(response.availability().inStock()).isTrue();
        assertThat(response.customer()).isNotNull();
        assertThat(response.customer().segment()).isEqualTo("Gold");
        assertThat(response.market()).isEqualTo(MARKET);
        assertThat(response.metadata()).isNotNull();
    }

    @Test
    void shouldFailWhenCatalogUnavailable() {
        when(catalogService.getProduct(anyString(), anyString()))
                .thenThrow(new UpstreamServiceException("CatalogService", "Service unavailable"));
        when(pricingService.getPricing(anyString(), anyString(), anyString())).thenReturn(samplePricing());
        when(availabilityService.getAvailability(anyString(), anyString())).thenReturn(sampleAvailability());

        var request = new ProductRequest(PRODUCT_ID, MARKET, null);

        assertThatThrownBy(() -> orchestrator.orchestrate(request))
                .isInstanceOf(CatalogUnavailableException.class);
    }

    @Test
    void shouldReturnProductWithoutPricingWhenPricingFails() {
        when(catalogService.getProduct(PRODUCT_ID, MARKET)).thenReturn(sampleCatalog());
        when(pricingService.getPricing(anyString(), anyString(), any()))
                .thenThrow(new UpstreamServiceException("PricingService", "Service unavailable"));
        when(availabilityService.getAvailability(PRODUCT_ID, MARKET)).thenReturn(sampleAvailability());

        var request = new ProductRequest(PRODUCT_ID, MARKET, null);
        var response = orchestrator.orchestrate(request);

        assertThat(response.productId()).isEqualTo(PRODUCT_ID);
        assertThat(response.name()).isNotNull();
        assertThat(response.pricing()).isNull();
        assertThat(response.availability()).isNotNull();
        assertThat(response.metadata().warnings()).isNotNull();
        assertThat(response.metadata().warnings()).anyMatch(w -> w.contains("PricingService"));
    }

    @Test
    void shouldReturnProductWithoutAvailabilityWhenAvailabilityFails() {
        when(catalogService.getProduct(PRODUCT_ID, MARKET)).thenReturn(sampleCatalog());
        when(pricingService.getPricing(anyString(), anyString(), any())).thenReturn(samplePricing());
        when(availabilityService.getAvailability(anyString(), anyString()))
                .thenThrow(new UpstreamServiceException("AvailabilityService", "Service unavailable"));

        var request = new ProductRequest(PRODUCT_ID, MARKET, null);
        var response = orchestrator.orchestrate(request);

        assertThat(response.productId()).isEqualTo(PRODUCT_ID);
        assertThat(response.pricing()).isNotNull();
        assertThat(response.availability()).isNull();
        assertThat(response.metadata().warnings()).anyMatch(w -> w.contains("AvailabilityService"));
    }

    @Test
    void shouldSkipCustomerServiceWhenNoCustomerId() {
        when(catalogService.getProduct(PRODUCT_ID, MARKET)).thenReturn(sampleCatalog());
        when(pricingService.getPricing(anyString(), anyString(), any())).thenReturn(samplePricing());
        when(availabilityService.getAvailability(PRODUCT_ID, MARKET)).thenReturn(sampleAvailability());

        var request = new ProductRequest(PRODUCT_ID, MARKET, null);
        var response = orchestrator.orchestrate(request);

        assertThat(response.customer()).isNull();
        verify(customerService, never()).getCustomer(anyString(), anyString());
        assertThat(response.metadata().dataSourceStatuses())
                .anyMatch(s -> s.serviceName().equals("CustomerService")
                        && s.status() == AggregatedProductResponse.DataSourceStatus.Status.SKIPPED);
    }

    @Test
    void shouldReturnProductWhenCustomerServiceFails() {
        when(catalogService.getProduct(PRODUCT_ID, MARKET)).thenReturn(sampleCatalog());
        when(pricingService.getPricing(anyString(), anyString(), anyString())).thenReturn(samplePricing());
        when(availabilityService.getAvailability(PRODUCT_ID, MARKET)).thenReturn(sampleAvailability());
        when(customerService.getCustomer(anyString(), anyString()))
                .thenThrow(new UpstreamServiceException("CustomerService", "Service unavailable"));

        var request = new ProductRequest(PRODUCT_ID, MARKET, CUSTOMER_ID);
        var response = orchestrator.orchestrate(request);

        assertThat(response.productId()).isEqualTo(PRODUCT_ID);
        assertThat(response.pricing()).isNotNull();
        assertThat(response.availability()).isNotNull();
        assertThat(response.customer()).isNull();
        assertThat(response.metadata().warnings()).anyMatch(w -> w.contains("CustomerService"));
    }

    @Test
    void shouldHandleAllOptionalServicesFailingGracefully() {
        when(catalogService.getProduct(PRODUCT_ID, MARKET)).thenReturn(sampleCatalog());
        when(pricingService.getPricing(anyString(), anyString(), any()))
                .thenThrow(new UpstreamServiceException("PricingService", "down"));
        when(availabilityService.getAvailability(anyString(), anyString()))
                .thenThrow(new UpstreamServiceException("AvailabilityService", "down"));

        var request = new ProductRequest(PRODUCT_ID, MARKET, null);
        var response = orchestrator.orchestrate(request);

        assertThat(response.productId()).isEqualTo(PRODUCT_ID);
        assertThat(response.name()).isNotNull();
        assertThat(response.pricing()).isNull();
        assertThat(response.availability()).isNull();
        assertThat(response.customer()).isNull();
        assertThat(response.metadata().warnings()).hasSize(3);
    }

    @Test
    void shouldIncludeResponseMetadata() {
        when(catalogService.getProduct(PRODUCT_ID, MARKET)).thenReturn(sampleCatalog());
        when(pricingService.getPricing(anyString(), anyString(), any())).thenReturn(samplePricing());
        when(availabilityService.getAvailability(PRODUCT_ID, MARKET)).thenReturn(sampleAvailability());

        var request = new ProductRequest(PRODUCT_ID, MARKET, null);
        var response = orchestrator.orchestrate(request);

        assertThat(response.metadata()).isNotNull();
        assertThat(response.metadata().timestamp()).isNotNull();
        assertThat(response.metadata().totalDurationMs()).isGreaterThanOrEqualTo(0);
        assertThat(response.metadata().dataSourceStatuses()).isNotEmpty();
    }

    private CatalogInfo sampleCatalog() {
        return new CatalogInfo(
                PRODUCT_ID,
                "Hydraulische Cilinder 50mm",
                "Dubbelwerkende hydraulische cilinder",
                "Agricultural Parts",
                List.of("Material: Steel", "Weight: 2.5kg"),
                List.of("https://cdn.example.com/images/PART-001/main.jpg")
        );
    }

    private PricingInfo samplePricing() {
        return new PricingInfo(
                new BigDecimal("245.00"),
                new BigDecimal("36.75"),
                new BigDecimal("208.25"),
                "EUR"
        );
    }

    private AvailabilityInfo sampleAvailability() {
        return new AvailabilityInfo(42, "Rotterdam, NL", LocalDate.now().plusDays(1), true);
    }

    private CustomerInfo sampleCustomer() {
        return new CustomerInfo("CUST-GOLD", "Gold", List.of("hydraulics"), "Rotterdam, NL");
    }
}
