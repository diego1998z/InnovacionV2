package com.creditai.service;

import com.creditai.entity.Client;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
public class GenerativeExplanationService {

    @Value("${explanation.provider:local}")
    private String provider;

    @Value("${ai.gemini.api-key:}")
    private String geminiApiKey;

    @Value("${ai.gemini.model:gemini-2.5-flash-lite}")
    private String geminiModel;

    @Value("${ai.gemini.url:https://generativelanguage.googleapis.com/v1beta/models}")
    private String geminiBaseUrl;

    @Value("${ai.groq.api-key:}")
    private String groqApiKey;

    @Value("${ai.groq.model:llama-3.1-8b-instant}")
    private String groqModel;

    @Value("${ai.groq.url:https://api.groq.com/openai/v1/chat/completions}")
    private String groqUrl;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public GenerativeExplanationService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    public String explain(Client client, DecisionTreeCreditService.ModelResult result, String userMessage) {
        String localReply = localExplanation(result, userMessage);
        String prompt = buildPrompt(client, result, userMessage);
        return switch (provider.toLowerCase()) {
            case "gemini" -> callGemini(prompt, localReply);
            case "groq" -> callGroq(prompt, localReply);
            default -> localReply;
        };
    }

    private String buildPrompt(Client client, DecisionTreeCreditService.ModelResult result, String userMessage) {
        return String.format("""
                Actúa como asistente conversacional de un sistema bancario académico. Responde natural y en español usando el resultado del Árbol de Decisión como contexto.

                Reglas:
                - No apruebes crédito automáticamente.
                - No contradigas al modelo.
                - Si el usuario saluda, saluda y ofrece ayuda para explicar score, riesgo, perfil o productos.
                - Si el usuario dice que no entiende algo, explica esa parte con palabras simples.
                - Si pregunta por producto, riesgo, score o perfil, explica usando los datos del árbol.
                - Máximo 3 párrafos cortos.

                Cliente: %s
                Perfil del árbol: %s
                Riesgo: %s
                Score inteligente: %d/100
                Línea sugerida referencial: S/ %s
                Productos sugeridos: %s
                Pregunta del usuario: %s
                """,
                client.getFullName(),
                result.profile(),
                result.riskLevel(),
                result.intelligentScore(),
                result.suggestedCreditLine(),
                result.recommendations(),
                userMessage
        );
    }

    private String callGemini(String prompt, String fallback) {
        if (isMissingKey(geminiApiKey)) return fallback;
        try {
            String url = geminiBaseUrl + "/" + geminiModel + ":generateContent";
            Map<String, Object> body = Map.of(
                    "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt))))
            );
            String response = webClient.post()
                    .uri(url)
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", geminiApiKey)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            JsonNode root = objectMapper.readTree(response);
            String text = root.at("/candidates/0/content/parts/0/text").asText();
            return text.isBlank() ? fallback : text;
        } catch (Exception e) {
            return fallback;
        }
    }

    private String callGroq(String prompt, String fallback) {
        if (isMissingKey(groqApiKey)) return fallback;
        try {
            Map<String, Object> body = Map.of(
                    "model", groqModel,
                    "messages", List.of(Map.of("role", "user", "content", prompt)),
                    "temperature", 0.2,
                    "max_tokens", 500
            );
            String response = webClient.post()
                    .uri(groqUrl)
                    .header("Authorization", "Bearer " + groqApiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            JsonNode root = objectMapper.readTree(response);
            String text = root.at("/choices/0/message/content").asText();
            return text.isBlank() ? fallback : text;
        } catch (Exception e) {
            return fallback;
        }
    }

    private String localExplanation(DecisionTreeCreditService.ModelResult result, String userMessage) {
        String normalized = userMessage == null ? "" : userMessage.trim().toLowerCase();
        if (normalized.isBlank()) {
            return "Puedo ayudarte a entender el perfil, riesgo, score o productos recomendados por el Árbol de Decisión para este cliente.";
        }
        if (normalized.matches(".*\\b(hola|buenas|buenos dias|buenos días|buenas tardes|buenas noches|hey)\\b.*")) {
            return "Hola. Puedo ayudarte a explicar el resultado del Árbol de Decisión: perfil crediticio, nivel de riesgo, score inteligente, línea sugerida o productos recomendados.";
        }
        if (normalized.matches(".*\\b(no entiendo|explica|explicame|explícame|por que|por qué|porque|qué significa|que significa)\\b.*")) {
            return String.format(
                    "Te lo explico simple: el Árbol clasificó al cliente como %s porque combinó score, deuda, capacidad de pago, historial, mora y ahorro. El riesgo quedó en %s y el score inteligente fue %d/100. Con esos datos, el sistema sugiere: %s.",
                    result.profile(),
                    result.riskLevel(),
                    result.intelligentScore(),
                    result.recommendations()
            );
        }
        return String.format(
                "El Árbol de Decisión clasificó al cliente como %s con riesgo %s y score inteligente %d/100. Por eso sugiere: %s. Esta recomendación es referencial y debe validarse con política bancaria, documentación e historial actualizado. Consulta: %s",
                result.profile(),
                result.riskLevel(),
                result.intelligentScore(),
                result.recommendations(),
                userMessage
        );
    }

    private boolean isMissingKey(String apiKey) {
        return apiKey == null || apiKey.isBlank() || apiKey.startsWith("YOUR_");
    }
}
