package com.creditai.dto;

import java.math.BigDecimal;

public record FinancialHistoryRequest(
        String recordType,
        BigDecimal amount,
        String description,
        String recordDate,
        Integer totalInstallments,
        Integer paidInstallments,
        Integer overdueInstallments,
        String paymentStatus,
        BigDecimal overdueAmount
) {}
