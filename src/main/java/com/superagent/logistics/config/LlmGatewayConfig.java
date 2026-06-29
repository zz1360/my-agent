package com.superagent.logistics.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LlmGatewayProperties.class)
public class LlmGatewayConfig {
}
