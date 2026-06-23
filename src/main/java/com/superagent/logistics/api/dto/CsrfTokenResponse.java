package com.superagent.logistics.api.dto;

public record CsrfTokenResponse(String headerName, String parameterName, String token) {
}
