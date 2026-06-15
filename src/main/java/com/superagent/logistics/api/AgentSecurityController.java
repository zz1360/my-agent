package com.superagent.logistics.api;

import com.superagent.logistics.api.dto.SecurityContextResponse;
import com.superagent.logistics.security.AgentUserContext;
import com.superagent.logistics.security.EnterpriseSecurityProperties;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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

    public AgentSecurityController(EnterpriseSecurityProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/context")
    public SecurityContextResponse context() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        AgentUserContext context = authentication != null && authentication.getPrincipal() instanceof AgentUserContext agentUser
                ? agentUser
                : AgentUserContext.from(null, null, null);
        List<String> roles = new ArrayList<>(context.roles());
        roles.sort(Comparator.naturalOrder());
        return new SecurityContextResponse(
                context.tenantId(),
                context.userId(),
                roles,
                authentication != null && authentication.isAuthenticated(),
                properties.isApiKeyRequired(),
                authentication == null ? "none" : authentication.getClass().getSimpleName()
        );
    }
}
