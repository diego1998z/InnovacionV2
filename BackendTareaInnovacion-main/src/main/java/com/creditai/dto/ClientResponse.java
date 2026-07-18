package com.creditai.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ClientResponse(
        Long id,
        String dni,
        String fullName,
        Integer age,
        String address,
        String phone,
        String email,
        BigDecimal monthlyIncome,
        BigDecimal totalSavings,
        BigDecimal currentDebts,
        String status,
        LocalDateTime createdAt
) {}
