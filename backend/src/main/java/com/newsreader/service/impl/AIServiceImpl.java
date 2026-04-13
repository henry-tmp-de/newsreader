package com.newsreader.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.newsreader.service.AIService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class AIServiceImpl implements AIService {

    private static final Logger log = LoggerFactory.getLogger(AIServiceImpl.class);

    @Value("${openai.api-key}")
    private String apiKey;

    @Value("${openai.base-url}")
    private String baseUrl;

    @Value("${openai.model}")
    private String model;

    @Value("${openai.max-tokens}")
    private Integer maxTokens;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String chat(String systemPrompt, String userContent) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("max_tokens", maxTokens);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userContent)
            ));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    baseUrl + "/chat/completions", entity, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            return root.path("choices").get(0).path("message").path("content").asText();
        } catch (Exception e) {
            log.error("OpenAI API call failed", e);
            return "";
        }
    }

    @Override
    public String generateSummary(String content) {
        String result = chat(
                "You are an English learning assistant. Generate a concise 2-3 sentence summary of the article.",
                content.length() > 2000 ? content.substring(0, 2000) : content
        );
        return result.isBlank() ? content.substring(0, Math.min(200, content.length())) : result;
    }

    @Override
    public String extractKeywords(String content) {
        String result = chat(
                "Extract 5-8 key vocabulary words from this article. Return only the words separated by commas, no explanation.",
                content.length() > 1500 ? content.substring(0, 1500) : content
        );
        return result.isBlank() ? "" : result;
    }

    @Override
    public String assessDifficulty(String content) {
        String result = chat(
                "Assess the English difficulty level of this article. Reply with only one word: EASY, MEDIUM, or HARD.",
                content.length() > 1000 ? content.substring(0, 1000) : content
        );
        if (result.toUpperCase().contains("EASY")) return "EASY";
        if (result.toUpperCase().contains("HARD")) return "HARD";
        return "MEDIUM";
    }

    @Override
    public Map<String, String> lookupWord(String word, String context) {
        String prompt = String.format(
                "The word \"%s\" appears in this context: \"%s\"\n" +
                "Provide: 1) definition in English 2) Chinese translation 3) example sentence. " +
                "Format as JSON: {\"definition\":\"...\",\"chinese\":\"...\",\"example\":\"...\"}", word, context);
        String result = chat("You are an English vocabulary assistant.", prompt);
        try {
            // clean markdown code blocks if present
            result = result.replaceAll("```json|```", "").trim();
            JsonNode node = objectMapper.readTree(result);
            Map<String, String> map = new HashMap<>();
            map.put("definition", node.path("definition").asText());
            map.put("chinese", node.path("chinese").asText());
            map.put("example", node.path("example").asText());
            return map;
        } catch (Exception e) {
            return Map.of("definition", result, "chinese", "", "example", "");
        }
    }

    @Override
    public List<Map<String, Object>> generateExercises(String content, String level, Integer count) {
        String prompt = String.format(
                "Article:\n%s\n\n" +
                "Generate %d English learning exercises for a %s level student. " +
                "Include a mix of vocabulary, comprehension, and grammar questions. " +
                "Return JSON array: [{\"type\":\"COMPREHENSION\",\"question\":\"...\",\"options\":[\"A. ...\",\"B. ...\",\"C. ...\",\"D. ...\"],\"correctAnswer\":\"A\",\"explanation\":\"...\"}]",
                content.length() > 2000 ? content.substring(0, 2000) : content, count, level);

        String result = chat("You are an English teacher creating quiz questions.", prompt);
        try {
            result = result.replaceAll("```json|```", "").trim();
            JsonNode array = objectMapper.readTree(result);
            List<Map<String, Object>> exercises = new ArrayList<>();
            for (JsonNode node : array) {
                Map<String, Object> ex = new HashMap<>();
                ex.put("type", node.path("type").asText("COMPREHENSION"));
                ex.put("question", node.path("question").asText());
                ex.put("options", node.path("options").toString());
                ex.put("correctAnswer", node.path("correctAnswer").asText());
                ex.put("explanation", node.path("explanation").asText());
                exercises.add(ex);
            }
            return exercises;
        } catch (Exception e) {
            log.error("Failed to parse exercise JSON", e);
            return Collections.emptyList();
        }
    }

    @Override
    public String evaluateAnswer(String question, String userAnswer, String correctAnswer, String context) {
        String prompt = String.format(
                "Question: %s\nUser's answer: %s\nCorrect answer: %s\nContext: %s\n" +
                "Provide a brief explanation (2-3 sentences) of why the correct answer is right, in English.",
                question, userAnswer, correctAnswer, context);
        return chat("You are a helpful English teacher.", prompt);
    }
}
