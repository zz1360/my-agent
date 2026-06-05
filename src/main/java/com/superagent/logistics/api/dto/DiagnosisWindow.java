package com.superagent.logistics.api.dto;

import java.time.LocalDate;

public record DiagnosisWindow(
        LocalDate from,
        LocalDate to,
        String label
) {
}
