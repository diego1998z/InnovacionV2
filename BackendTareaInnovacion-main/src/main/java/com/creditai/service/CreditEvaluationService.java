package com.creditai.service;

import com.creditai.dto.*;
import com.creditai.entity.*;
import com.creditai.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CreditEvaluationService {

    private final ClientRepository         clientRepository;
    private final FinancialHistoryRepository historyRepository;
    private final CreditEvaluationRepository evaluationRepository;
    private final ScoreCalculationService  scoreService;
    private final DecisionTreeCreditService decisionTreeService;
    private final GenerativeExplanationService explanationService;
    private final ObjectMapper             objectMapper;

    // ── Evaluación completa ──────────────────────────────────────────────

    @Transactional
    public CreditEvaluationResponse evaluateClient(Long clientId, User currentUser) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado: " + clientId));

        List<FinancialHistory> history = historyRepository.findByClientId(clientId);

        // 1. Score tradicional
        ScoreCalculationService.ScoreResult scoreResult = scoreService.calculate(client, history);

        // 2. Clasificación con Árbol de Decisión
        DecisionTreeCreditService.ModelResult modelResult = decisionTreeService.evaluate(client, history, scoreResult.score());

        // 3. Persistir evaluación
        CreditEvaluation evaluation = buildEvaluation(client, scoreResult, modelResult, currentUser, false, null);
        evaluation = evaluationRepository.save(evaluation);

        return mapToResponse(evaluation, scoreResult);
    }

    // ── Simulación ────────────────────────────────────────────────────────

    @Transactional
    public CreditEvaluationResponse simulate(Long clientId, SimulationRequest sim, User currentUser) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado: " + clientId));

        // Clonar cliente con los valores simulados (sin persistir)
        Client simClient = cloneWithSimulation(client, sim);
        List<FinancialHistory> history = historyRepository.findByClientId(clientId);

        ScoreCalculationService.ScoreResult scoreResult = scoreService.calculate(simClient, history);
        DecisionTreeCreditService.ModelResult modelResult = decisionTreeService.evaluate(simClient, history, scoreResult.score());

        String simParamsJson = toJson(sim);
        CreditEvaluation evaluation = buildEvaluation(simClient, scoreResult, modelResult, currentUser, true, simParamsJson);
        evaluation = evaluationRepository.save(evaluation);

        return mapToResponse(evaluation, scoreResult);
    }

    // ── Explicación del modelo ────────────────────────────────────────────

    public ChatResponse chat(Long clientId, String userMessage) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado: " + clientId));
        List<FinancialHistory> history = historyRepository.findByClientId(clientId);
        ScoreCalculationService.ScoreResult scoreResult = scoreService.calculate(client, history);

        DecisionTreeCreditService.ModelResult modelResult = decisionTreeService.evaluate(client, history, scoreResult.score());
        String aiReply = explanationService.explain(client, modelResult, userMessage);
        return new ChatResponse(aiReply, scoreResult.score());
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private CreditEvaluation buildEvaluation(Client client,
                                              ScoreCalculationService.ScoreResult scoreResult,
                                              DecisionTreeCreditService.ModelResult modelResult,
                                              User user, boolean isSimulation, String simParams) {
        return CreditEvaluation.builder()
                .client(client)
                .traditionalScore(scoreResult.score())
                .scoreBreakdown(toJson(scoreResult.breakdown()))
                .aiProfile(mapProfile(modelResult.profile()))
                .riskLevel(mapRisk(modelResult.riskLevel()))
                .suggestedCreditLine(modelResult.suggestedCreditLine())
                .aiJustification(modelResult.justification())
                .aiRecommendations(modelResult.recommendations())
                .fullAiResponse(modelResult.rawResponse())
                .isSimulation(isSimulation)
                .simulationParams(simParams)
                .evaluatedBy(user)
                .build();
    }

    private Client cloneWithSimulation(Client original, SimulationRequest sim) {
        return Client.builder()
                .id(original.getId())
                .dni(original.getDni())
                .fullName(original.getFullName())
                .age(original.getAge())
                .status(original.getStatus())
                .monthlyIncome(sim.monthlyIncome() != null ? sim.monthlyIncome() : original.getMonthlyIncome())
                .totalSavings(sim.totalSavings() != null ? sim.totalSavings() : original.getTotalSavings())
                .currentDebts(sim.currentDebts() != null ? sim.currentDebts() : original.getCurrentDebts())
                .build();
    }

    private CreditEvaluationResponse mapToResponse(CreditEvaluation ev, ScoreCalculationService.ScoreResult score) {
        return new CreditEvaluationResponse(
                ev.getId(),
                ev.getClient().getId(),
                ev.getClient().getFullName(),
                ev.getTraditionalScore(),
                score.interpretation(),
                score.breakdown(),
                ev.getAiProfile() != null ? ev.getAiProfile().name() : null,
                ev.getRiskLevel() != null ? ev.getRiskLevel().name() : null,
                ev.getSuggestedCreditLine(),
                ev.getAiJustification(),
                ev.getAiRecommendations(),
                ev.getIsSimulation(),
                ev.getEvaluatedAt()
        );
    }

    private CreditEvaluation.AIProfile mapProfile(String raw) {
        try { return CreditEvaluation.AIProfile.valueOf(raw.toUpperCase().replace(" ", "_")); }
        catch (Exception e) { return CreditEvaluation.AIProfile.INTERMEDIATE; }
    }

    private CreditEvaluation.RiskLevel mapRisk(String raw) {
        try {
            return switch (raw.toUpperCase().replace(" ", "_")) {
                case "MUY_BAJO", "VERY_LOW" -> CreditEvaluation.RiskLevel.VERY_LOW;
                case "BAJO",     "LOW"      -> CreditEvaluation.RiskLevel.LOW;
                case "ALTO",     "HIGH"     -> CreditEvaluation.RiskLevel.HIGH;
                case "MUY_ALTO", "VERY_HIGH"-> CreditEvaluation.RiskLevel.VERY_HIGH;
                default                    -> CreditEvaluation.RiskLevel.MEDIUM;
            };
        } catch (Exception e) { return CreditEvaluation.RiskLevel.MEDIUM; }
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (Exception e) { return "{}"; }
    }
}
