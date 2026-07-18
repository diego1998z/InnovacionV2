package com.creditai.dto;

import java.math.BigDecimal;

public record SimulationRequest(
        BigDecimal monthlyIncome,
        BigDecimal totalSavings,
        BigDecimal currentDebts
) {}
