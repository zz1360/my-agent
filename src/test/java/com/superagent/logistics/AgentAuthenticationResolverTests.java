package com.superagent.logistics;

import com.superagent.logistics.security.AgentAuthenticationResolver;
import com.superagent.logistics.security.AgentUserContext;
import com.superagent.logistics.security.EnterpriseSecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentAuthenticationResolverTests {

    @Test
    void mapsOidcClaimsToTenantUserAndNormalizedRoles() {
        EnterpriseSecurityProperties properties = new EnterpriseSecurityProperties();
        AgentAuthenticationResolver resolver = new AgentAuthenticationResolver(properties);
        OidcIdToken token = new OidcIdToken("token", Instant.now(), Instant.now().plusSeconds(300), Map.of(
                "sub", "user-42",
                "preferred_username", "zhangsan",
                "tenant_id", "T009",
                "roles", List.of("ops_manager", "ROLE_OPERATIONS")
        ));
        DefaultOidcUser oidcUser = new DefaultOidcUser(
                List.of(new SimpleGrantedAuthority("OIDC_USER")), token, "preferred_username");
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                oidcUser, null, oidcUser.getAuthorities());

        AgentUserContext context = resolver.resolve(authentication);

        assertThat(context.tenantId()).isEqualTo("T009");
        assertThat(context.userId()).isEqualTo("zhangsan");
        assertThat(context.roles()).containsExactlyInAnyOrder("OPS_MANAGER", "OPERATIONS");
    }
}
