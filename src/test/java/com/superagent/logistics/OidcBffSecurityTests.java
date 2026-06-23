package com.superagent.logistics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "agent.security.mode=oidc-bff",
        "spring.security.oauth2.client.registration.corporate.provider=corporate",
        "spring.security.oauth2.client.registration.corporate.client-id=test-client",
        "spring.security.oauth2.client.registration.corporate.client-secret=test-secret",
        "spring.security.oauth2.client.registration.corporate.authorization-grant-type=authorization_code",
        "spring.security.oauth2.client.registration.corporate.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}",
        "spring.security.oauth2.client.registration.corporate.scope=openid,profile",
        "spring.security.oauth2.client.provider.corporate.authorization-uri=https://idp.example.test/authorize",
        "spring.security.oauth2.client.provider.corporate.token-uri=https://idp.example.test/token",
        "spring.security.oauth2.client.provider.corporate.jwk-set-uri=https://idp.example.test/jwks",
        "spring.security.oauth2.client.provider.corporate.user-info-uri=https://idp.example.test/userinfo",
        "spring.security.oauth2.client.provider.corporate.user-name-attribute=sub"
})
@AutoConfigureMockMvc
class OidcBffSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void unauthenticatedApiIsRejectedButLoginConfigurationRemainsPublic() throws Exception {
        mockMvc.perform(get("/api/ops/readiness"))
                .andExpect(status().isUnauthorized());

        JsonNode config = objectMapper.readTree(mockMvc.perform(get("/api/agent/security/config"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(config.get("mode").asText()).isEqualTo("oidc-bff");
        assertThat(config.get("loginUrl").asText()).isEqualTo("/oauth2/authorization/corporate");
    }

    @Test
    void oidcClaimsDriveContextAndManagementAuthorization() throws Exception {
        var login = oidcLogin().idToken(token -> token
                .claim("preferred_username", "oidc-operator")
                .claim("tenant_id", "T001")
                .claim("roles", List.of("OPS_MANAGER")));

        JsonNode context = objectMapper.readTree(mockMvc.perform(get("/api/agent/security/context").with(login))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(context.get("tenantId").asText()).isEqualTo("T001");
        assertThat(context.get("userId").asText()).isEqualTo("oidc-operator");
        assertThat(context.get("permissions")).anySatisfy(permission ->
                assertThat(permission.asText()).isEqualTo("QUALITY_MANAGE"));

        mockMvc.perform(post("/api/agent/quality/alerts/evaluate")
                        .param("tenantId", "T999")
                        .param("roles", "CUSTOMER_SERVICE")
                        .with(login)
                        .with(csrf()))
                .andExpect(status().isOk());
    }
}
