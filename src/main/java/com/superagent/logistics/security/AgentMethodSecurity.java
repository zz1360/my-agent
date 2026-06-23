package com.superagent.logistics.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component("agentMethodSecurity")
public class AgentMethodSecurity {

    private final EnterpriseSecurityProperties properties;
    private final AgentAuthenticationResolver resolver;
    private final AgentPermissionService permissionService;

    public AgentMethodSecurity(EnterpriseSecurityProperties properties,
                               AgentAuthenticationResolver resolver,
                               AgentPermissionService permissionService) {
        this.properties = properties;
        this.resolver = resolver;
        this.permissionService = permissionService;
    }

    public boolean hasPermission(String permission) {
        if (!properties.isOidcEnabled() && !properties.isApiKeyRequired()) {
            return true;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && permissionService.permissions(resolver.resolve(authentication)).contains(permission);
    }
}
