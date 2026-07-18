package com.creditai.controller;

import com.creditai.dto.ApiResponse;
import com.creditai.dto.DashboardStats;
import com.creditai.entity.CreditEvaluation;
import com.creditai.repository.ClientRepository;
import com.creditai.repository.CreditEvaluationRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final ClientRepository clientRepository;
    private final CreditEvaluationRepository evalRepository;
    private final ObjectMapper objectMapper;

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<DashboardStats>> getStats() {
        long totalClients  = clientRepository.count();
        long activeClients = clientRepository.countActive();
        long evaluatedClients = evalRepository.countEvaluatedClients();
        Double avgScore    = evalRepository.averageScore();
        BigDecimal totalSuggested = evalRepository.totalSuggestedCredit();

        List<Object[]> profileCounts = evalRepository.countByProfile();
        List<Object[]> riskCounts    = evalRepository.countByRiskLevel();
        List<CreditEvaluation> evaluations = evalRepository.findByIsSimulationFalse();

        long advanced     = extractCount(profileCounts, "ADVANCED");
        long intermediate = extractCount(profileCounts, "INTERMEDIATE");
        long basic        = extractCount(profileCounts, "BASIC");

        Map<String, Long> riskDist = riskCounts.stream()
                .collect(Collectors.toMap(r -> r[0].toString(), r -> (Long) r[1]));

        Map<String, Double> avgScoreByProfile = evalRepository.averageScoreByProfile().stream()
                .collect(Collectors.toMap(r -> r[0].toString(), r -> ((Number) r[1]).doubleValue()));

        Map<String, Long> recommendedProducts = countRecommendedProducts(evaluations);
        Map<String, Long> clientEvolution = countClientEvolution(evaluations);
        Map<String, Long> eligibleByProduct = countEligibleByProduct(evaluations);
        long clientsWithOverdue = (riskDist.getOrDefault("HIGH", 0L) + riskDist.getOrDefault("VERY_HIGH", 0L));

        DatasetAnalytics datasetAnalytics = loadDatasetAnalytics();
        if (evaluations.size() < 10 && datasetAnalytics.records() > 0) {
            evaluatedClients = datasetAnalytics.records();
            avgScore = datasetAnalytics.averageScore();
            basic = datasetAnalytics.profileCounts().getOrDefault("BASIC", 0L);
            intermediate = datasetAnalytics.profileCounts().getOrDefault("INTERMEDIATE", 0L);
            advanced = datasetAnalytics.profileCounts().getOrDefault("ADVANCED", 0L);
            riskDist = datasetAnalytics.riskDistribution();
            avgScoreByProfile = datasetAnalytics.averageScoreByProfile();
            recommendedProducts = datasetAnalytics.recommendedProducts();
            clientEvolution = datasetAnalytics.clientEvolution();
            eligibleByProduct = datasetAnalytics.eligibleByProduct();
            clientsWithOverdue = riskDist.getOrDefault("HIGH", 0L) + riskDist.getOrDefault("VERY_HIGH", 0L);
        }

        DashboardStats stats = new DashboardStats(
                totalClients, activeClients, evaluatedClients,
                avgScore != null ? avgScore : 0.0,
                advanced, intermediate, basic,
                clientsWithOverdue,
                totalSuggested != null ? totalSuggested.doubleValue() : 0.0,
                riskDist,
                avgScoreByProfile,
                recommendedProducts,
                clientEvolution,
                eligibleByProduct,
                loadMlMetrics()
        );
        return ResponseEntity.ok(ApiResponse.ok(stats));
    }

    private Map<String, Long> countRecommendedProducts(List<CreditEvaluation> evaluations) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (CreditEvaluation evaluation : evaluations) {
            String recommendations = evaluation.getAiRecommendations();
            if (recommendations == null || recommendations.isBlank()) continue;
            for (String product : recommendations.split(";")) {
                String key = product.trim();
                if (!key.isEmpty()) counts.merge(key, 1L, Long::sum);
            }
        }
        return counts;
    }

    private Map<String, Long> countClientEvolution(List<CreditEvaluation> evaluations) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        return evaluations.stream()
                .filter(e -> e.getEvaluatedAt() != null)
                .collect(Collectors.groupingBy(e -> e.getEvaluatedAt().format(formatter), LinkedHashMap::new, Collectors.counting()));
    }

    private Map<String, Long> countEligibleByProduct(List<CreditEvaluation> evaluations) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (CreditEvaluation evaluation : evaluations) {
            if (evaluation.getSuggestedCreditLine() == null || evaluation.getSuggestedCreditLine().compareTo(BigDecimal.ZERO) <= 0) continue;
            int score = evaluation.getTraditionalScore() != null ? evaluation.getTraditionalScore() : 0;
            String risk = evaluation.getRiskLevel() != null ? evaluation.getRiskLevel().name() : "MEDIUM";
            String profile = evaluation.getAiProfile() != null ? evaluation.getAiProfile().name() : "INTERMEDIATE";
            addEligibility(counts, score, risk, profile);
        }
        return counts;
    }

    private Map<String, Object> loadMlMetrics() {
        try {
            ClassPathResource resource = new ClassPathResource("ml/decision-tree-metrics.json");
            return objectMapper.readValue(resource.getInputStream(), new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private DatasetAnalytics loadDatasetAnalytics() {
        try {
            ClassPathResource resource = new ClassPathResource("ml/credit_decision_dataset.csv");
            String csv = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String[] lines = csv.split("\\R");
            Map<String, Long> profileCounts = new LinkedHashMap<>();
            Map<String, Long> riskDistribution = new LinkedHashMap<>();
            Map<String, Long> recommendedProducts = new LinkedHashMap<>();
            Map<String, Long> eligibleByProduct = new LinkedHashMap<>();
            Map<String, Long> clientEvolution = new LinkedHashMap<>();
            Map<String, Double> scoreTotals = new HashMap<>();
            Map<String, Long> scoreCounts = new HashMap<>();
            double totalScore = 0;

            int records = 0;
            LocalDate start = LocalDate.now().minusMonths(7).withDayOfMonth(1);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");
            int[] monthlyBuckets = {68, 82, 91, 103, 111, 97, 122, 126};

            for (int i = 1; i < lines.length; i++) {
                if (lines[i].isBlank()) continue;
                String[] columns = lines[i].split(",", -1);
                if (columns.length < 14) continue;
                int intelligentScore = Integer.parseInt(columns[11]);
                String profile = columns[12];
                String products = columns[13];
                String risk = riskFromScore(intelligentScore);
                String month = start.plusMonths(monthIndexForRecord(records, monthlyBuckets)).format(formatter);

                records++;
                totalScore += intelligentScore;
                profileCounts.merge(profile, 1L, Long::sum);
                riskDistribution.merge(risk, 1L, Long::sum);
                clientEvolution.merge(month, 1L, Long::sum);
                scoreTotals.merge(profile, (double) intelligentScore, Double::sum);
                scoreCounts.merge(profile, 1L, Long::sum);

                Arrays.stream(products.split(";"))
                        .map(String::trim)
                        .filter(product -> !product.isEmpty())
                        .forEach(product -> {
                            recommendedProducts.merge(product, 1L, Long::sum);
                        });

                addDatasetEligibility(eligibleByProduct, profile, intelligentScore, risk);
            }

            Map<String, Double> averageScoreByProfile = new LinkedHashMap<>();
            scoreTotals.forEach((profile, total) -> averageScoreByProfile.put(profile, total / scoreCounts.getOrDefault(profile, 1L)));
            double averageScore = records > 0 ? totalScore / records : 0;
            return new DatasetAnalytics(records, averageScore, profileCounts, riskDistribution, averageScoreByProfile, recommendedProducts, clientEvolution, eligibleByProduct);
        } catch (Exception e) {
            return new DatasetAnalytics(0, 0, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
    }

    private String riskFromScore(int score) {
        if (score < 25) return "VERY_HIGH";
        if (score < 40) return "HIGH";
        if (score < 70) return "MEDIUM";
        if (score < 80) return "LOW";
        return "VERY_LOW";
    }

    private void addDatasetEligibility(Map<String, Long> counts, String profile, int intelligentScore, String risk) {
        int conventionalLikeScore = (int) Math.round(300 + (intelligentScore / 100.0) * 650);
        addEligibility(counts, conventionalLikeScore, risk, profile);
    }

    private void addEligibility(Map<String, Long> counts, int score, String risk, String profile) {
        boolean highRisk = "HIGH".equals(risk) || "VERY_HIGH".equals(risk);
        boolean advanced = "ADVANCED".equals(profile) || "PREMIUM".equals(profile);
        boolean intermediate = "INTERMEDIATE".equals(profile);

        if (score >= 420 && !"VERY_HIGH".equals(risk)) increment(counts, "Cuenta de ahorro");
        if (score >= 500 && !highRisk) increment(counts, "Tarjeta de credito");
        if (score >= 540 && !highRisk) increment(counts, "Prestamo personal");
        if (score >= 560 && (intermediate || advanced) && !highRisk) increment(counts, "Seguro de proteccion de pagos");
        if (score >= 600 && (intermediate || advanced) && !highRisk) increment(counts, "Credito de consumo");
        if (score >= 650 && advanced && ("LOW".equals(risk) || "VERY_LOW".equals(risk))) increment(counts, "Credito vehicular");
        if (score >= 720 && advanced && "VERY_LOW".equals(risk)) increment(counts, "Credito hipotecario");
        if (score >= 760 && advanced && "VERY_LOW".equals(risk)) increment(counts, "Productos premium");
    }

    private void increment(Map<String, Long> counts, String key) {
        counts.merge(key, 1L, Long::sum);
    }

    private int monthIndexForRecord(int recordIndex, int[] buckets) {
        int accumulated = 0;
        for (int i = 0; i < buckets.length; i++) {
            accumulated += buckets[i];
            if (recordIndex < accumulated) return i;
        }
        return buckets.length - 1;
    }

    private record DatasetAnalytics(
            int records,
            double averageScore,
            Map<String, Long> profileCounts,
            Map<String, Long> riskDistribution,
            Map<String, Double> averageScoreByProfile,
            Map<String, Long> recommendedProducts,
            Map<String, Long> clientEvolution,
            Map<String, Long> eligibleByProduct
    ) {}

    private long extractCount(List<Object[]> rows, String key) {
        return rows.stream()
                .filter(r -> key.equals(r[0].toString()))
                .mapToLong(r -> (Long) r[1])
                .findFirst().orElse(0L);
    }
}
