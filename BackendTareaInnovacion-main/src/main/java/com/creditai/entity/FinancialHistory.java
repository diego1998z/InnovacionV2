package com.creditai.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "financial_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinancialHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecordType recordType;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(length = 200)
    private String description;

    @Column(nullable = false)
    private LocalDate recordDate;

    // Para cuotas/créditos
    private Integer totalInstallments;
    private Integer paidInstallments;
    private Integer overdueInstallments;

    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus;

    @Column(precision = 12, scale = 2)
    private BigDecimal overdueAmount;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum RecordType {
        INCOME, EXPENSE, PAYMENT, INSTALLMENT, CREDIT_PRODUCT, SAVINGS_DEPOSIT
    }

    public enum PaymentStatus {
        ON_TIME, LATE, OVERDUE, DEFAULTED
    }
}
