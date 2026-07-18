package com.creditai.controller;

import com.creditai.dto.ApiResponse;
import com.creditai.dto.ClientRequest;
import com.creditai.dto.ClientResponse;
import com.creditai.dto.FinancialHistoryRequest;
import com.creditai.dto.FinancialHistoryResponse;
import com.creditai.entity.Client;
import com.creditai.entity.FinancialHistory;
import com.creditai.repository.ClientRepository;
import com.creditai.repository.FinancialHistoryRepository;
import com.creditai.service.ClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientRepository clientRepository;
    private final ClientService clientService;
    private final FinancialHistoryRepository histRepo;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ClientResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(clientService.findAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ClientResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(clientService.findById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ClientResponse>> create(@RequestBody ClientRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Cliente registrado", clientService.create(req)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ClientResponse>> update(@PathVariable Long id,
                                                               @RequestBody ClientRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Cliente actualizado", clientService.update(id, req)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        clientService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Cliente eliminado", null));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<ApiResponse<List<FinancialHistoryResponse>>> getHistory(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(histRepo.findByClientIdOrderByRecordDateDesc(id).stream()
                .map(this::mapHistory)
                .toList()));
    }

    @PostMapping("/{id}/history")
    public ResponseEntity<ApiResponse<FinancialHistoryResponse>> addHistory(@PathVariable Long id,
                                                                             @RequestBody FinancialHistoryRequest req) {
        Client client = clientRepository.findById(id).orElseThrow();
        FinancialHistory fh = FinancialHistory.builder()
                .client(client)
                .recordType(FinancialHistory.RecordType.valueOf(req.recordType()))
                .amount(req.amount())
                .description(req.description())
                .recordDate(LocalDate.parse(req.recordDate()))
                .totalInstallments(req.totalInstallments())
                .paidInstallments(req.paidInstallments())
                .overdueInstallments(req.overdueInstallments())
                .paymentStatus(req.paymentStatus() != null
                        ? FinancialHistory.PaymentStatus.valueOf(req.paymentStatus()) : null)
                .overdueAmount(req.overdueAmount())
                .build();
        return ResponseEntity.ok(ApiResponse.ok("Historial registrado", mapHistory(histRepo.save(fh))));
    }

    private FinancialHistoryResponse mapHistory(FinancialHistory fh) {
        return new FinancialHistoryResponse(
                fh.getId(),
                fh.getClient().getId(),
                fh.getRecordType() != null ? fh.getRecordType().name() : null,
                fh.getAmount(),
                fh.getDescription(),
                fh.getRecordDate(),
                fh.getTotalInstallments(),
                fh.getPaidInstallments(),
                fh.getOverdueInstallments(),
                fh.getPaymentStatus() != null ? fh.getPaymentStatus().name() : null,
                fh.getOverdueAmount(),
                fh.getCreatedAt()
        );
    }
}
