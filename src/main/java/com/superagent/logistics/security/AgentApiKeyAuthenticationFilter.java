package com.superagent.logistics.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

public class AgentApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final EnterpriseSecurityProperties properties;

    public AgentApiKeyAuthenticationFilter(EnterpriseSecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (requiresApiKey(request) && !hasValidApiKey(request)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("""
                    {"code":"AUTH_API_KEY_INVALID","message":"缺少或无效的企业 API Key","status":401,"path":"%s","traceId":"%s","timestamp":"%s"}
                    """.formatted(request.getRequestURI(), firstNonBlank(MDC.get("traceId"), MDC.get("requestId")), Instant.now()));
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(buildAuthentication(request));
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private boolean requiresApiKey(HttpServletRequest request) {
        if (!properties.isApiKeyRequired()) {
            return false;
        }
        String path = request.getRequestURI();
        if (matchesAny(path, properties.getPublicPathPrefixes())) {
            return false;
        }
        return matchesAny(path, properties.getProtectedPathPrefixes());
    }

    private boolean matchesAny(String path, List<String> prefixes) {
        if (prefixes == null || prefixes.isEmpty()) {
            return false;
        }
        return prefixes.stream()
                .filter(StringUtils::hasText)
                .anyMatch(prefix -> path.startsWith(prefix.trim()));
    }

    private boolean hasValidApiKey(HttpServletRequest request) {
        String provided = request.getHeader(properties.getApiKeyHeader());
        if (!StringUtils.hasText(provided)) {
            return false;
        }
        byte[] expected = properties.getApiKey().trim().getBytes(StandardCharsets.UTF_8);
        byte[] actual = provided.trim().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null ? "" : second;
    }

    private UsernamePasswordAuthenticationToken buildAuthentication(HttpServletRequest request) {
        String tenantId = defaultText(request.getHeader(properties.getTenantHeader()), "T001");
        String userId = defaultText(request.getHeader(properties.getUserHeader()), "api-client");
        List<String> roles = splitRoles(request.getHeader(properties.getRolesHeader()));
        AgentUserContext principal = new AgentUserContext(tenantId, userId, java.util.Set.copyOf(roles));
        List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private List<String> splitRoles(String rolesHeader) {
        if (!StringUtils.hasText(rolesHeader)) {
            return List.of("CUSTOMER_SERVICE");
        }
        List<String> roles = Arrays.stream(rolesHeader.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
        return roles.isEmpty() ? List.of("CUSTOMER_SERVICE") : roles;
    }
}
