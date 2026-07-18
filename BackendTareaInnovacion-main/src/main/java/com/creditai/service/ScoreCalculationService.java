package com.creditai.service;

import com.creditai.entity.Client;
import com.creditai.entity.FinancialHistory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Service
public class ScoreCalculationService {

    private static final int MAX_SCORE = 1000;
    private static final int BASE_SCORE = 300;

    public ScoreResult calculate(Client client, List<FinancialHistory> history) {
        Map<String, Integer> breakdown = new HashMap<>();
        int score = BASE_SCORE;

        // 1. Ingresos mensuales
        int incomePoints = evaluateIncome(client.getMonthlyIncome());
        breakdown.put("income", incomePoints);
        score += incomePoints;

        // 2. Ahorros totales
        int savingsPoints = evaluateSavings(client.getTotalSavings());
        breakdown.put("savings", savingsPoints);
        score += savingsPoints;

        // 3. Edad del cliente
        int agePoints = evaluateAge(client.getAge());
        breakdown.put("age", agePoints);
        score += agePoints;

        // 4. Relación deuda / ingreso
        int debtRatioPoints = evaluateDebtRatio(client.getCurrentDebts(), client.getMonthlyIncome());
        breakdown.put("debtRatio", debtRatioPoints);
        score += debtRatioPoints;

        // 5. Historial de pagos
        int paymentHistoryPoints = evaluatePaymentHistory(history);
        breakdown.put("paymentHistory", paymentHistoryPoints);
        score += paymentHistoryPoints;

        // 6. Moras activas
        int overduePoints = evaluateOverduePayments(history);
        breakdown.put("overdues", overduePoints);
        score += overduePoints;

        // 7. Productos financieros activos
        int productsPoints = evaluateFinancialProducts(history);
        breakdown.put("financialProducts", productsPoints);
        score += productsPoints;

        // Clamp entre 300 y 950
        score = Math.max(300, Math.min(950, score));

        return new ScoreResult(score, breakdown, interpretScore(score));
    }

    // ── Reglas individuales ──────────────────────────────────────────────

    private int evaluateIncome(BigDecimal income) {
        if (income == null) return 0;
        double val = income.doubleValue();
        if (val >= 8000) return 40;
        if (val >= 5000) return 30;
        if (val >= 3000) return 20;
        if (val >= 1500) return 10;
        return 0;
    }

    private int evaluateSavings(BigDecimal savings) {
        if (savings == null) return 0;
        double val = savings.doubleValue();
        if (val >= 20000) return 50;
        if (val >= 10000) return 35;
        if (val >= 5000) return 25;
        if (val >= 2000) return 15;
        if (val >= 500)  return 5;
        return 0;
    }

    private int evaluateAge(int age) {
        if (age >= 25 && age <= 55) return 20;
        if (age >= 18 && age < 25)  return 10;
        if (age > 55 && age <= 65)  return 15;
        return 5; // < 18 o > 65
    }

    private int evaluateDebtRatio(BigDecimal debts, BigDecimal income) {
        if (debts == null || income == null || income.doubleValue() == 0) return 0;
        double ratio = debts.doubleValue() / income.doubleValue();
        if (ratio == 0)         return 40;
        if (ratio <= 0.2)       return 30;
        if (ratio <= 0.4)       return 15;
        if (ratio <= 0.6)       return 0;
        if (ratio <= 0.8)       return -20;
        return -50; // ratio > 0.8 → muy endeudado
    }

    private int evaluatePaymentHistory(List<FinancialHistory> history) {
        if (history == null || history.isEmpty()) return 0;
        long total    = history.stream().filter(h -> h.getPaymentStatus() != null).count();
        long onTime   = history.stream().filter(h -> h.getPaymentStatus() == FinancialHistory.PaymentStatus.ON_TIME).count();
        if (total == 0) return 0;
        double pct = (double) onTime / total;
        if (pct >= 0.95) return 80;
        if (pct >= 0.85) return 50;
        if (pct >= 0.70) return 25;
        if (pct >= 0.50) return 0;
        return -30;
    }

    private int evaluateOverduePayments(List<FinancialHistory> history) {
        if (history == null || history.isEmpty()) return 0;
        long overdue = history.stream()
                .filter(h -> h.getPaymentStatus() == FinancialHistory.PaymentStatus.OVERDUE
                          || h.getPaymentStatus() == FinancialHistory.PaymentStatus.DEFAULTED)
                .count();
        if (overdue == 0)  return 30;
        if (overdue == 1)  return -20;
        if (overdue <= 3)  return -50;
        return -100;
    }

    private int evaluateFinancialProducts(List<FinancialHistory> history) {
        if (history == null) return 0;
        long products = history.stream()
                .filter(h -> h.getRecordType() == FinancialHistory.RecordType.CREDIT_PRODUCT)
                .count();
        if (products == 0) return 5;
        if (products <= 2) return 15;
        if (products <= 4) return 10;
        return 5; // demasiados puede indicar dependencia de crédito
    }

    // ── Interpretación ───────────────────────────────────────────────────

    private String interpretScore(int score) {
        if (score >= 800) return "EXCELENTE";
        if (score >= 700) return "MUY BUENO";
        if (score >= 600) return "BUENO";
        if (score >= 500) return "REGULAR";
        if (score >= 400) return "BAJO";
        return "MUY BAJO";
    }

    // ── DTO interno ───────────────────────────────────────────────────────

    public record ScoreResult(int score, Map<String, Integer> breakdown, String interpretation) {}
}
