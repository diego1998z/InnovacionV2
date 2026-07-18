package com.creditai.dto;

import java.util.Map;

public record DashboardStats(
        long totalClients,
        long activeClients,
        long evaluatedClients,
        double averageScore,
        long advancedCount,
        long intermediateCount,
        long basicCount,
        long clientsWithOverdue,
        double totalSuggestedCredit,
        Map<String, Long> riskDistribution,
        Map<String, Double> averageScoreByProfile,
        Map<String, Long> recommendedProducts,
        Map<String, Long> clientEvolution,
        Map<String, Long> eligibleByProduct,
        Map<String, Object> mlMetrics
) {}
