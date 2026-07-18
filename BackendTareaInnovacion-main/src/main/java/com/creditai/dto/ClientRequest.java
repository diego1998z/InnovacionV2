package com.creditai.dto;

import java.math.BigDecimal;

public record ClientRequest(
        String dni,
        String fullName,
        Integer age,
        String address,
        String phone,
        String email,
        BigDecimal monthlyIncome,
        BigDecimal totalSavings,
        BigDecimal currentDebts
) {}
