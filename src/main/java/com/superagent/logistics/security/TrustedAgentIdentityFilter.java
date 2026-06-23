package com.superagent.logistics.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class TrustedAgentIdentityFilter extends OncePerRequestFilter {

    private final EnterpriseSecurityProperties properties;
    private final AgentAuthenticationResolver authenticationResolver;

    public TrustedAgentIdentityFilter(EnterpriseSecurityProperties properties,
                                      AgentAuthenticationResolver authenticationResolver) {
        this.properties = properties;
        this.authenticationResolver = authenticationResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (properties.isOidcEnabled() && authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            EnterpriseIdentityContext.set(authenticationResolver.resolve(authentication));
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            EnterpriseIdentityContext.clear();
        }
    }
}
