package com.newsreader.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.newsreader.service.AIService;
import com.newsreader.service.SystemConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.util.StringUtils;

import java.util.*;

@Service
public class AIServiceImpl implements AIService {

    private static final Logger log = LoggerFactory.getLogger(AIServiceImpl.class);

    @Value("${deepseek.base-url}")
    private String baseUrl;

    @Value("${deepseek.model}")
    private String model;

    @Value("${deepseek.max-tokens}")
    private Integer maxTokens;

    private final SystemConfigService systemConfigService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AIServiceImpl(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    private String chat(String systemPrompt, String userContent) {
        try {
            String resolvedApiKey = systemConfigService.getDeepseekApiKey();
            if (!StringUtils.hasText(resolvedApiKey) || resolvedApiKey.contains("your_deepseek_api_key")) {
                return "";
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(resolvedApiKey);

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
            log.error("DeepSeek API call failed", e);
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
        return lookupText(word, context, "word");
    }

    @Override
    public Map<String, String> lookupText(String text, String context, String type) {
        String prompt = String.format(
                "Text: \"%s\"\nContext: \"%s\"\nType: %s\n" +
                "Return JSON with keys definition, chinese, example. " +
                "If type is sentence, definition should explain the sentence meaning in English and example can be empty.",
                text, context, type == null ? "word" : type);
        String result = chat("You are an English learning assistant.", prompt);
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

    @Override
    public String chatAboutArticle(String articleContent, String question, List<Map<String, String>> history) {
        String snippet = articleContent == null ? "" : articleContent.substring(0, Math.min(3500, articleContent.length()));
        StringBuilder historyText = new StringBuilder();
        if (history != null) {
            for (Map<String, String> h : history) {
                String role = h.getOrDefault("role", "user");
                String content = h.getOrDefault("content", "");
                if (StringUtils.hasText(content)) {
                    historyText.append(role).append(": ").append(content).append("\n");
                }
            }
        }
        String prompt = "Article content:\n" + snippet + "\n\nConversation history:\n" + historyText +
                "\nUser question:\n" + question;
        String answer = chat("You are an article reading copilot. Answer only based on the article. If unclear, say what is missing.", prompt);
        if (!StringUtils.hasText(answer)) {
            return "当前 AI 服务不可用，请稍后重试。";
        }
        return answer;
    }
}
