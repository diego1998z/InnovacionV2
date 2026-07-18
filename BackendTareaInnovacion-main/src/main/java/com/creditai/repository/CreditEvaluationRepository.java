package com.creditai.repository;

import com.creditai.entity.CreditEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CreditEvaluationRepository extends JpaRepository<CreditEvaluation, Long> {
    List<CreditEvaluation> findByClientIdOrderByEvaluatedAtDesc(Long clientId);

    @Query("SELECT AVG(e.traditionalScore) FROM CreditEvaluation e WHERE e.isSimulation = false")
    Double averageScore();

    @Query("SELECT COUNT(e) FROM CreditEvaluation e WHERE e.isSimulation = false")
    long countEvaluated();

    @Query("SELECT COUNT(DISTINCT e.client.id) FROM CreditEvaluation e WHERE e.isSimulation = false")
    long countEvaluatedClients();

    @Query("SELECT COALESCE(SUM(e.suggestedCreditLine), 0) FROM CreditEvaluation e WHERE e.isSimulation = false")
    java.math.BigDecimal totalSuggestedCredit();

    @Query("SELECT e.aiProfile, COUNT(e) FROM CreditEvaluation e " +
           "WHERE e.isSimulation = false GROUP BY e.aiProfile")
    List<Object[]> countByProfile();

    @Query("SELECT e.riskLevel, COUNT(e) FROM CreditEvaluation e " +
           "WHERE e.isSimulation = false GROUP BY e.riskLevel")
    List<Object[]> countByRiskLevel();

    @Query("SELECT e.aiProfile, AVG(e.traditionalScore) FROM CreditEvaluation e " +
           "WHERE e.isSimulation = false GROUP BY e.aiProfile")
    List<Object[]> averageScoreByProfile();

    List<CreditEvaluation> findByIsSimulationFalse();
}
