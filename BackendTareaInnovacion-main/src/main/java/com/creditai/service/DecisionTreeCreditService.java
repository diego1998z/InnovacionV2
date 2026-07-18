package com.creditai.service;

import com.creditai.entity.Client;
import com.creditai.entity.FinancialHistory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DecisionTreeCreditService {

    private static final String MODEL_PATH = "ml/decision-tree-model.json";

    private final ObjectMapper objectMapper;
    private JsonNode tree;
    private JsonNode profileProducts;

    public DecisionTreeCreditService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void loadModel() throws Exception {
        try (InputStream input = new ClassPathResource(MODEL_PATH).getInputStream()) {
            JsonNode model = objectMapper.readTree(input);
            this.tree = model.path("tree");
            this.profileProducts = model.path("profileProducts");
        }
    }

    public ModelResult evaluate(Client client, List<FinancialHistory> history, int conventionalScore) {
        Map<String, Double> features = extractFeatures(client, history, conventionalScore);
        String profile = classify(features);
        int intelligentScore = calculateIntelligentScore(features);
        String riskLevel = riskLevel(intelligentScore, features.get("has_mora"));
        BigDecimal suggestedCreditLine = suggestedCreditLine(profile, riskLevel, client.getMonthlyIncome(), features.get("payment_capacity"));
        String recommendations = recommendationsFor(profile);
        String justification = buildJustification(profile, intelligentScore, riskLevel, features);
        String rawResponse = buildRawResponse(profile, intelligentScore, riskLevel, suggestedCreditLine, recommendations, features);

        return new ModelResult(profile, riskLevel, suggestedCreditLine, justification, recommendations, rawResponse, features, intelligentScore);
    }

    public String explain(Client client, List<FinancialHistory> history, int conventionalScore, String userMessage) {
        ModelResult result = evaluate(client, history, conventionalScore);
        return String.format(
                "Modelo de Árbol de Decisión: perfil %s, score inteligente %d/100 y riesgo %s. %s Productos sugeridos: %s. Consulta: %s",
                displayProfile(result.profile()),
                result.intelligentScore(),
                displayRisk(result.riskLevel()),
                result.justification(),
                result.recommendations(),
                userMessage
        );
    }

    private Map<String, Double> extractFeatures(Client client, List<FinancialHistory> history, int conventionalScore) {
        double monthlyIncome = value(client.getMonthlyIncome());
        double savings = value(client.getTotalSavings());
        double debts = value(client.getCurrentDebts());
        long totalPayments = history.stream().filter(h -> h.getPaymentStatus() != null).count();
        long onTimePayments = history.stream().filter(h -> h.getPaymentStatus() == FinancialHistory.PaymentStatus.ON_TIME).count();
        long activeCredits = history.stream().filter(h -> h.getRecordType() == FinancialHistory.RecordType.CREDIT_PRODUCT).count();
        long products = history.stream().map(FinancialHistory::getRecordType).distinct().count();
        boolean hasMora = history.stream().anyMatch(h -> h.getPaymentStatus() == FinancialHistory.PaymentStatus.OVERDUE || h.getPaymentStatus() == FinancialHistory.PaymentStatus.DEFAULTED);
        double debtRatio = monthlyIncome > 0 ? (debts / monthlyIncome) * 100 : 0;
        double paymentCapacity = monthlyIncome > 0 ? Math.max(0, ((monthlyIncome - debts) / monthlyIncome) * 100) : 0;
        double paymentHistoryRate = totalPayments > 0 ? ((double) onTimePayments / totalPayments) * 100 : 75;
        double normalizedConventionalScore = Math.max(0, Math.min(100, ((conventionalScore - 300) / 650.0) * 100));

        Map<String, Double> features = new LinkedHashMap<>();
        features.put("monthly_income", monthlyIncome);
        features.put("payment_history_rate", paymentHistoryRate);
        features.put("savings_level", savings);
        features.put("active_credits", (double) activeCredits);
        features.put("debt_ratio", debtRatio);
        features.put("payment_capacity", paymentCapacity);
        features.put("employment_months", estimateEmploymentMonths(client.getAge()));
        features.put("conventional_score", normalizedConventionalScore);
        features.put("product_count", (double) products);
        features.put("has_mora", hasMora ? 1.0 : 0.0);
        features.put("age", client.getAge() != null ? client.getAge().doubleValue() : 0.0);
        return features;
    }

    private String classify(Map<String, Double> features) {
        JsonNode node = tree;
        while (!node.has("prediction")) {
            String feature = node.path("feature").asText();
            double threshold = node.path("threshold").asDouble();
            double value = features.getOrDefault(feature, 0.0);
            node = value <= threshold ? node.path("left") : node.path("right");
        }
        return node.path("prediction").asText("INTERMEDIATE");
    }

    private int calculateIntelligentScore(Map<String, Double> features) {
        double score = features.get("conventional_score") * 0.55
                + features.get("payment_history_rate") * 0.22
                + features.get("payment_capacity") * 0.16
                + Math.min(features.get("savings_level") / Math.max(1, features.get("monthly_income")) * 2.0, 7)
                - features.get("has_mora") * 16
                - (features.get("debt_ratio") > 65 ? 10 : 0);
        return (int) Math.max(0, Math.min(100, Math.round(score)));
    }

    private String riskLevel(int intelligentScore, Double hasMora) {
        if (hasMora != null && hasMora >= 1.0) return "HIGH";
        if (intelligentScore >= 80) return "VERY_LOW";
        if (intelligentScore >= 70) return "LOW";
        if (intelligentScore >= 40) return "MEDIUM";
        return "HIGH";
    }

    private BigDecimal suggestedCreditLine(String profile, String riskLevel, BigDecimal income, double paymentCapacity) {
        if ("HIGH".equals(riskLevel) || "VERY_HIGH".equals(riskLevel)) return BigDecimal.ZERO;
        double monthlyIncome = value(income);
        double multiplier = switch (profile) {
            case "ADVANCED" -> 6.0;
            case "INTERMEDIATE" -> 2.5;
            default -> 0.8;
        };
        double capacityFactor = Math.max(0.25, Math.min(1.0, paymentCapacity / 100));
        return BigDecimal.valueOf(Math.round(monthlyIncome * multiplier * capacityFactor));
    }

    private String recommendationsFor(String profile) {
        JsonNode products = profileProducts.path(profile);
        if (!products.isArray()) return "Cuenta de ahorro; Evaluación personalizada";
        StringBuilder builder = new StringBuilder();
        for (JsonNode product : products) {
            if (!builder.isEmpty()) builder.append("; ");
            builder.append(product.asText());
        }
        return builder.toString();
    }

    private String buildJustification(String profile, int intelligentScore, String riskLevel, Map<String, Double> features) {
        String debtSignal = features.get("debt_ratio") <= 30
                ? "endeudamiento controlado"
                : features.get("debt_ratio") <= 60 ? "endeudamiento moderado" : "endeudamiento elevado";
        String capacitySignal = features.get("payment_capacity") >= 70
                ? "alta capacidad de pago"
                : features.get("payment_capacity") >= 40 ? "capacidad de pago media" : "capacidad de pago limitada";
        String moraSignal = features.get("has_mora") >= 1
                ? "presenta mora financiera, lo que reduce su perfil"
                : "no presenta mora financiera relevante";
        String historySignal = features.get("payment_history_rate") >= 85
                ? "historial de pagos favorable"
                : features.get("payment_history_rate") >= 60 ? "historial de pagos aceptable" : "historial de pagos débil";
        String savingsSignal = features.get("savings_level") >= features.get("monthly_income") * 3
                ? "ahorro suficiente como respaldo"
                : "ahorro limitado frente a sus ingresos";

        return String.format(
                "El Árbol de Decisión clasificó al cliente como %s con score inteligente %d/100 y riesgo %s. El resultado se explica por %s, %s, %s, %s y %s. Score convencional normalizado: %.0f/100; créditos activos: %.0f.",
                displayProfile(profile),
                intelligentScore,
                displayRisk(riskLevel),
                debtSignal,
                capacitySignal,
                moraSignal,
                historySignal,
                savingsSignal,
                features.get("conventional_score"),
                features.get("active_credits")
        );
    }

    private String buildRawResponse(String profile, int intelligentScore, String riskLevel, BigDecimal suggestedCreditLine, String recommendations, Map<String, Double> features) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "model", "Decision Tree CART",
                    "profile", profile,
                    "intelligentScore", intelligentScore,
                    "riskLevel", riskLevel,
                    "suggestedCreditLine", suggestedCreditLine,
                    "recommendations", recommendations,
                    "features", features
            ));
        } catch (Exception e) {
            return "{}";
        }
    }

    private double estimateEmploymentMonths(Integer age) {
        if (age == null || age < 18) return 0;
        return Math.min(240, Math.max(0, (age - 18) * 8.0));
    }

    private double value(BigDecimal value) {
        return value != null ? value.doubleValue() : 0;
    }

    private String displayProfile(String profile) {
        return switch (profile) {
            case "BASIC" -> "Básico";
            case "ADVANCED" -> "Avanzado";
            default -> "Intermedio";
        };
    }

    private String displayRisk(String riskLevel) {
        return switch (riskLevel) {
            case "VERY_LOW" -> "muy bajo";
            case "LOW" -> "bajo";
            case "HIGH" -> "alto";
            case "VERY_HIGH" -> "muy alto";
            default -> "moderado";
        };
    }

    public record ModelResult(
            String profile,
            String riskLevel,
            BigDecimal suggestedCreditLine,
            String justification,
            String recommendations,
            String rawResponse,
            Map<String, Double> features,
            int intelligentScore
    ) {}
}
