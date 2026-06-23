package com.superagent.logistics.ops;

import com.superagent.logistics.config.DeepSeekProperties;
import com.superagent.logistics.config.DeploymentProperties;
import com.superagent.logistics.config.PgVectorProperties;
import com.superagent.logistics.config.RetrievalProperties;
import com.superagent.logistics.security.EnterpriseSecurityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class DeploymentConfigValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DeploymentConfigValidator.class);

    private final DeploymentProperties deploymentProperties;
    private final EnterpriseSecurityProperties securityProperties;
    private final DeepSeekProperties deepSeekProperties;
    private final PgVectorProperties vectorProperties;
    private final RetrievalProperties retrievalProperties;

    public DeploymentConfigValidator(DeploymentProperties deploymentProperties,
                                     EnterpriseSecurityProperties securityProperties,
                                     DeepSeekProperties deepSeekProperties,
                                     PgVectorProperties vectorProperties,
                                     RetrievalProperties retrievalProperties) {
        this.deploymentProperties = deploymentProperties;
        this.securityProperties = securityProperties;
        this.deepSeekProperties = deepSeekProperties;
        this.vectorProperties = vectorProperties;
        this.retrievalProperties = retrievalProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> issues = validate();
        if (issues.isEmpty()) {
            log.info("Agent deployment configuration validated: environment={}, retrievalDefaultMode={}, vectorEnabled={}, deepseekEnabled={}",
                    deploymentProperties.getEnvironment(), retrievalProperties.normalizedDefaultMode(),
                    vectorProperties.isEnabled(), deepSeekProperties.isEnabled());
            return;
        }
        String message = "Agent deployment configuration issues: " + String.join("; ", issues);
        if (deploymentProperties.isFailFast()) {
            throw new IllegalStateException(message);
        }
        log.warn(message);
    }

    List<String> validate() {
        List<String> issues = new ArrayList<>();
        String environment = deploymentProperties.getEnvironment() == null
                ? "local"
                : deploymentProperties.getEnvironment().trim().toLowerCase(Locale.ROOT);
        if ("prod".equals(environment)
                && !securityProperties.isApiKeyRequired()
                && !securityProperties.isOidcEnabled()) {
            issues.add("prod environment requires AGENT_API_KEY or AGENT_AUTH_MODE=oidc-bff");
        }
        if (deepSeekProperties.isEnabled() && isBlank(deepSeekProperties.getApiKeyFile())) {
            issues.add("DeepSeek is enabled but DEEPSEEK_API_KEY_FILE is empty");
        }
        String retrievalMode = retrievalProperties.normalizedDefaultMode();
        if ("VECTOR_ONLY".equals(retrievalMode) && !vectorProperties.isEnabled()) {
            issues.add("VECTOR_ONLY retrieval requires agent.vector-store.enabled=true");
        }
        if ("prod".equals(environment) && vectorProperties.isEnabled() && isBlank(vectorProperties.getPassword())) {
            issues.add("prod PGVector connection should use PGVECTOR_PASSWORD");
        }
        if (vectorProperties.getDimension() <= 0) {
            issues.add("PGVector dimension must be positive");
        }
        return issues;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
