package com.creditai.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record FinancialHistoryResponse(
        Long id,
        Long clientId,
        String recordType,
        BigDecimal amount,
        String description,
        LocalDate recordDate,
        Integer totalInstallments,
        Integer paidInstallments,
        Integer overdueInstallments,
        String paymentStatus,
        BigDecimal overdueAmount,
        LocalDateTime createdAt
) {}
