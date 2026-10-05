package com.trihydro.rsuinfobridge.models.dtos;

import io.swagger.v3.oas.annotations.Parameter;

public record RsuFilter(
        @Parameter(description = "Filter RSUs by the primary route (case-insensitive exact match)", example = "I 80")
        String primaryRoute,

        @Parameter(description = "Filter RSUs by TIM deposit enabled status", example = "false")
        Boolean timDepositEnabledOnly
) {
    public RsuFilter {
        primaryRoute = (primaryRoute == null || primaryRoute.isBlank()) ? null : primaryRoute.trim();
        timDepositEnabledOnly = timDepositEnabledOnly != null && timDepositEnabledOnly;
    }
}
