package com.superagent.logistics.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

@Component
public class AgentAuthenticationResolver {

    private final EnterpriseSecurityProperties properties;

    public AgentAuthenticationResolver(EnterpriseSecurityProperties properties) {
        this.properties = properties;
    }

    public AgentUserContext resolve(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AgentUserContext context) {
            return context;
        }
        if (authentication != null && authentication.getPrincipal() instanceof OAuth2User user) {
            String tenantId = claimText(user, properties.getOidcTenantClaim(), properties.getOidcDefaultTenant());
            String userId = claimText(user, properties.getOidcUserClaim(), authentication.getName());
            List<String> roles = claimRoles(user.getAttributes().get(properties.getOidcRolesClaim()));
            if (roles.isEmpty()) {
                roles = authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .filter(authority -> authority.startsWith("ROLE_"))
                        .map(authority -> authority.substring(5))
                        .toList();
            }
            return AgentUserContext.from(tenantId, userId, roles);
        }
        return AgentUserContext.from(null, null, null);
    }

    public List<String> claimRoles(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf).map(this::normalizeRole)
                    .filter(StringUtils::hasText).distinct().toList();
        }
        if (value instanceof String text) {
            return Arrays.stream(text.split("[, ]")).map(this::normalizeRole)
                    .filter(StringUtils::hasText).distinct().toList();
        }
        return List.of();
    }

    private String claimText(OAuth2User user, String claimName, String fallback) {
        Object value = user.getAttributes().get(claimName);
        return value != null && StringUtils.hasText(String.valueOf(value)) ? String.valueOf(value).trim() : fallback;
    }

    private String normalizeRole(String role) {
        String normalized = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        return normalized.startsWith("ROLE_") ? normalized.substring(5) : normalized;
    }
}
