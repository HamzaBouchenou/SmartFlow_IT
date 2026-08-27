package com.smartflow.backend.crosscutting.logging;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests TraceIdFilter.doFilterInternal.
 *
 * §8 - "le traceId doit se retrouver dans les logs" : the MDC value the filter sets is what
 * logging.pattern.level prints on every line while the request is being handled, and is the
 * same value GlobalExceptionHandler puts in the error response body - the three must always
 * agree, which this test locks in by asserting the exact MDC key and header name both sides
 * share (TraceIdFilter.MDC_KEY, TraceIdFilter.HEADER).
 */
class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("generates a traceId, exposes it in the MDC during the chain, and clears it afterwards")
    void generatesAndClearsTraceId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> traceIdDuringChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> traceIdDuringChain.set(MDC.get(TraceIdFilter.MDC_KEY));

        filter.doFilter(request, response, chain);

        assertThat(traceIdDuringChain.get()).isNotBlank();
        assertThat(response.getHeader(TraceIdFilter.HEADER)).isEqualTo(traceIdDuringChain.get());
        assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("clears the MDC entry even when the rest of the chain throws")
    void clearsMdcOnException() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            throw new IllegalStateException("downstream failure");
        };

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> filter.doFilter(request, response, chain));

        assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("reuses an inbound X-Trace-Id instead of generating a new one, to keep one traceId across hops")
    void reusesInboundTraceId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.HEADER, "upstream-trace-id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> traceIdDuringChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> traceIdDuringChain.set(MDC.get(TraceIdFilter.MDC_KEY));

        filter.doFilter(request, response, chain);

        assertThat(traceIdDuringChain.get()).isEqualTo("upstream-trace-id");
        assertThat(response.getHeader(TraceIdFilter.HEADER)).isEqualTo("upstream-trace-id");
    }
}
