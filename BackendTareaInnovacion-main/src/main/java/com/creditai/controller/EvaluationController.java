package com.creditai.controller;

import com.creditai.dto.*;
import com.creditai.entity.CreditEvaluation;
import com.creditai.entity.User;
import com.creditai.repository.CreditEvaluationRepository;
import com.creditai.repository.UserRepository;
import com.creditai.service.CreditEvaluationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/evaluations")
@RequiredArgsConstructor
public class EvaluationController {

    private final CreditEvaluationService evaluationService;
    private final UserRepository userRepository;
    private final CreditEvaluationRepository evalRepository;

    @PostMapping("/client/{clientId}")
    public ResponseEntity<ApiResponse<CreditEvaluationResponse>> evaluate(
            @PathVariable Long clientId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByUsername(userDetails.getUsername()).orElseThrow();
        return ResponseEntity.ok(ApiResponse.ok(evaluationService.evaluateClient(clientId, user)));
    }

    @PostMapping("/client/{clientId}/simulate")
    public ResponseEntity<ApiResponse<CreditEvaluationResponse>> simulate(
            @PathVariable Long clientId,
            @RequestBody SimulationRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByUsername(userDetails.getUsername()).orElseThrow();
        return ResponseEntity.ok(ApiResponse.ok(evaluationService.simulate(clientId, req, user)));
    }

    @PostMapping("/client/{clientId}/chat")
    public ResponseEntity<ApiResponse<ChatResponse>> chat(
            @PathVariable Long clientId,
            @RequestBody ChatRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(evaluationService.chat(clientId, req.message())));
    }

    @GetMapping("/client/{clientId}")
    public ResponseEntity<ApiResponse<List<CreditEvaluation>>> getByClient(@PathVariable Long clientId) {
        return ResponseEntity.ok(ApiResponse.ok(evalRepository.findByClientIdOrderByEvaluatedAtDesc(clientId)));
    }
}
