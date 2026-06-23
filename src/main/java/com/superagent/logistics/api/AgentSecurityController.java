package com.superagent.logistics.api;

import com.superagent.logistics.api.dto.CsrfTokenResponse;
import com.superagent.logistics.api.dto.SecurityConfigResponse;
import com.superagent.logistics.api.dto.SecurityContextResponse;
import com.superagent.logistics.security.AgentAuthenticationResolver;
import com.superagent.logistics.security.AgentPermissionService;
import com.superagent.logistics.security.AgentUserContext;
import com.superagent.logistics.security.EnterpriseSecurityProperties;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/agent/security")
public class AgentSecurityController {

    private final EnterpriseSecurityProperties properties;
    private final AgentPermissionService permissionService;
    private final AgentAuthenticationResolver authenticationResolver;

    public AgentSecurityController(EnterpriseSecurityProperties properties,
                                   AgentPermissionService permissionService,
                                   AgentAuthenticationResolver authenticationResolver) {
        this.properties = properties;
        this.permissionService = permissionService;
        this.authenticationResolver = authenticationResolver;
    }

    @GetMapping("/config")
    public SecurityConfigResponse config() {
        String loginUrl = properties.isOidcEnabled()
                ? "/oauth2/authorization/" + properties.getOidcRegistrationId()
                : "";
        return new SecurityConfigResponse(properties.getMode(), loginUrl,
                "/api/agent/security/logout", "/api/agent/security/csrf");
    }

    @GetMapping("/csrf")
    public CsrfTokenResponse csrf(CsrfToken token) {
        return token == null
                ? new CsrfTokenResponse("X-XSRF-TOKEN", "_csrf", "")
                : new CsrfTokenResponse(token.getHeaderName(), token.getParameterName(), token.getToken());
    }

    @GetMapping("/context")
    public SecurityContextResponse context() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        AgentUserContext context = authenticationResolver.resolve(authentication);
        boolean authenticated = authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        List<String> roles = new ArrayList<>(context.roles());
        roles.sort(Comparator.naturalOrder());
        return new SecurityContextResponse(
                context.tenantId(),
                context.userId(),
                roles,
                permissionService.permissions(context),
                authenticated,
                properties.isApiKeyRequired(),
                authentication == null ? "none" : authentication.getClass().getSimpleName()
        );
    }
}
