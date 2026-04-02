package com.tccc.aggregator.exception;

import com.tccc.aggregator.service.aggregator.CatalogUnavailableException;
import com.tccc.aggregator.service.upstream.UpstreamServiceException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MissingServletRequestParameterException;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @BeforeEach
    void setUp() {
        MDC.put("correlationId", "test-correlation-id");
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void shouldReturn502ForCatalogUnavailable() {
        var ex = new CatalogUnavailableException("Catalog down", new RuntimeException("timeout"));

        var response = handler.handleCatalogUnavailable(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo(502);
        assertThat(response.getBody().get("message")).isEqualTo("Catalog down");
        assertThat(response.getBody().get("correlationId")).isEqualTo("test-correlation-id");
    }

    @Test
    void shouldReturn502ForUpstreamServiceFailure() {
        var ex = new UpstreamServiceException("PricingService", "Connection refused");

        var response = handler.handleUpstreamFailure(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("message")).isEqualTo("Upstream service failure: PricingService");
    }

    @Test
    void shouldReturn400ForConstraintViolation() {
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        var path = mock(Path.class);
        when(path.toString()).thenReturn("market");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("must not be blank");

        var ex = new ConstraintViolationException(Set.of(violation));

        var response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat((String) response.getBody().get("message")).contains("market");
    }

    @Test
    void shouldReturn400ForMissingParam() {
        var ex = new MissingServletRequestParameterException("market", "String");

        var response = handler.handleMissingParam(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat((String) response.getBody().get("message")).contains("market");
    }

    @Test
    void shouldReturn500ForUnexpectedException() {
        var ex = new RuntimeException("Something broke");

        var response = handler.handleGeneral(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("message")).isEqualTo("Internal server error");
        assertThat(response.getBody().get("correlationId")).isEqualTo("test-correlation-id");
    }

    @Test
    void shouldReturnUnknownCorrelationIdWhenMdcEmpty() {
        MDC.clear();

        var ex = new RuntimeException("error");
        var response = handler.handleGeneral(ex);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("correlationId")).isEqualTo("unknown");
    }

    @Test
    void shouldIncludeTimestampAndErrorInAllResponses() {
        var ex = new RuntimeException("test");

        var response = handler.handleGeneral(ex);

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body).containsKeys("timestamp", "status", "error", "message", "correlationId");
    }
}
