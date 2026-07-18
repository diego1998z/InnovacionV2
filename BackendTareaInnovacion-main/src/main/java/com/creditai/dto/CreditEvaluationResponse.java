package com.creditai.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

public record CreditEvaluationResponse(
        Long id,
        Long clientId,
        String clientName,
        Integer traditionalScore,
        String scoreInterpretation,
        Map<String, Integer> scoreBreakdown,
        String aiProfile,
        String riskLevel,
        BigDecimal suggestedCreditLine,
        String aiJustification,
        String aiRecommendations,
        Boolean isSimulation,
        LocalDateTime evaluatedAt
) {}
