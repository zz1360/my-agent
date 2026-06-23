package com.superagent.logistics.security;

import java.util.List;
import java.util.Set;

public record AgentUserContext(
        String tenantId,
        String userId,
        Set<String> roles
) {
    public static AgentUserContext from(String tenantId, String userId, List<String> roles) {
        AgentUserContext trusted = EnterpriseIdentityContext.current();
        if (trusted != null) {
            return trusted;
        }
        String resolvedTenant = tenantId == null || tenantId.isBlank() ? "T001" : tenantId;
        String resolvedUser = userId == null || userId.isBlank() ? "u-demo-cs" : userId;
        Set<String> resolvedRoles = roles == null || roles.isEmpty()
                ? Set.of("CUSTOMER_SERVICE")
                : Set.copyOf(roles);
        return new AgentUserContext(resolvedTenant, resolvedUser, resolvedRoles);
    }

    public boolean hasAnyRole(String... requiredRoles) {
        for (String role : requiredRoles) {
            if (roles.contains(role)) {
                return true;
            }
        }
        return false;
    }
}
