package com.superagent.logistics.observability;

import com.superagent.logistics.security.EnterpriseSecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestMdcFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    private final EnterpriseSecurityProperties securityProperties;

    public RequestMdcFilter(EnterpriseSecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = requestId(request);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        response.setHeader(TRACE_ID_HEADER, requestId);
        try (MDC.MDCCloseable ignoredRequest = MDC.putCloseable("requestId", requestId);
             MDC.MDCCloseable ignoredTrace = MDC.putCloseable("traceId", requestId);
             MDC.MDCCloseable ignoredTenant = MDC.putCloseable("tenantId", headerOrDefault(request, securityProperties.getTenantHeader(), ""));
             MDC.MDCCloseable ignoredUser = MDC.putCloseable("userId", headerOrDefault(request, securityProperties.getUserHeader(), ""));
             MDC.MDCCloseable ignoredPath = MDC.putCloseable("path", request.getRequestURI())) {
            filterChain.doFilter(request, response);
        }
    }

    private String requestId(HttpServletRequest request) {
        String header = request.getHeader(TRACE_ID_HEADER);
        if (!StringUtils.hasText(header)) {
            header = request.getHeader(REQUEST_ID_HEADER);
        }
        return StringUtils.hasText(header) ? header.trim() : "req-" + UUID.randomUUID().toString().substring(0, 12);
    }

    private String headerOrDefault(HttpServletRequest request, String headerName, String fallback) {
        String value = request.getHeader(headerName);
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
