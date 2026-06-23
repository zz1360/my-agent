package com.superagent.logistics.security;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.LinkedHashSet;
import java.util.Set;

public class AgentOidcUserService extends OidcUserService {

    private final EnterpriseSecurityProperties properties;
    private final AgentAuthenticationResolver resolver;

    public AgentOidcUserService(EnterpriseSecurityProperties properties, AgentAuthenticationResolver resolver) {
        this.properties = properties;
        this.resolver = resolver;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser user = super.loadUser(userRequest);
        Set<SimpleGrantedAuthority> authorities = new LinkedHashSet<>();
        user.getAuthorities().forEach(authority -> authorities.add(new SimpleGrantedAuthority(authority.getAuthority())));
        resolver.claimRoles(user.getClaims().get(properties.getOidcRolesClaim())).stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .forEach(authorities::add);
        String userNameClaim = user.getClaims().containsKey(properties.getOidcUserClaim())
                ? properties.getOidcUserClaim()
                : "sub";
        return new DefaultOidcUser(authorities, user.getIdToken(), user.getUserInfo(), userNameClaim);
    }
}
