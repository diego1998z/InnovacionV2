package com.creditai.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "credit_evaluations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    // Score tradicional (reglas de negocio)
    @Column(nullable = false)
    private Integer traditionalScore;

    @Column(length = 500)
    private String scoreBreakdown; // JSON con el detalle del puntaje

    // Resultado IA
    @Enumerated(EnumType.STRING)
    private AIProfile aiProfile;

    @Enumerated(EnumType.STRING)
    private RiskLevel riskLevel;

    @Column(precision = 12, scale = 2)
    private BigDecimal suggestedCreditLine;

    @Column(columnDefinition = "TEXT")
    private String aiJustification;

    @Column(columnDefinition = "TEXT")
    private String aiRecommendations;

    @Column(columnDefinition = "TEXT")
    private String fullAiResponse; // Respuesta completa de la IA

    // Simulación (si aplica)
    private Boolean isSimulation = false;

    @Column(columnDefinition = "TEXT")
    private String simulationParams; // JSON con parámetros de simulación

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluated_by")
    private User evaluatedBy;

    @Column(updatable = false)
    private LocalDateTime evaluatedAt;

    @PrePersist
    protected void onCreate() {
        evaluatedAt = LocalDateTime.now();
    }

    public enum AIProfile {
        BASIC, INTERMEDIATE, ADVANCED, PREMIUM,
        DIGITAL_ENTREPRENEUR, CONSERVATIVE_CLIENT, HIGH_POTENTIAL, EMERGING_RISK
    }

    public enum RiskLevel {
        VERY_LOW, LOW, MEDIUM, HIGH, VERY_HIGH
    }
}
