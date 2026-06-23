package com.superagent.logistics.api.dto;

public record SecurityConfigResponse(
        String mode,
        String loginUrl,
        String logoutUrl,
        String csrfUrl
) {
}
