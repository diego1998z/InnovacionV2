package com.creditai.ai;

import com.creditai.entity.Client;
import com.creditai.entity.FinancialHistory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;


@Service
public class AIService {

    @Value("${ai.provider:google_gemini}")
    private String provider;

    @Value("${ai.gemini.api-key:}") private String geminiApiKey;
    @Value("${ai.gemini.url:}")     private String geminiUrl;
    @Value("${ai.gemini.model:gemini-1.5-flash}") private String geminiModel;

    @Value("${ai.openai.api-key:}") private String openaiApiKey;
    @Value("${ai.openai.url:}")     private String openaiUrl;
    @Value("${ai.openai.model:gpt-4o-mini}") private String openaiModel;

    @Value("${ai.openrouter.api-key:}") private String openrouterApiKey;
    @Value("${ai.openrouter.url:}")     private String openrouterUrl;
    @Value("${ai.openrouter.model:meta-llama/llama-3.1-8b-instruct:free}") private String openrouterModel;

    @Value("${ai.groq.api-key:}") private String groqApiKey;
    @Value("${ai.groq.url:}")     private String groqUrl;
    @Value("${ai.groq.model:llama-3.1-8b-instant}") private String groqModel;

    @Value("${ai.ollama.url:http://localhost:11434/api/generate}") private String ollamaUrl;
    @Value("${ai.ollama.model:llama3}") private String ollamaModel;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public AIService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient    = webClientBuilder.build();
        this.objectMapper = objectMapper;
    }


    public AIEvaluationResult evaluate(Client client, List<FinancialHistory> history, int traditionalScore) {
        String prompt = buildCreditPrompt(client, history, traditionalScore);
        String rawResponse = switch (provider.toLowerCase()) {
            case "openai", "groq", "openrouter" -> callOpenAICompatible(prompt);
            case "ollama"                        -> callOllama(prompt);
            default                              -> callGemini(prompt);  // google_gemini
        };
        return parseAIResponse(rawResponse);
    }

    public String chat(Client client, List<FinancialHistory> history, int score, String userMessage) {
        String context = buildContextSummary(client, history, score);
        String prompt  = String.format("""
                Contexto del cliente para análisis crediticio:
                %s

                Pregunta del analista: %s

                Responde de forma clara, profesional y en español.
                Sé específico con los datos del cliente.
                """, context, userMessage);

        return switch (provider.toLowerCase()) {
            case "openai", "groq", "openrouter" -> callOpenAICompatible(prompt);
            case "ollama"                        -> callOllama(prompt);
            default                              -> callGemini(prompt);
        };
    }


    private String buildCreditPrompt(Client client, List<FinancialHistory> history, int traditionalScore) {
        long overdues = history.stream()
                .filter(h -> h.getPaymentStatus() != null &&
                        (h.getPaymentStatus() == FinancialHistory.PaymentStatus.OVERDUE ||
                         h.getPaymentStatus() == FinancialHistory.PaymentStatus.DEFAULTED))
                .count();
        long onTime = history.stream()
                .filter(h -> h.getPaymentStatus() == FinancialHistory.PaymentStatus.ON_TIME)
                .count();

        return String.format("""
                Eres un experto en evaluación crediticia bancaria para el mercado peruano.
                Analiza el siguiente perfil de cliente y genera una evaluación crediticia completa.

                === DATOS DEL CLIENTE ===
                Nombre: %s
                DNI: %s
                Edad: %d años
                Ingreso mensual: S/ %.2f
                Ahorros totales: S/ %.2f
                Deudas actuales: S/ %.2f
                Estado: %s

                === HISTORIAL FINANCIERO ===
                Total de registros: %d
                Pagos puntuales: %d
                Moras / Impagos: %d

                === SCORE TRADICIONAL ===
                Score calculado: %d / 950
                Interpretación: %s

                === INSTRUCCIONES DE RESPUESTA ===
                Responde ÚNICAMENTE con un JSON válido con esta estructura exacta:
                {
                  "perfil": "BASIC|INTERMEDIATE|PREMIUM|DIGITAL_ENTREPRENEUR|CONSERVATIVE_CLIENT|HIGH_POTENTIAL|EMERGING_RISK",
                  "nivelRiesgo": "MUY_BAJO|BAJO|MEDIO|ALTO|MUY_ALTO",
                  "montoSugerido": <número sin símbolo de moneda>,
                  "justificacion": "<párrafo explicando la decisión>",
                  "recomendaciones": "<lista de 2-3 recomendaciones concretas>",
                  "resumenEjecutivo": "<2 oraciones para el dashboard>"
                }
                """,
                client.getFullName(), client.getDni(), client.getAge(),
                client.getMonthlyIncome(), client.getTotalSavings(),
                client.getCurrentDebts() != null ? client.getCurrentDebts() : BigDecimal.ZERO,
                client.getStatus().name(),
                history.size(), onTime, overdues,
                traditionalScore, interpretScore(traditionalScore));
    }

    private String buildContextSummary(Client client, List<FinancialHistory> history, int score) {
        return String.format(
                "Cliente: %s | DNI: %s | Edad: %d | Ingresos: S/ %.2f | Ahorros: S/ %.2f | Score: %d",
                client.getFullName(), client.getDni(), client.getAge(),
                client.getMonthlyIncome(), client.getTotalSavings(), score);
    }


    private String callGemini(String prompt) {
        try {
            String baseUrl = geminiUrl.contains(":generateContent")
                    ? geminiUrl
                    : geminiUrl + "/" + geminiModel + ":generateContent";
            String url = baseUrl + "?key=" + geminiApiKey;
            Map<String, Object> body = Map.of(
                    "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                    "generationConfig", Map.of("temperature", 0.3, "maxOutputTokens", 1024)
            );
            String response = webClient.post()
                    .uri(url)
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            return root.at("/candidates/0/content/parts/0/text").asText();
        } catch (Exception e) {
            return fallbackResponse("Gemini: " + e.getMessage());
        }
    }

    private String callOpenAICompatible(String prompt) {
        try {
            String apiKey, url, model;
            switch (provider.toLowerCase()) {
                case "groq"       -> { apiKey = groqApiKey;       url = groqUrl;       model = groqModel;       }
                case "openrouter" -> { apiKey = openrouterApiKey; url = openrouterUrl; model = openrouterModel; }
                default           -> { apiKey = openaiApiKey;     url = openaiUrl;     model = openaiModel;     }
            }
            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", List.of(Map.of("role", "user", "content", prompt)),
                    "temperature", 0.3,
                    "max_tokens", 1024
            );
            String response = webClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            return root.at("/choices/0/message/content").asText();
        } catch (Exception e) {
            return fallbackResponse(provider + ": " + e.getMessage());
        }
    }

    private String callOllama(String prompt) {
        try {
            Map<String, Object> body = Map.of(
                    "model", ollamaModel,
                    "prompt", prompt,
                    "stream", false
            );
            String response = webClient.post()
                    .uri(ollamaUrl)
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            return root.get("response").asText();
        } catch (Exception e) {
            return fallbackResponse("Ollama: " + e.getMessage());
        }
    }


    private AIEvaluationResult parseAIResponse(String raw) {
        try {
            String clean = raw.replaceAll("```json", "").replaceAll("```", "").trim();
            JsonNode node = objectMapper.readTree(clean);

            return new AIEvaluationResult(
                    node.path("perfil").asText("INTERMEDIATE"),
                    node.path("nivelRiesgo").asText("MEDIO"),
                    node.path("montoSugerido").asDouble(0),
                    node.path("justificacion").asText(""),
                    node.path("recomendaciones").asText(""),
                    node.path("resumenEjecutivo").asText(""),
                    raw
            );
        } catch (Exception e) {
            return new AIEvaluationResult("INTERMEDIATE", "MEDIO", 0,
                    "No se pudo procesar la respuesta de IA.", "", "", raw);
        }
    }

    private String fallbackResponse(String errorMsg) {
        return "{\"error\": \"" + errorMsg + "\", \"perfil\": \"INTERMEDIATE\", " +
               "\"nivelRiesgo\": \"MEDIO\", \"montoSugerido\": 0, " +
               "\"justificacion\": \"Error al conectar con el servicio de IA.\", " +
               "\"recomendaciones\": \"\", \"resumenEjecutivo\": \"\"}";
    }

    private String interpretScore(int score) {
        if (score >= 800) return "EXCELENTE";
        if (score >= 700) return "MUY BUENO";
        if (score >= 600) return "BUENO";
        if (score >= 500) return "REGULAR";
        if (score >= 400) return "BAJO";
        return "MUY BAJO";
    }


    public record AIEvaluationResult(
            String profile,
            String riskLevel,
            double suggestedAmount,
            String justification,
            String recommendations,
            String executiveSummary,
            String rawResponse
    ) {}
}
