package com.superagent.logistics.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "agent.security")
public class EnterpriseSecurityProperties {

    private boolean enabled = true;
    private String apiKey = "";
    private String apiKeyHeader = "X-Agent-Api-Key";
    private String tenantHeader = "X-Agent-Tenant";
    private String userHeader = "X-Agent-User";
    private String rolesHeader = "X-Agent-Roles";
    private String mode = "api-key";
    private String oidcRegistrationId = "corporate";
    private String oidcTenantClaim = "tenant_id";
    private String oidcUserClaim = "preferred_username";
    private String oidcRolesClaim = "roles";
    private String oidcDefaultTenant = "T001";
    private String postLoginRedirect = "/";
    private List<String> protectedPathPrefixes = List.of("/api/");
    private List<String> publicPathPrefixes = List.of("/api/demo/");

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiKeyHeader() {
        return apiKeyHeader;
    }

    public void setApiKeyHeader(String apiKeyHeader) {
        this.apiKeyHeader = apiKeyHeader;
    }

    public String getTenantHeader() {
        return tenantHeader;
    }

    public void setTenantHeader(String tenantHeader) {
        this.tenantHeader = tenantHeader;
    }

    public String getUserHeader() {
        return userHeader;
    }

    public void setUserHeader(String userHeader) {
        this.userHeader = userHeader;
    }

    public String getRolesHeader() {
        return rolesHeader;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getOidcRegistrationId() {
        return oidcRegistrationId;
    }

    public void setOidcRegistrationId(String oidcRegistrationId) {
        this.oidcRegistrationId = oidcRegistrationId;
    }

    public String getOidcTenantClaim() {
        return oidcTenantClaim;
    }

    public void setOidcTenantClaim(String oidcTenantClaim) {
        this.oidcTenantClaim = oidcTenantClaim;
    }

    public String getOidcUserClaim() {
        return oidcUserClaim;
    }

    public void setOidcUserClaim(String oidcUserClaim) {
        this.oidcUserClaim = oidcUserClaim;
    }

    public String getOidcRolesClaim() {
        return oidcRolesClaim;
    }

    public void setOidcRolesClaim(String oidcRolesClaim) {
        this.oidcRolesClaim = oidcRolesClaim;
    }

    public String getOidcDefaultTenant() {
        return oidcDefaultTenant;
    }

    public void setOidcDefaultTenant(String oidcDefaultTenant) {
        this.oidcDefaultTenant = oidcDefaultTenant;
    }

    public String getPostLoginRedirect() {
        return postLoginRedirect;
    }

    public void setPostLoginRedirect(String postLoginRedirect) {
        this.postLoginRedirect = postLoginRedirect;
    }

    public void setRolesHeader(String rolesHeader) {
        this.rolesHeader = rolesHeader;
    }

    public List<String> getProtectedPathPrefixes() {
        return protectedPathPrefixes;
    }

    public void setProtectedPathPrefixes(List<String> protectedPathPrefixes) {
        this.protectedPathPrefixes = protectedPathPrefixes;
    }

    public List<String> getPublicPathPrefixes() {
        return publicPathPrefixes;
    }

    public void setPublicPathPrefixes(List<String> publicPathPrefixes) {
        this.publicPathPrefixes = publicPathPrefixes;
    }

    public boolean isApiKeyRequired() {
        return enabled && !isOidcEnabled() && apiKey != null && !apiKey.isBlank();
    }

    public boolean isOidcEnabled() {
        return enabled && "oidc-bff".equalsIgnoreCase(mode);
    }
}
