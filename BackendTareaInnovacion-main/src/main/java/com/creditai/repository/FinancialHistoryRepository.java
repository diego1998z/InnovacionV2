package com.creditai.repository;

import com.creditai.entity.FinancialHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FinancialHistoryRepository extends JpaRepository<FinancialHistory, Long> {
    List<FinancialHistory> findByClientId(Long clientId);
    List<FinancialHistory> findByClientIdOrderByRecordDateDesc(Long clientId);

    @Query("SELECT COUNT(f) FROM FinancialHistory f WHERE f.client.id = :clientId " +
           "AND f.paymentStatus IN ('OVERDUE','DEFAULTED')")
    long countOverdueByClient(Long clientId);
}
